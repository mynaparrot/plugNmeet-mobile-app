import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../services/livekit_service.dart';

class ConferenceScreen extends StatefulWidget {
  final String serverUrl;
  final String jwt;

  const ConferenceScreen({
    super.key,
    required this.serverUrl,
    required this.jwt,
  });

  @override
  State<ConferenceScreen> createState() => _ConferenceScreenState();
}

class _ConferenceScreenState extends State<ConferenceScreen> {
  final _lk = LiveKitService();
  String _nativeUserId = '';
  String _bridgeStatus = 'waiting';
  Timer? _heartbeatTimer;
  int _lastPing = 0;

  late final WebViewController _webCtrl;

  @override
  void initState() {
    super.initState();

    _webCtrl = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..addJavaScriptChannel(
        'PnmBridge',
        onMessageReceived: _onBridgeMessage,
      )
      ..setNavigationDelegate(NavigationDelegate(
        onPageFinished: (_) => _injectBridge(),
      ))
      ..loadRequest(Uri.parse(
          '${widget.serverUrl}/?access_token=${widget.jwt}'));

    _lk.connected.addListener(_onLkConnection);
  }

  void _injectBridge() {
    _webCtrl.runJavaScript('''
      window.PnmNative = {
        postMessage: function(data) {
          PnmBridge.postMessage(data);
        }
      };
    ''');
  }

  void _onLkConnection() {
    if (_lk.connected.value) {
      _send('NATIVE_MEDIA_STATUS', {
        'case': 'status',
        'value': {'status': 'connected'},
      });
    }
  }

  void _send(String action, [Map<String, dynamic>? payload]) {
    final Map<String, dynamic> msg = {'action': action};
    if (payload != null) {
      final case_ = payload['case'] as String?;
      final value = payload['value'];
      if (case_ != null && value != null) {
        msg[case_] = value;
      }
    }
    final json = jsonEncode(msg);
    _webCtrl.runJavaScript(
        "window.dispatchEvent(new MessageEvent('message', { data: '${json.replaceAll("\\", "\\\\").replaceAll("'", "\\'").replaceAll('\n', '\\n')}' }))");
  }

  void _sendError(String msg, String context) {
    _send('NATIVE_ERROR', {
      'case': 'error',
      'value': {'msg': msg, 'context': context},
    });
  }

  void _sendTrackState(String action, String source) {
    _send(action, {
      'case': 'trackState',
      'value': {
        'userId': _nativeUserId,
        'kind': source == 'MIC' ? 'AUDIO' : 'VIDEO',
        'source': source,
      },
    });
  }

  void _onBridgeMessage(JavaScriptMessage event) {
    final raw = event.message;
    Map<String, dynamic> msg;
    try {
      msg = jsonDecode(raw) as Map<String, dynamic>;
    } catch (_) {
      return;
    }

    final action = msg['action'] as String?;
    if (action == null) return;

    switch (action) {
      case 'INITIALIZE_NATIVE_PUBLISHER':
        _handleInit(
            msg['initializeNativePublisher'] as Map<String, dynamic>?);
      case 'PUBLISH_NATIVE_MEDIA':
        _handlePublish(msg['mediaSource']?['source'] as String?);
      case 'UNPUBLISH_NATIVE_MEDIA':
        _handleUnpublish(msg['mediaSource']?['source'] as String?);
      case 'MUTE_NATIVE_MEDIA':
        _handleMute(msg['mediaSource']?['source'] as String?);
      case 'UNMUTE_NATIVE_MEDIA':
        _handleUnmute(msg['mediaSource']?['source'] as String?);
      case 'NATIVE_HEARTBEAT_PING':
        _handleHeartbeat(msg['heartbeat']?['ts']);
      case 'TEARDOWN_NATIVE_PUBLISHER':
        _handleTeardown();
    }
  }

  Future<void> _handleInit(Map<String, dynamic>? val) async {
    if (val == null) {
      _sendError('Expected payload', 'INITIALIZE_NATIVE_PUBLISHER');
      return;
    }
    final lkUrl = val['livekitUrl'] as String?;
    final token = val['token'] as String?;
    if (lkUrl == null || token == null) {
      _sendError('Missing livekitUrl or token', 'INITIALIZE_NATIVE_PUBLISHER');
      return;
    }

    _nativeUserId = val['nativeUserId'] as String? ?? '';

    final e2ee = val['e2ee'] as Map<String, dynamic>?;
    final e2eeEnabled = e2ee?['enabled'] == true;
    final e2eeKey = e2ee?['key'] as String?;

    try {
      await _lk.connect(
        lkUrl,
        token,
        e2eeKey: e2eeEnabled && e2eeKey != null ? e2eeKey : null,
      );
      setState(() => _bridgeStatus = 'connected');

      _lastPing = DateTime.now().millisecondsSinceEpoch;
      _heartbeatTimer = Timer.periodic(const Duration(seconds: 5), (_) {
        if (DateTime.now().millisecondsSinceEpoch - _lastPing > 30000) {
          _lk.disconnect();
          _heartbeatTimer?.cancel();
        }
      });
    } catch (e) {
      setState(() => _bridgeStatus = 'error');
      _sendError(e.toString(), 'LiveKit connection');
    }
  }

  Future<void> _handlePublish(String? source) async {
    if (source == null) return;
    try {
      if (source == 'MIC') {
        await _lk.enableMic();
      } else if (source == 'WEBCAM') {
        await _lk.enableWebcam();
      } else if (source == 'SCREENSHARE') {
        await _lk.enableScreenShare();
      }
      _sendTrackState('NATIVE_TRACK_PUBLISHED', source);
    } catch (e) {
      _sendError(e.toString(), 'Publishing $source');
    }
  }

  Future<void> _handleUnpublish(String? source) async {
    if (source == null) return;
    try {
      if (source == 'MIC') {
        await _lk.disableMic();
      } else if (source == 'WEBCAM') {
        await _lk.disableWebcam();
      } else if (source == 'SCREENSHARE') {
        await _lk.disableScreenShare();
      }
      _sendTrackState('NATIVE_TRACK_UNPUBLISHED', source);
    } catch (e) {
      _sendError(e.toString(), 'Unpublishing $source');
    }
  }

  Future<void> _handleMute(String? source) async {
    if (source == null) return;
    try {
      if (source == 'MIC') {
        await _lk.muteMic();
      } else if (source == 'WEBCAM') {
        await _lk.muteWebcam();
      } else if (source == 'SCREENSHARE') {
        await _lk.disableScreenShare();
      }
      _send('NATIVE_MEDIA_MUTED', {
        'case': 'mediaMuted',
        'value': {'source': source, 'muted': true},
      });
    } catch (e) {
      _sendError(e.toString(), 'Muting $source');
    }
  }

  Future<void> _handleUnmute(String? source) async {
    if (source == null) return;
    try {
      if (source == 'MIC') {
        await _lk.unmuteMic();
      } else if (source == 'WEBCAM') {
        await _lk.unmuteWebcam();
      } else if (source == 'SCREENSHARE') {
        await _lk.enableScreenShare();
      }
      _send('NATIVE_MEDIA_MUTED', {
        'case': 'mediaMuted',
        'value': {'source': source, 'muted': false},
      });
    } catch (e) {
      _sendError(e.toString(), 'Unmuting $source');
    }
  }

  void _handleHeartbeat(dynamic ts) {
    _lastPing = ts != null
        ? int.tryParse(ts.toString()) ?? DateTime.now().millisecondsSinceEpoch
        : DateTime.now().millisecondsSinceEpoch;

    _send('NATIVE_HEARTBEAT_PONG', {
      'case': 'heartbeat',
      'value': {'ts': DateTime.now().millisecondsSinceEpoch.toString()},
    });
  }

  Future<void> _handleTeardown() async {
    await _lk.disconnect();
    _heartbeatTimer?.cancel();
    if (mounted) {
      Navigator.pop(context);
    }
  }

  @override
  void dispose() {
    _heartbeatTimer?.cancel();
    _lk.connected.removeListener(_onLkConnection);
    _lk.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final statusColor = _bridgeStatus == 'connected'
        ? Colors.green
        : _bridgeStatus == 'error'
            ? Colors.red
            : Colors.orange;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              color: Colors.white,
              child: Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: statusColor,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    'Native publisher: '
                    '${_bridgeStatus == 'connected' ? 'Connected' : _bridgeStatus == 'error' ? 'Error' : 'Waiting...'}',
                    style: const TextStyle(fontSize: 12, color: Colors.black54),
                  ),
                ],
              ),
            ),
            Expanded(child: WebViewWidget(controller: _webCtrl)),
          ],
        ),
      ),
    );
  }
}
