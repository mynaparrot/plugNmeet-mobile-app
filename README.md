# plugnmeet-mobile-app

Demo apps showcasing plugNmeet's hybrid integration pattern — host the web client in a WebView and publish media (mic, webcam, screen share) natively. This is necessary because mobile browsers don't support screen sharing.

Follow the official documentation for integration details: https://www.plugnmeet.org/docs/developer-guide/mobile-app-integration

plugNmeet doesn't require a mobile SDK; the hybrid approach uses the web client in a WebView with a native media bridge. Check the demo apps to understand the pattern:

- [PnmNativeAndroidDemo](./PnmNativeAndroidDemo) — Kotlin / Jetpack Compose / Android WebView + [LiveKit Android SDK](https://github.com/livekit/client-sdk-android)
- [PnmReactNativeDemo](./PnmReactNativeDemo) — React Native WebView + [LiveKit React Native SDK](https://github.com/livekit/client-sdk-react-native)
- [PnmFlutterDemo](./PnmFlutterDemo) — Flutter WebView + [LiveKit Flutter Client SDK](https://github.com/livekit/client-sdk-flutter)

## Quick Test (Android APK)

[![Download APK](https://img.shields.io/badge/Download-Latest_APK-blue)](https://github.com/mynaparrot/plugNmeet-mobile-app/releases/download/latest/plugnmeet-demo-debug.apk)

1. Click the download button → download `plugnmeet-demo-debug.apk` from the **[Latest Debug Build](https://github.com/mynaparrot/plugnmeet-mobile-app/releases)** release
2. Install on Android 7.0+ device

> The APK is a debug build signed with a debug keystore. It connects to demo.plugnmeet.com by default.
