package com.plugnmeet.nativedemo

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.plugnmeet.nativedemo.ui.ConferenceScreen
import com.plugnmeet.nativedemo.ui.JoinScreen
import io.livekit.android.LiveKit
import io.livekit.android.util.LoggingLevel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveKit.init(this)
        LiveKit.loggingLevel = LoggingLevel.VERBOSE

        // Transparent bars + dark icons — content draws white under bars (site bg-white chrome)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var config by remember { mutableStateOf<HybridConfig?>(null) }

                    if (config != null) {
                        ConferenceScreen(
                            config = config!!,
                            onSessionEnded = { config = null }
                        )
                    } else {
                        JoinScreen(onJoin = { newConfig ->
                            config = newConfig
                        })
                    }
                }
            }
        }
    }
}
