package com.plugnmeet.nativedemo.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.plugnmeet.nativedemo.services.LiveKitService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Bridge between WebView (web client) and native host.
 * Uses proto3 JSON format as defined in HYBRID_INTEGRATION_ARCHITECTURE.md:
 *
 * Incoming (web → native):
 *   { "action": "INITIALIZE_NATIVE_PUBLISHER", "initializeNativePublisher": { ... } }
 *   { "action": "PUBLISH_NATIVE_MEDIA", "mediaSource": { "source": "MIC"|"WEBCAM" } }
 *
 * Outgoing (native → web):
 *   { "action": "NATIVE_MEDIA_STATUS", "mediaStatus": { ... } }
 *   { "action": "NATIVE_TRACK_PUBLISHED", "trackState": { ... } }
 */
class NativeBridge(
    private val webView: WebView,
    private val liveKitService: LiveKitService
) {
    companion object {
        private const val TAG = "NativeBridge"
    }

    /** Called when the web requests screen share — the host should launch MediaProjectionManager intent */
    var onScreenShareIntentRequest: (() -> Unit)? = null

    /** Called when the session ends (web sends TEARDOWN_NATIVE_PUBLISHER) */
    var onSessionEnded: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var nativeUserId: String = ""
    private var lastPingTimestamp: Long = 0

    fun getNativeUserId(): String = nativeUserId

    /**
     * Send a structured message to the web application using proto3 JSON format.
     */
    fun sendToWeb(action: String, payloadField: String? = null, payload: JSONObject? = null) {
        val msg = JSONObject().apply {
            put("action", action)
            if (payloadField != null && payload != null) {
                put(payloadField, payload)
            }
        }
        val js = "window.dispatchEvent(new MessageEvent('message', { data: ${JSONObject.quote(msg.toString())} }))"
        android.util.Log.d(TAG, "Sending to web: $msg")
        mainHandler.post { webView.evaluateJavascript(js, null) }
    }

    /**
     * Handle incoming messages from the WebView JavaScript interface.
     */
    @JavascriptInterface
    fun postMessage(rawData: String) {
        try {
            handleBridgeMessage(rawData)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error handling bridge message", e)
        }
    }

    private fun handleBridgeMessage(rawData: String) {
        android.util.Log.d(TAG, "Received: $rawData")
        val msg = JSONObject(rawData)
        val action = msg.optString("action", "")
        if (action.isEmpty()) return

        when (action) {
            "INITIALIZE_NATIVE_PUBLISHER" -> {
                val p = msg.optJSONObject("initializeNativePublisher")
                handleInitialize(p)
            }
            "PUBLISH_NATIVE_MEDIA" -> {
                val p = msg.optJSONObject("mediaSource")
                handlePublish(p)
            }
            "UNPUBLISH_NATIVE_MEDIA" -> {
                val p = msg.optJSONObject("mediaSource")
                handleUnpublish(p)
            }
            "MUTE_NATIVE_MEDIA" -> {
                val p = msg.optJSONObject("mediaSource")
                handleMute(p, muted = true)
            }
            "UNMUTE_NATIVE_MEDIA" -> {
                val p = msg.optJSONObject("mediaSource")
                handleMute(p, muted = false)
            }
            "NATIVE_HEARTBEAT_PING" -> {
                val p = msg.optJSONObject("heartbeat")
                handleHeartbeat(p)
            }
            "TEARDOWN_NATIVE_PUBLISHER" -> {
                handleTeardown()
            }
        }
    }

    private fun handleInitialize(payload: JSONObject?) {
        val livekitUrl = payload?.optString("livekitUrl", "") ?: ""
        val token = payload?.optString("token", "") ?: ""
        nativeUserId = payload?.optString("nativeUserId", "") ?: ""

        // Optional E2EE config: { enabled: Boolean, key: String }
        val e2ee = payload?.optJSONObject("e2ee")
        val e2eeEnabled = e2ee?.optBoolean("enabled", false) ?: false
        val e2eeKey = e2ee?.optString("key", "") ?: ""

        if (livekitUrl.isEmpty() || token.isEmpty()) {
            sendError("Missing livekitUrl or token", "INITIALIZE_NATIVE_PUBLISHER")
            return
        }

        scope.launch {
            try {
                liveKitService.connect(
                    webView.context,
                    livekitUrl,
                    token,
                    if (e2eeEnabled && e2eeKey.isNotEmpty()) e2eeKey else null
                )
            } catch (e: Exception) {
                sendError(e.message ?: "Failed to connect LiveKit", "LiveKit connection")
            }
        }
    }

    private fun handlePublish(payload: JSONObject?) {
        val source = payload?.optString("source", "") ?: ""
        scope.launch {
            try {
                when (source) {
                    "MIC" -> {
                        liveKitService.enableMic()
                        sendTrackPublished("AUDIO", "MIC")
                    }
                    "WEBCAM" -> {
                        liveKitService.enableWebcam()
                        sendTrackPublished("VIDEO", "WEBCAM")
                    }
                    "SCREENSHARE" -> {
                        val room = liveKitService.getRoom()
                        if (room?.localParticipant?.getTrackPublication(io.livekit.android.room.track.Track.Source.SCREEN_SHARE) != null) {
                            android.util.Log.d(TAG, "Screen share already published, ignoring")
                            return@launch
                        }
                        val r = onScreenShareIntentRequest
                        if (r != null) {
                            mainHandler.post { r() }
                        } else {
                            sendError("No screen share launcher available", "Publishing SCREENSHARE")
                        }
                    }
                }
            } catch (e: Exception) {
                sendError(e.message ?: "Failed to publish", "Publishing $source")
            }
        }
    }

    private fun handleUnpublish(payload: JSONObject?) {
        val source = payload?.optString("source", "") ?: ""
        scope.launch {
            try {
                when (source) {
                    "MIC" -> {
                        liveKitService.disableMic()
                        sendTrackUnpublished("AUDIO", "MIC")
                    }
                    "WEBCAM" -> {
                        liveKitService.disableWebcam()
                        sendTrackUnpublished("VIDEO", "WEBCAM")
                    }
                    "SCREENSHARE" -> {
                        liveKitService.disableScreenShare()
                        sendTrackUnpublished("VIDEO", "SCREENSHARE")
                    }
                }
            } catch (e: Exception) {
                sendError(e.message ?: "Failed to unpublish", "Unpublishing $source")
            }
        }
    }

    fun sendTrackPublished(kind: String, source: String) {
        sendToWeb("NATIVE_TRACK_PUBLISHED", "trackState", JSONObject().apply {
            put("userId", nativeUserId)
            put("kind", kind)
            put("source", source)
        })
    }

    fun sendTrackUnpublished(kind: String, source: String) {
        sendToWeb("NATIVE_TRACK_UNPUBLISHED", "trackState", JSONObject().apply {
            put("userId", nativeUserId)
            put("kind", kind)
            put("source", source)
        })
    }

    private fun handleMute(payload: JSONObject?, muted: Boolean) {
        val source = payload?.optString("source", "") ?: ""
        scope.launch {
            try {
                when (source) {
                    "MIC" -> {
                        if (muted) liveKitService.muteMic() else liveKitService.unmuteMic()
                    }
                    "WEBCAM" -> {
                        if (muted) liveKitService.muteWebcam() else liveKitService.unmuteWebcam()
                    }
                }
            } catch (e: Exception) {
                sendError(e.message ?: "Failed to ${if (muted) "mute" else "unmute"}", "${if (muted) "Muting" else "Unmuting"} $source")
            }
        }
    }

    private fun handleHeartbeat(payload: JSONObject?) {
        lastPingTimestamp = System.currentTimeMillis()
        sendToWeb("NATIVE_HEARTBEAT_PONG", "heartbeat", JSONObject().apply {
            put("ts", System.currentTimeMillis().toString())
        })
    }

    private fun handleTeardown() {
        scope.launch {
            liveKitService.release()
            onSessionEnded?.invoke()
        }
    }

    private fun sendError(msg: String, context: String) {
        sendToWeb("NATIVE_MEDIA_STATUS", "mediaStatus", JSONObject().apply {
            put("error", msg)
            put("context", context)
        })
    }
}
