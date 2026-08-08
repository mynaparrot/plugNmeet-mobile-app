# PnmNativeAndroidDemo

A minimal native Android app demonstrating the plugNmeet hybrid integration architecture (native publisher + web subscriber model).

## Overview

This app follows the exact same pattern as `PnmReactNativeDemo` but implemented as a pure native Android application using Kotlin, Jetpack Compose, native Android WebView, and the [LiveKit Android SDK](https://github.com/livekit/client-sdk-android).

### Architecture

- **Join Screen**: Jetpack Compose UI for entering server URL, API key/secret, room name, and user name
- **Conference Screen**: Hosts the plugNmeet web client in a native Android WebView
- **Native Bridge**: JavaScript interface (`PnmNative`) injected into the WebView for bidirectional communication
- **LiveKit Service**: Native Android LiveKit SDK for publishing mic/webcam/screen share
- **API Service**: OkHttp-based client for plugNmeet REST API calls

### Flow

1. User fills in server credentials and room details on JoinScreen
2. App calls `/auth/room/create` (if room inactive) then `/auth/room/getJoinToken` with `client_type = HYBRID_WEB`
3. ConferenceScreen loads the web client via `WebView` at `<serverUrl>/?access_token=<jwt>`
4. Web client sends `initialize_native_publisher` → native app connects LiveKit with `native_token`
5. Bridge handles `publish_native_media`, `unpublish_native_media`, `mute_native_media`, etc.
6. Native app publishes mic/webcam/screen share tracks as `[userID]-native` participant

### Demo Only

> **WARNING**: This demo embeds the API key/secret directly in the app for simplicity. Production apps must never hold the API key. Use only `serverUrl` + `access_token` in production.

## Prerequisites

- Android Studio (latest stable)
- JDK 17
- Android SDK (API 35)

## Build & Run

1. Open the `PnmNativeAndroidDemo` folder in Android Studio
2. Sync Gradle
3. Connect a device or start an emulator
4. Run the app (Run > Run 'app')

Or from command line:

```bash
cd PnmNativeAndroidDemo
./gradlew assembleDebug
```

## Permissions

The app requests:
- `CAMERA` - for webcam publishing
- `RECORD_AUDIO` - for microphone publishing
- `INTERNET` - for network access
- `FOREGROUND_SERVICE` - for screen share (Android 14+)
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` - for screen share (Android 14+)
- `POST_NOTIFICATIONS` - for screen share notification (Android 13+)

## Screen Share

Screen share uses Android's `MediaProjection` API via the LiveKit Android SDK:

1. Web client sends `publish_native_media` with source `SCREENSHARE`
2. `NativeBridge.onScreenShareIntentRequest` fires → `ActivityResultLauncher` shows the system screen capture permission dialog
3. User grants permission → the resulting `Intent` is passed to `LiveKitService.enableScreenShare(data: Intent)`
4. `setScreenShareEnabled(true, ScreenCaptureParams(data))` publishes the screen share track
5. On Android 14+, `track.startForegroundService(null, null)` is called before `startCapture()`
6. Web client sends `unpublish_native_media` → `disableScreenShare()` stops capture and disposes the track

### Key Implementation Details

- `LiveKitService.kt` — `enableScreenShare`/`disableScreenShare` with duplicate publish guard
- `NativeBridge.kt` — `onScreenShareIntentRequest` callback for system permission flow
- `ConferenceScreen.kt` — `ActivityResultLauncher` for `MediaProjectionManager.createScreenCaptureIntent()`
- Source mapping: `"screenshare"` in callbacks maps to `Track.Source.SCREEN_SHARE` for all track events

## Bridge Contract

This app implements the full bridge contract from `HYBRID_INTEGRATION_ARCHITECTURE.md` section 4:

| Web → Native | Description |
|---|---|
| `initialize_native_publisher` | Connect to LiveKit with native token |
| `publish_native_media` | Publish mic/webcam/screen share |
| `unpublish_native_media` | Unpublish mic/webcam/screen share |
| `mute_native_media` | Mute mic/webcam/screen share |
| `unmute_native_media` | Unmute mic/webcam/screen share |
| `native_heartbeat_ping` | Keepalive ping |
| `teardown_native_publisher` | Disconnect and clean up |

| Native → Web | Description |
|---|---|
| `native_media_status` | Connection status or error |
| `native_track_published` | Track published confirmation |
| `native_track_unpublished` | Track unpublished notification |
| `native_media_muted` | Mute state confirmation |
| `native_heartbeat_pong` | Heartbeat response |
