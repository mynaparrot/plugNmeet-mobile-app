# PnmReactNativeDemo

A React Native demo app demonstrating plugNmeet's hybrid integration pattern: native publisher + web subscriber.

## Overview

This app hosts the plugNmeet web client in a WebView while using the native [LiveKit React Native SDK](https://github.com/livekit/client-sdk-react-native)) to publish mic, webcam, and screen share. The web client subscribes to all media (web subscribers don't publish). This avoids browser media permission issues on mobile.

### Architecture

- **Join Screen**: Server URL, API key/secret, room name, user name
- **Conference Screen**: WebView hosting plugNmeet web client
- **Native Bridge**: Proto3 JSON messages over `postMessage`/`onMessage`
- **LiveKit Service**: `livekit-client` managing native mic/webcam/screen share publishing
- **API Service**: Fetch-based plugNmeet REST API calls

### Flow

1. User fills in credentials on JoinScreen
2. App calls `/auth/room/create` then `/auth/room/getJoinToken` with `client_type = HYBRID_WEB`
3. ConferenceScreen loads the web client at `<serverUrl>/?access_token=<jwt>`
4. Web client sends `initialize_native_publisher` -> native app connects LiveKit with `native_token`
5. Bridge handles `publish_native_media`, `unpublish_native_media`, `mute_native_media`, etc.
6. Native app publishes mic/webcam/screen share as `[userID]-native` participant

## Prerequisites

- Node 22+
- React Native CLI environment (see [Set Up Your Environment](https://reactnative.dev/docs/set-up-your-environment))
- Android Studio / Xcode for platform builds

## Build & Run

```bash
cd PnmReactNativeDemo

# Start Metro
npm start

# Android
npm run android

# iOS (after installing pods)
cd ios && bundle exec pod install && cd ..
npm run ios
```

## Bridge Contract

### Web -> Native

| Action | Payload | Description |
|---|---|---|
| `initialize_native_publisher` | `{livekit_url, token, native_user_id}` | Connect to LiveKit |
| `publish_native_media` | `{source: MIC\|WEBCAM\|SCREENSHARE}` | Publish a track |
| `unpublish_native_media` | `{source: MIC\|WEBCAM\|SCREENSHARE}` | Unpublish a track |
| `mute_native_media` | `{source: MIC\|WEBCAM\|SCREENSHARE}` | Mute a track |
| `unmute_native_media` | `{source: MIC\|WEBCAM\|SCREENSHARE}` | Unmute a track |
| `native_heartbeat_ping` | `{ts}` | Keepalive ping |
| `teardown_native_publisher` | — | Disconnect LiveKit |

### Native -> Web

| Action | Payload | Description |
|---|---|---|
| `native_media_status` | `{status: connected\|error, error?}` | Connection state |
| `native_track_published` | `{user_id, kind, source}` | Track published |
| `native_track_unpublished` | `{user_id, kind, source}` | Track unpublished |
| `native_media_muted` | `{source, muted}` | Mute state change |
| `native_heartbeat_pong` | `{ts}` | Heartbeat response |

## Screen Share

- **Android**: `localParticipant.setScreenShareEnabled(true)` triggers `getDisplayMedia()` from `@livekit/react-native-webrtc` which handles MediaProjection permission dialog automatically
- **iOS**: `ScreenCapturePickerView` is rendered and triggered via `NativeModules.ScreenCapturePickerViewManager.show()` before enabling. Note: a Broadcast Extension target must be added to the Xcode project for full iOS screen share support

Requires `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, and `POST_NOTIFICATIONS` permissions on Android.

## Demo Only

> Embeds API key/secret in the app for simplicity. Production apps must never hold the API key. Use only `serverUrl` + `access_token` in production.
