package com.plugnmeet.nativedemo.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.plugnmeet.nativedemo.HybridConfig
import com.plugnmeet.nativedemo.bridge.NativeBridge
import com.plugnmeet.nativedemo.services.KeepAliveService
import com.plugnmeet.nativedemo.services.LiveKitService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/** Map LiveKit source string to bridge (kind, source) pair. */
private fun mapSource(source: String): Pair<String, String> = when (source) {
    "mic" -> "AUDIO" to "MIC"
    "webcam" -> "VIDEO" to "WEBCAM"
    "screenshare" -> "VIDEO" to "SCREENSHARE"
    else -> "VIDEO" to source.uppercase()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConferenceScreen(config: HybridConfig, onSessionEnded: () -> Unit = {}) {
    val activity = LocalContext.current as? Activity
    var bridgeStatus by remember { mutableStateOf("waiting") }
    val liveKitService = remember { LiveKitService() }
    val nativeBridge = remember { mutableStateOf<NativeBridge?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val screenShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            scope.launch {
                liveKitService.enableScreenShare(result.data!!)
                // Confirm immediately; callback also fires as safety net.
                nativeBridge.value?.sendTrackPublished("VIDEO", "SCREENSHARE")
            }
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val dataString = result.data?.dataString
            val uri = if (dataString != null) arrayOf(Uri.parse(dataString)) else null
            filePathCallback?.onReceiveValue(uri)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // Request permissions on mount
    DisposableEffect(Unit) {
        val perms = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(activity!!, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity!!, needed.toTypedArray(), 1001)
        }

        KeepAliveService.start(activity!!)

        onDispose {
            KeepAliveService.stop(activity!!)
            liveKitService.destroy()
        }
    }

    // Pan WebView up when keyboard opens without resizing it (resize steals focus / flashes IME).
    // Extra lift above nav bar is the portion of IME taller than the nav bar inset.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val imePanPx = max(0, imeBottom - navBottom)

    LaunchedEffect(imePanPx) {
        if (imePanPx > 0) {
            delay(50)
            webViewRef.value?.evaluateJavascript(
                """
                (function() {
                  var el = document.activeElement;
                  if (!el) return;
                  var tag = (el.tagName || '').toLowerCase();
                  if (tag === 'input' || tag === 'textarea' || el.isContentEditable) {
                    el.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
                  }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status bar — background before statusBarsPadding fills white under the status bar.
        // Bottom border (Gray-200) visually separates the native indicator from the WebView.
        val indicatorBorder = remember { Color(0xFFE5E7EB) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .border(BorderStroke(1.dp, indicatorBorder))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotColor = when (bridgeStatus) {
                "connected" -> Color(0xFF4CAF50)
                "error" -> Color(0xFFF44336)
                else -> Color(0xFFFFC107)
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
                    .padding(end = 8.dp)
            )
            Text(
                text = "Native publisher: ${
                    when (bridgeStatus) {
                        "connected" -> "Connected"
                        "error" -> "Error"
                        else -> "Waiting for handshake..."
                    }
                }",
                fontSize = 12.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // WebView — white under nav bar (site chrome); navigationBarsPadding inside so content stays clear.
        // IME uses offset pan (not padding).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .navigationBarsPadding()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                    WebView.setWebContentsDebuggingEnabled(true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            callback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (filePathCallback != null) {
                                filePathCallback?.onReceiveValue(null)
                            }
                            filePathCallback = callback

                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            try {
                                filePickerLauncher.launch(intent)
                            } catch (e: Exception) {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                            }
                            return true
                        }

                        override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                            android.util.Log.d("WebView", "${msg.messageLevel()}: ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                            return true
                        }
                    }

                    // Initialize LiveKit service callbacks
                    liveKitService.setCallbacks(object : LiveKitService.Callbacks {
                        override fun onConnected() {
                            bridgeStatus = "connected"
                            nativeBridge.value?.sendToWeb("NATIVE_MEDIA_STATUS", "mediaStatus", org.json.JSONObject().apply {
                                put("status", "connected")
                            })
                        }

                        override fun onDisconnected(reason: String?) {
                            bridgeStatus = "waiting"
                        }

                        override fun onError(error: Exception) {
                            bridgeStatus = "error"
                            nativeBridge.value?.sendToWeb("NATIVE_MEDIA_STATUS", "mediaStatus", org.json.JSONObject().apply {
                                put("error", error.message ?: "Unknown error")
                                put("context", "LiveKit service error")
                            })
                        }

                        override fun onTrackPublished(source: String) {
                            val (kind, src) = mapSource(source)
                            nativeBridge.value?.sendToWeb(
                                "NATIVE_TRACK_PUBLISHED",
                                "trackState",
                                org.json.JSONObject().apply {
                                    put("userId", nativeBridge.value?.getNativeUserId() ?: "")
                                    put("kind", kind)
                                    put("source", src)
                                }
                            )
                        }

                        override fun onTrackUnpublished(source: String) {
                            val (kind, src) = mapSource(source)
                            nativeBridge.value?.sendToWeb(
                                "NATIVE_TRACK_UNPUBLISHED",
                                "trackState",
                                org.json.JSONObject().apply {
                                    put("userId", nativeBridge.value?.getNativeUserId() ?: "")
                                    put("kind", kind)
                                    put("source", src)
                                }
                            )
                        }

                        override fun onMuted(source: String, muted: Boolean) {
                            val (_, src) = mapSource(source)
                            nativeBridge.value?.sendToWeb(
                                "NATIVE_MEDIA_MUTED",
                                "mediaMuted",
                                org.json.JSONObject().apply {
                                    put("source", src)
                                    put("muted", muted)
                                }
                            )
                        }
                    })

                    // Create and inject the JavaScript bridge
                    val bridge = NativeBridge(this, liveKitService)
                    bridge.onScreenShareIntentRequest = {
                        val mpm = ctx.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenShareLauncher.launch(mpm.createScreenCaptureIntent())
                    }
                    bridge.onSessionEnded = onSessionEnded
                    nativeBridge.value = bridge
                    webViewRef.value = this

                    addJavascriptInterface(bridge, "PnmNative")

                    // Load the plugNmeet web client
                    val url = "${config.serverUrl.trimEnd('/')}/?access_token=${config.jwt}"
                    loadUrl(url)
                }
            },
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset(0, -imePanPx) }
        )
        }
    }
}
