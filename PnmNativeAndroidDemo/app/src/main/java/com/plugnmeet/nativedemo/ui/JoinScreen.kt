package com.plugnmeet.nativedemo.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plugnmeet.nativedemo.HybridConfig
import com.plugnmeet.nativedemo.services.PlugNmeetApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ROOM_OPTIONS = listOf(
    "room01", "room02", "room03", "room04", "room05",
    "room06", "room07", "room08", "room09", "room10",
    "room11", "room12", "room13", "room14", "room15"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JoinScreen(onJoin: (HybridConfig) -> Unit) {
    val scope = rememberCoroutineScope()
    val api = remember { PlugNmeetApi() }

    var serverUrl by remember { mutableStateOf("https://demo.plugnmeet.com") }
    var apiKey by remember { mutableStateOf("plugnmeet") }
    var apiSecret by remember { mutableStateOf("zumyyYWqv7KR2kUqvYdq4z4sXg7XTBD2ljT6") }
    var roomId by remember { mutableStateOf(ROOM_OPTIONS[0]) }
    var customRoomId by remember { mutableStateOf("") }
    var useCustomRoom by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("user-" + (10..99).random()) }
    var isAdmin by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val effectiveRoomId = if (useCustomRoom) customRoomId else roomId
    val isFormValid = serverUrl.isNotBlank() && apiKey.isNotBlank() &&
            apiSecret.isNotBlank() && effectiveRoomId.isNotBlank() && userName.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Demo warning banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFF3CD))
                .border(1.dp, Color(0xFFFFCA2C), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "DEMO ONLY — Never embed API keys in production apps.",
                color = Color(0xFF664D03),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "PlugNMeet Demo",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = "Hybrid Native Publisher",
            fontSize = 16.sp,
            color = Color(0xFF666666),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // Server URL
        Label("Server URL")
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://your-plugnmeet-server.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        // API Key
        Label("API Key")
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("API Key") },
            singleLine = true
        )

        // API Secret
        Label("API Secret")
        OutlinedTextField(
            value = apiSecret,
            onValueChange = { apiSecret = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("API Secret") },
            singleLine = true
        )

        // Room selection
        Label("Room")
        if (!useCustomRoom) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ROOM_OPTIONS.forEach { id ->
                    val isActive = roomId == id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isActive) Color(0xFF007AFF) else Color(0xFFE8E8E8))
                            .border(1.dp, if (isActive) Color(0xFF007AFF) else Color(0xFFDDDDDD), RoundedCornerShape(16.dp))
                            .clickable { roomId = id; useCustomRoom = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = id,
                            fontSize = 13.sp,
                            color = if (isActive) Color.White else Color(0xFF333333)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "or Custom",
                color = Color(0xFF007AFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    useCustomRoom = true
                    customRoomId = "room-" + (1000..9999).random()
                }
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customRoomId,
                    onValueChange = { customRoomId = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Text(
                    text = "Random",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF666666))
                        .clickable { customRoomId = "room-" + (1000..9999).random() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    text = "List",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF666666))
                        .clickable { useCustomRoom = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // User Name
        Label("Name")
        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Your name") },
            singleLine = true
        )

        // User Type
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "User Type: ${if (isAdmin) "Admin" else "Participant"}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF333333)
            )
            Switch(
                checked = isAdmin,
                onCheckedChange = { isAdmin = it },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF007AFF),
                    uncheckedTrackColor = Color(0xFFE8E8E8)
                )
            )
        }

        // Error message
        if (errorMsg != null) {
            Text(
                text = errorMsg ?: "",
                color = Color(0xFFF44336),
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Join button
        Button(
            onClick = {
                loading = true
                errorMsg = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            // 1. Check if room is active
                            val activeRes = api.isRoomActive(serverUrl, apiKey, apiSecret, effectiveRoomId)
                            if (!activeRes.status) {
                                return@withContext Result.failure(Exception(activeRes.msg.ifEmpty { "Failed to check room status" }))
                            }
                            if (!activeRes.isActive) {
                                // 2. Create room if not active
                                val createRes = api.createRoom(serverUrl, apiKey, apiSecret, effectiveRoomId, "Demo room")
                                if (!createRes.status) {
                                    return@withContext Result.failure(Exception(createRes.msg.ifEmpty { "Failed to create room" }))
                                }
                            }
                            // 3. Get join token with HYBRID_WEB client type
                            val tokenRes = api.getJoinToken(
                                serverUrl, apiKey, apiSecret,
                                effectiveRoomId, userName,
                                userId = System.currentTimeMillis().toString(),
                                isAdmin = isAdmin
                            )
                            if (!tokenRes.status || tokenRes.token.isNullOrEmpty()) {
                                return@withContext Result.failure(Exception(tokenRes.msg.ifEmpty { "Failed to get join token" }))
                            }
                            return@withContext Result.success(tokenRes.token!!)
                        }
                        result.onSuccess { token ->
                            onJoin(HybridConfig(serverUrl.trim(), token))
                        }
                        result.onFailure { e ->
                            errorMsg = e.message
                        }
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "Something went wrong"
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(52.dp),
            enabled = isFormValid && !loading,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF007AFF),
                disabledContainerColor = Color(0xFFA0CFFF)
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(24.dp)
                )
            } else {
                Text("Join Meeting", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "PlugNmeet",
            color = Color(0xFF999999),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF333333),
        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp)
    )
}
