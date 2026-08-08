import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:crypto/crypto.dart';

String _hmacSha256(String message, String secret) {
  final hmac = Hmac(sha256, utf8.encode(secret));
  return hmac.convert(utf8.encode(message)).toString();
}

Map<String, String> _authHeaders(String apiKey, String apiSecret, String body) {
  return {
    'Content-Type': 'application/json',
    'API-KEY': apiKey,
    'HASH-SIGNATURE': _hmacSha256(body, apiSecret),
  };
}

Future<Map<String, dynamic>> _authRequest(
  String serverUrl,
  String apiKey,
  String apiSecret,
  String method,
  Map<String, dynamic> body,
) async {
  final bodyStr = jsonEncode(body);
  final uri = Uri.parse('$serverUrl/auth/$method');
  final response = await http.post(
    uri,
    headers: _authHeaders(apiKey, apiSecret, bodyStr),
    body: bodyStr,
  );

  if (response.statusCode != 200) {
    throw Exception('API error ${response.statusCode}: ${response.body}');
  }

  return jsonDecode(response.body) as Map<String, dynamic>;
}

Future<Map<String, dynamic>> isRoomActive(
  String serverUrl,
  String apiKey,
  String apiSecret,
  String roomId,
) async {
  return _authRequest(serverUrl, apiKey, apiSecret, 'room/isRoomActive', {
    'room_id': roomId,
  });
}

Future<Map<String, dynamic>> createRoom(
  String serverUrl,
  String apiKey,
  String apiSecret,
  String roomId,
  String roomTitle,
) async {
  return _authRequest(serverUrl, apiKey, apiSecret, 'room/create', {
    'room_id': roomId,
    'empty_timeout': 60 * 60 * 2,
    'metadata': {
      'room_title': roomTitle,
      'welcome_message':
          'Welcome to plugNmeet!<br /> To share microphone click mic icon from bottom left side.',
      'room_features': {
        'allow_webcams': true,
        'mute_on_start': false,
        'allow_screen_share': true,
        'admin_only_webcams': false,
        'allow_view_other_webcams': true,
        'allow_view_other_users_list': true,
        'room_duration': 0,
        'enable_analytics': true,
        'allow_virtual_bg': true,
        'allow_raise_hand': true,
        'allow_reactions': true,
        'recording_features': {
          'is_allow': true,
          'is_allow_cloud': true,
          'is_allow_local': true,
          'enable_auto_cloud_recording': false,
          'only_record_admin_webcams': false,
        },
        'external_broadcasting_features': {
          'is_allow': true,
          'is_allow_rtmp': true,
        },
        'chat_features': {
          'is_allow': true,
          'is_allow_file_upload': true,
          'max_file_size': 50,
          'allowed_file_types': ['jpg', 'png', 'zip', 'pdf'],
        },
        'shared_note_pad_features': {
          'is_allow': true,
        },
        'whiteboard_features': {
          'is_allow': true,
        },
        'external_media_player_features': {
          'is_allow': true,
        },
        'waiting_room_features': {
          'is_active': true,
        },
        'breakout_room_features': {
          'is_allow': true,
          'allowed_number_rooms': 6,
        },
        'display_external_link_features': {
          'is_allow': true,
        },
        'ingress_features': {
          'is_allow': true,
        },
        'polls_features': {
          'is_allow': true,
        },
        'sip_dial_in_features': {
          'is_allow': true,
        },
        'insights_features': {
          'is_allow': true,
          'transcription_features': {
            'is_allow': true,
            'is_allow_translation': true,
            'is_allow_speech_synthesis': true,
          },
          'chat_translation_features': {
            'is_allow': true,
          },
          'ai_features': {
            'is_allow': true,
            'ai_text_chat_features': {
              'is_allow': true,
            },
            'meeting_summarization_features': {
              'is_allow': true,
            },
          },
        },
        'end_to_end_encryption_features': {
          'is_enabled': false,
          'included_chat_messages': false,
          'included_whiteboard': false,
          'enabled_self_insert_encryption_key': false,
        },
      },
    },
  });
}

Future<Map<String, dynamic>> getJoinToken(
  String serverUrl,
  String apiKey,
  String apiSecret,
  String roomId,
  String userName,
  String userId,
  bool isAdmin,
) async {
  return _authRequest(serverUrl, apiKey, apiSecret, 'room/getJoinToken', {
    'room_id': roomId,
    'user_info': {
      'name': userName,
      'user_id': userId,
      'is_admin': isAdmin,
      'client_type': 1, // HYBRID_WEB
    },
  });
}
