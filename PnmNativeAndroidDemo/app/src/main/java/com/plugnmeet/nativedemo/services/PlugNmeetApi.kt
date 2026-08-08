package com.plugnmeet.nativedemo.services

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


class PlugNmeetApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ── Response data classes ──

    data class ApiResult(val status: Boolean, val msg: String = "")

    data class IsRoomActiveResult(val status: Boolean, val msg: String = "", val isActive: Boolean = false)

    data class CreateRoomResult(val status: Boolean, val msg: String = "")

    data class GetJoinTokenResult(val status: Boolean, val msg: String = "", val token: String? = null)

    // ── HMAC signing ──

    private fun hmacSha256(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(message.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun authHeaders(apiKey: String, apiSecret: String, body: String): Map<String, String> {
        val signature = hmacSha256(body, apiSecret)
        return mapOf(
            "Content-Type" to jsonType.toString(),
            "API-KEY" to apiKey,
            "HASH-SIGNATURE" to signature
        )
    }

    // ── Generic auth request ──

    private fun authRequest(serverUrl: String, apiKey: String, apiSecret: String, method: String, body: String): String {
        val url = "${serverUrl.trimEnd('/')}/auth/$method"
        val headers = authHeaders(apiKey, apiSecret, body)
        val requestBuilder = Request.Builder().url(url).post(body.toRequestBody(jsonType))
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw Exception("API error ${response.code}: ${response.body?.string() ?: ""}")
        }
        return response.body?.string() ?: "{}"
    }

    // ── Public API methods ──

    /**
     * Check whether a room is active.
     * POST /auth/room/isRoomActive
     */
    fun isRoomActive(serverUrl: String, apiKey: String, apiSecret: String, roomId: String): IsRoomActiveResult {
        val body = JSONObject().put("room_id", roomId).toString()
        val resp = authRequest(serverUrl, apiKey, apiSecret, "room/isRoomActive", body)
        val json = JSONObject(resp)
        return IsRoomActiveResult(
            status = json.optBoolean("status", false),
            msg = json.optString("msg", ""),
            isActive = json.optBoolean("is_active", false)
        )
    }

    /**
     * Create a room with default features.
     * POST /auth/room/create
     */
    fun createRoom(serverUrl: String, apiKey: String, apiSecret: String, roomId: String, roomTitle: String): CreateRoomResult {
        val roomFeatures = JSONObject().apply {
            put("allow_webcams", true)
            put("mute_on_start", false)
            put("allow_screen_share", true)
            put("admin_only_webcams", false)
            put("allow_view_other_webcams", true)
            put("allow_view_other_users_list", true)
            put("room_duration", 0)
            put("enable_analytics", true)
            put("allow_virtual_bg", true)
            put("allow_raise_hand", true)
            put("allow_reactions", true)
            put("recording_features", JSONObject().apply {
                put("is_allow", true)
                put("is_allow_cloud", true)
                put("is_allow_local", true)
                put("enable_auto_cloud_recording", false)
                put("only_record_admin_webcams", false)
            })
            put("external_broadcasting_features", JSONObject().apply {
                put("is_allow", true)
                put("is_allow_rtmp", true)
            })
            put("chat_features", JSONObject().apply {
                put("is_allow", true)
                put("is_allow_file_upload", true)
                put("max_file_size", 50)
                put("allowed_file_types", org.json.JSONArray(listOf("jpg", "png", "zip", "pdf")))
            })
            put("shared_note_pad_features", JSONObject().apply { put("is_allow", true) })
            put("whiteboard_features", JSONObject().apply { put("is_allow", true) })
            put("external_media_player_features", JSONObject().apply { put("is_allow", true) })
            put("waiting_room_features", JSONObject().apply { put("is_active", true) })
            put("breakout_room_features", JSONObject().apply {
                put("is_allow", true)
                put("allowed_number_rooms", 6)
            })
            put("display_external_link_features", JSONObject().apply { put("is_allow", true) })
            put("ingress_features", JSONObject().apply { put("is_allow", true) })
            put("polls_features", JSONObject().apply { put("is_allow", true) })
            put("sip_dial_in_features", JSONObject().apply { put("is_allow", true) })
            put("end_to_end_encryption_features", JSONObject().apply {
                put("is_enabled", false)
                put("included_chat_messages", false)
                put("included_whiteboard", false)
                put("enabled_self_insert_encryption_key", false)
            })
        }

        val metadata = JSONObject().apply {
            put("room_title", roomTitle)
            put("welcome_message", "Welcome to plugNmeet! To share microphone click mic icon from bottom left side.")
            put("room_features", roomFeatures)
        }

        val body = JSONObject().apply {
            put("room_id", roomId)
            put("empty_timeout", 7200)
            put("metadata", metadata)
        }.toString()

        val resp = authRequest(serverUrl, apiKey, apiSecret, "room/create", body)
        val json = JSONObject(resp)
        return CreateRoomResult(
            status = json.optBoolean("status", false),
            msg = json.optString("msg", "")
        )
    }

    /**
     * Get a join token with client_type = HYBRID_WEB (1).
     * POST /auth/room/getJoinToken
     */
    fun getJoinToken(
        serverUrl: String,
        apiKey: String,
        apiSecret: String,
        roomId: String,
        userName: String,
        userId: String,
        isAdmin: Boolean = false
    ): GetJoinTokenResult {
        val userInfo = JSONObject().apply {
            put("name", userName)
            put("user_id", userId)
            put("is_admin", isAdmin)
            put("client_type", 1) // HYBRID_WEB
        }
        val body = JSONObject().apply {
            put("room_id", roomId)
            put("user_info", userInfo)
        }.toString()

        val resp = authRequest(serverUrl, apiKey, apiSecret, "room/getJoinToken", body)
        val json = JSONObject(resp)
        return GetJoinTokenResult(
            status = json.optBoolean("status", false),
            msg = json.optString("msg", ""),
            token = json.optString("token", null)
        )
    }
}
