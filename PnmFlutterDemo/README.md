# PnmFlutterDemo

A Flutter demo app demonstrating plugNmeet's hybrid integration pattern: native publisher + web subscriber.

## Overview

Hosts the plugNmeet web client in a WebView while using [LiveKit Flutter Client SDK](https://github.com/livekit/client-sdk-flutter) to publish mic, webcam, and screen share natively. Mobile browsers don't support screen sharing, so native publishing is required.

### Architecture

- **Join Screen**: Server URL, API key/secret, room name, user name
- **Conference Screen**: WebView hosting plugNmeet web client + native JSON bridge
- **LiveKit Service**: `livekit_client` managing native mic/webcam/screen share publishing
- **API Service**: HTTP client for plugNmeet REST API calls

### Flow

1. User fills in credentials on JoinScreen
2. App calls `/auth/room/create` then `/auth/room/getJoinToken` with `client_type = HYBRID_WEB`
3. ConferenceScreen loads the web client at `<serverUrl>/?access_token=<jwt>`
4. Web client sends `initialize_native_publisher` → native app connects LiveKit with `native_token`
5. Bridge handles `publish_native_media`, `unpublish_native_media`, etc.
6. Native app publishes mic/webcam/screen share as `[userID]-native` participant

## Prerequisites

- Flutter SDK 3.0+
- Android Studio / Xcode
- For screen share on Android: Android 14+ device/emulator

## Setup

```bash
# Create the Flutter project shell
flutter create pnm_flutter_demo

# Copy the source files from this repo into the created project
# (lib/, pubspec.yaml, analysis_options.yaml)

# Get dependencies
cd pnm_flutter_demo
flutter pub get

# Run
flutter run
```

## Android Setup

Add to `android/app/src/main/AndroidManifest.xml` inside `<manifest>`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />

<application ...>
  ...
  <service
      android:name="de.julianassmann.flutter_background.IsolateHolderService"
      android:enabled="true"
      android:exported="false"
      android:foregroundServiceType="mediaProjection" />
</application>
```

## iOS Setup

Add to `ios/Runner/Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>Camera access for video calls</string>
<key>NSMicrophoneUsageDescription</key>
<string>Microphone access for audio calls</string>
<key>UIBackgroundModes</key>
<array>
  <string>audio</string>
</array>
```

For iOS screen share, a Broadcast Extension must be added in Xcode. See the [LiveKit iOS screen share guide](https://github.com/livekit/client-sdk-flutter#ios-1).

## Bridge Contract

### Web → Native

| Action | Description |
|---|---|
| `initialize_native_publisher` | Connect to LiveKit with native token |
| `publish_native_media` | Publish mic/webcam/screen share |
| `unpublish_native_media` | Unpublish mic/webcam/screen share |
| `mute_native_media` | Mute mic/webcam/screen share |
| `unmute_native_media` | Unmute mic/webcam/screen share |
| `native_heartbeat_ping` | Keepalive ping |
| `teardown_native_publisher` | Disconnect LiveKit |

### Native → Web

| Action | Description |
|---|---|
| `native_media_status` | Connection status |
| `native_track_published` | Track published |
| `native_track_unpublished` | Track unpublished |
| `native_media_muted` | Mute state change |
| `native_heartbeat_pong` | Heartbeat response |

## Demo Only

> Embeds API key/secret in the app for simplicity. Production apps must never hold the API key. Use only `serverUrl` + `access_token` in production.
