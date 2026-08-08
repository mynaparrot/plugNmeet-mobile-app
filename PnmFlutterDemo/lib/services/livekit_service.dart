import 'package:flutter/foundation.dart';
import 'package:livekit_client/livekit_client.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:flutter_background/flutter_background.dart';

enum MediaSource { mic, webcam, screenshare }

class LiveKitService {
  Room? _room;
  BaseKeyProvider? _keyProvider;
  LocalParticipant? get participant => _room?.localParticipant;
  bool get isConnected => _room?.connectionState == ConnectionState.connected;

  final ValueNotifier<bool> connected = ValueNotifier(false);
  final ValueNotifier<String?> error = ValueNotifier(null);

  VoidCallback? onTrackPublished;
  VoidCallback? onTrackUnpublished;

  /// Connects to LiveKit with the native twin token.
  ///
  /// [e2eeKey] is the optional shared E2EE key sent by the web client. When
  /// non-empty, end-to-end encryption is enabled for published media.
  Future<void> connect(String url, String token, {String? e2eeKey}) async {
    try {
      // H.264 for hardware encoder support on mobile (lower CPU/battery vs VP8)
      const videoPublishOptions = VideoPublishOptions(videoCodec: 'h264');

      RoomOptions roomOptions = const RoomOptions(
        defaultVideoPublishOptions: videoPublishOptions,
      );

      if (e2eeKey != null && e2eeKey.isNotEmpty) {
        await _disposeKeyProvider();
        // Shared-key AES-GCM frame encryption, mirroring the web client's
        // ExternalE2EEKeyProvider (media frames only, data packets stay plain).
        _keyProvider = await BaseKeyProvider.create(sharedKey: true);
        await _keyProvider!.setSharedKey(e2eeKey);
        roomOptions = RoomOptions(
          defaultVideoPublishOptions: videoPublishOptions,
          // `encryption` would additionally encrypt data channel packets,
          // which the web client does not do, so keep media-only E2EE.
          // ignore: deprecated_member_use
          e2eeOptions: E2EEOptions(keyProvider: _keyProvider!),
        );
      }

      _room = Room(roomOptions: roomOptions);
      _room!.addListener(_onRoomChange);

      await _room!.connect(
        url,
        token,
        connectOptions: const ConnectOptions(autoSubscribe: false),
      );
      connected.value = true;
    } catch (e) {
      error.value = e.toString();
      rethrow;
    }
  }

  void _onRoomChange() {
    connected.value = _room?.connectionState == ConnectionState.connected;
  }

  Future<void> _disposeKeyProvider() async {
    final provider = _keyProvider;
    _keyProvider = null;
    if (provider != null) {
      await provider.keyProvider.dispose();
    }
  }

  Future<void> enableMic() async {
    await _room?.localParticipant?.setMicrophoneEnabled(true);
  }

  Future<void> disableMic() async {
    final pub = _room?.localParticipant?.getTrackPublicationBySource(TrackSource.microphone);
    if (pub != null) {
      await _room?.localParticipant?.removePublishedTrack(pub.sid);
    }
  }

  Future<void> muteMic() async {
    await _room?.localParticipant?.setMicrophoneEnabled(false);
  }

  Future<void> unmuteMic() async {
    await _room?.localParticipant?.setMicrophoneEnabled(true);
  }

  Future<void> enableWebcam() async {
    await _room?.localParticipant?.setCameraEnabled(true);
  }

  Future<void> disableWebcam() async {
    final pub = _room?.localParticipant?.getTrackPublicationBySource(TrackSource.camera);
    if (pub != null) {
      await _room?.localParticipant?.removePublishedTrack(pub.sid);
    }
  }

  Future<void> muteWebcam() async {
    await _room?.localParticipant?.setCameraEnabled(false);
  }

  Future<void> unmuteWebcam() async {
    await _room?.localParticipant?.setCameraEnabled(true);
  }

  Future<bool> enableScreenShare() async {
    if (defaultTargetPlatform == TargetPlatform.android) {
      final hasPermission = await Helper.requestCapturePermission();
      if (!hasPermission) return false;

      final androidConfig = const FlutterBackgroundAndroidConfig(
        notificationTitle: 'Screen Sharing',
        notificationText: 'Sharing screen via plugNmeet',
        notificationImportance: AndroidNotificationImportance.normal,
      );
      await FlutterBackground.initialize(androidConfig: androidConfig);
      if (!FlutterBackground.isBackgroundExecutionEnabled) {
        await FlutterBackground.enableBackgroundExecution();
      }
      // Give the foreground service time to enter foreground state
      await Future.delayed(const Duration(milliseconds: 500));
    }

    if (defaultTargetPlatform == TargetPlatform.iOS) {
      // iOS needs broadcast extension setup in Xcode
    }

    await _room?.localParticipant?.setScreenShareEnabled(true);
    return true;
  }

  Future<void> disableScreenShare() async {
    await _room?.localParticipant?.setScreenShareEnabled(false);
  }

  Future<void> disconnect() async {
    await _room?.disconnect();
    _room = null;
    await _disposeKeyProvider();
    connected.value = false;
    error.value = null;
  }

  void dispose() {
    _room?.dispose();
    _room = null;
    _disposeKeyProvider();
  }
}
