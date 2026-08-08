package com.plugnmeet.nativedemo.services

import android.content.Context
import android.content.Intent
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.AudioOptions
import io.livekit.android.RoomOptions
import io.livekit.android.e2ee.E2EEOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Manages a native LiveKit publisher connection.
 * Connects with the native twin token, publishes mic/webcam on demand.
 * Follows the LiveKit Android SDK patterns from the sample app.
 */
class LiveKitService {

    companion object {
        private const val TAG = "LiveKitService"
    }

    interface Callbacks {
        fun onConnected()
        fun onDisconnected(reason: String? = null)
        fun onError(error: Exception)
        fun onTrackPublished(source: String) // "mic" or "webcam"
        fun onTrackUnpublished(source: String)
        fun onMuted(source: String, muted: Boolean)
    }

    private var room: Room? = null
    private var callbacks: Callbacks? = null

    fun getRoom(): Room? = room
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setCallbacks(cbs: Callbacks) {
        callbacks = cbs
    }

    /**
     * Connect to LiveKit with the native twin token.
     * If already connected, tears down the previous connection first.
     *
     * @param e2eeKey optional shared E2EE key. When non-null, end-to-end encryption
     * is enabled for the published tracks using the same shared key as the web client.
     */
    suspend fun connect(context: Context, url: String, token: String, e2eeKey: String? = null) {
        if (room != null) {
            disconnect()
        }

        try {
            // Shared-key E2EE (matches the web client's ExternalE2EEKeyProvider shared key).
            // The Room creates/owns the E2EEManager when e2eeOptions is set.
            val e2eeOptions = if (!e2eeKey.isNullOrEmpty()) {
                Log.d(TAG, "E2EE enabled for native publisher")
                E2EEOptions(sharedKey = e2eeKey)
            } else {
                null
            }

            // H.264 for hardware encoder support on Android (lower CPU/battery vs VP8).
            // Let the SDK auto-select bitrate/FPS based on source and network conditions.
            val lkRoom = LiveKit.create(
                appContext = context,
                options = RoomOptions(
                    e2eeOptions = e2eeOptions,
                    videoTrackPublishDefaults = VideoTrackPublishDefaults(
                        videoCodec = "h264"
                    )
                ),
                overrides = LiveKitOverrides(
                    audioOptions = AudioOptions()
                )
            )

            room = lkRoom
            lkRoom.connect(url = url, token = token)

            // Observe room events for connection state and track changes
            scope.launch {
                lkRoom.events.collect { event ->
                    when (event) {
                        is RoomEvent.Connected -> {
                            Log.d(TAG, "Connected to LiveKit")
                            callbacks?.onConnected()
                        }
                        is RoomEvent.Disconnected -> {
                            Log.d(TAG, "Disconnected: ${event.reason}")
                            callbacks?.onDisconnected(event.reason.toString())
                        }
                        is RoomEvent.Reconnecting -> {
                            Log.d(TAG, "Reconnecting...")
                        }
                        is RoomEvent.FailedToConnect -> {
                            Log.e(TAG, "Failed to connect", event.error)
                            callbacks?.onError(Exception(event.error.message))
                        }
                        is RoomEvent.TrackPublished -> {
                            if (event.participant is io.livekit.android.room.participant.LocalParticipant) {
                                when (event.publication.source) {
                                    Track.Source.MICROPHONE -> callbacks?.onTrackPublished("mic")
                                    Track.Source.CAMERA -> callbacks?.onTrackPublished("webcam")
                                    Track.Source.SCREEN_SHARE -> callbacks?.onTrackPublished("screenshare")
                                    else -> Unit
                                }
                            }
                        }
                        is RoomEvent.TrackUnpublished -> {
                            if (event.participant is io.livekit.android.room.participant.LocalParticipant) {
                                when (event.publication.source) {
                                    Track.Source.MICROPHONE -> callbacks?.onTrackUnpublished("mic")
                                    Track.Source.CAMERA -> callbacks?.onTrackUnpublished("webcam")
                                    Track.Source.SCREEN_SHARE -> callbacks?.onTrackUnpublished("screenshare")
                                    else -> Unit
                                }
                            }
                        }
                        is RoomEvent.TrackMuted -> {
                            if (event.participant is io.livekit.android.room.participant.LocalParticipant) {
                                when (event.publication.source) {
                                    Track.Source.MICROPHONE -> callbacks?.onMuted("mic", true)
                                    Track.Source.CAMERA -> callbacks?.onMuted("webcam", true)
                                    else -> Unit
                                }
                            }
                        }
                        is RoomEvent.TrackUnmuted -> {
                            if (event.participant is io.livekit.android.room.participant.LocalParticipant) {
                                when (event.publication.source) {
                                    Track.Source.MICROPHONE -> callbacks?.onMuted("mic", false)
                                    Track.Source.CAMERA -> callbacks?.onMuted("webcam", false)
                                    else -> Unit
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect", e)
            callbacks?.onError(e)
            throw e
        }
    }

    /** Publish microphone */
    suspend fun enableMic() {
        val room = room ?: return
        try {
            room.localParticipant.setMicrophoneEnabled(true)
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Unpublish microphone */
    suspend fun disableMic() {
        val room = room ?: return
        try {
            val pub = room.localParticipant.getTrackPublication(Track.Source.MICROPHONE)
            pub?.track?.let { room.localParticipant.unpublishTrack(it) }
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Publish webcam */
    suspend fun enableWebcam() {
        val room = room ?: return
        try {
            room.localParticipant.setCameraEnabled(true)
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Unpublish webcam */
    suspend fun disableWebcam() {
        val room = room ?: return
        try {
            val pub = room.localParticipant.getTrackPublication(Track.Source.CAMERA)
            pub?.track?.let { room.localParticipant.unpublishTrack(it) }
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Publish screen share */
    suspend fun enableScreenShare(data: Intent) {
        val room = room ?: return
        if (room.localParticipant.getTrackPublication(Track.Source.SCREEN_SHARE) != null) {
            Log.d(TAG, "Screen share already published")
            return
        }
        try {
            room.localParticipant.setScreenShareEnabled(true, ScreenCaptureParams(data))
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Unpublish screen share */
    suspend fun disableScreenShare() {
        val room = room ?: return
        if (room.localParticipant.getTrackPublication(Track.Source.SCREEN_SHARE) == null) {
            Log.d(TAG, "Screen share not published")
            return
        }
        try {
            room.localParticipant.setScreenShareEnabled(false)
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Mute microphone (keep track published, no audio) */
    suspend fun muteMic() {
        val room = room ?: return
        try {
            room.localParticipant.setMicrophoneEnabled(false)
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Unmute microphone */
    suspend fun unmuteMic() {
        val room = room ?: return
        try {
            room.localParticipant.setMicrophoneEnabled(true)
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Mute webcam (keep track published, no video) */
    suspend fun muteWebcam() {
        val room = room ?: return
        try {
            room.localParticipant.setCameraEnabled(false)
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Unmute webcam */
    suspend fun unmuteWebcam() {
        val room = room ?: return
        try {
            room.localParticipant.setCameraEnabled(true)
        } catch (e: Exception) {
            callbacks?.onError(e)
            throw e
        }
    }

    /** Disconnect from LiveKit and release all media */
    suspend fun disconnect() {
        room?.disconnect()
        room = null
    }

    /** Fully release the room and resources */
    suspend fun release() {
        room?.release()
        room = null
    }

    fun destroy() {
        scope.cancel()
        room?.release()
        room = null
    }
}