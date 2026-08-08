import CryptoJS from 'crypto-js';

/**
 * Generate HMAC-SHA256 signature for plugNmeet API authentication.
 */
function hmacSha256(message: string, secret: string): string {
  return CryptoJS.HmacSHA256(message, secret).toString(CryptoJS.enc.Hex);
}

/**
 * Standard headers for plugNmeet /auth/* endpoints (API-key gated).
 */
function authHeaders(
  apiKey: string,
  apiSecret: string,
  body: string,
): Record<string, string> {
  const signature = hmacSha256(body, apiSecret);
  return {
    'Content-Type': 'application/json',
    'API-KEY': apiKey,
    'HASH-SIGNATURE': signature,
  };
}

/**
 * Generic request helper for /auth/* endpoints.
 */
async function authRequest<T>(
  serverUrl: string,
  apiKey: string,
  apiSecret: string,
  method: string,
  body: unknown,
): Promise<T> {
  const bodyStr = JSON.stringify(body);
  const response = await fetch(`${serverUrl}/auth/${method}`, {
    method: 'POST',
    headers: authHeaders(apiKey, apiSecret, bodyStr),
    body: bodyStr,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`API error ${response.status}: ${text}`);
  }

  return await response.json() as Promise<T>;
}

// ─── Response types (subset from plugnmeet protocol) ───

export interface ApiStatusResponse {
  status: boolean;
  msg: string;
}

export interface IsRoomActiveResponse extends ApiStatusResponse {
  is_active: boolean;
}

export interface CreateRoomResponse extends ApiStatusResponse {
  room_info?: {
    room_id: string;
    sid: string;
    room_title: string;
    is_running: number;
  };
}

export interface GetJoinTokenResponse extends ApiStatusResponse {
  token?: string;
}

/**
 * Check whether a room is active.
 * POST /auth/room/isRoomActive
 */
export async function isRoomActive(
  serverUrl: string,
  apiKey: string,
  apiSecret: string,
  roomId: string,
): Promise<IsRoomActiveResponse> {
  return authRequest<IsRoomActiveResponse>(serverUrl, apiKey, apiSecret, 'room/isRoomActive', {
    room_id: roomId,
  });
}

/**
 * Create a room with default features (same defaults as login.html demo).
 * POST /auth/room/create
 */
export async function createRoom(
  serverUrl: string,
  apiKey: string,
  apiSecret: string,
  roomId: string,
  roomTitle: string,
): Promise<CreateRoomResponse> {
  const req = {
    room_id: roomId,
    empty_timeout: 60 * 60 * 2,
    metadata: {
      room_title: roomTitle,
      welcome_message:
        'Welcome to plugNmeet!<br /> To share microphone click mic icon from bottom left side.',
      room_features: {
        allow_webcams: true,
        mute_on_start: false,
        allow_screen_share: true,
        admin_only_webcams: false,
        allow_view_other_webcams: true,
        allow_view_other_users_list: true,
        room_duration: 0,
        enable_analytics: true,
        allow_virtual_bg: true,
        allow_raise_hand: true,
        allow_reactions: true,
        recording_features: {
          is_allow: true,
          is_allow_cloud: true,
          is_allow_local: true,
          enable_auto_cloud_recording: false,
          only_record_admin_webcams: false,
        },
        external_broadcasting_features: {
          is_allow: true,
          is_allow_rtmp: true,
        },
        chat_features: {
          is_allow: true,
          is_allow_file_upload: true,
          max_file_size: 50,
          allowed_file_types: ['jpg', 'png', 'zip', 'pdf'],
        },
        shared_note_pad_features: {
          is_allow: true,
        },
        whiteboard_features: {
          is_allow: true,
        },
        external_media_player_features: {
          is_allow: true,
        },
        waiting_room_features: {
          is_active: true,
        },
        breakout_room_features: {
          is_allow: true,
          allowed_number_rooms: 6,
        },
        display_external_link_features: {
          is_allow: true,
        },
        ingress_features: {
          is_allow: true,
        },
        polls_features: {
          is_allow: true,
        },
        sip_dial_in_features: {
          is_allow: true,
        },
        insights_features: {
          is_allow: true,
          transcription_features: {
            is_allow: true,
            is_allow_translation: true,
            is_allow_speech_synthesis: true,
          },
          chat_translation_features: {
            is_allow: true,
          },
          ai_features: {
            is_allow: true,
            ai_text_chat_features: {
              is_allow: true,
            },
            meeting_summarization_features: {
              is_allow: true,
            },
          },
        },
        end_to_end_encryption_features: {
          is_enabled: false,
          included_chat_messages: false,
          included_whiteboard: false,
          enabled_self_insert_encryption_key: false,
        },
      },
    },
  };

  return authRequest<CreateRoomResponse>(serverUrl, apiKey, apiSecret, 'room/create', req);
}

/**
 * Get a join token for a room, with client_type = HYBRID_WEB.
 * POST /auth/room/getJoinToken
 */
export async function getJoinToken(
  serverUrl: string,
  apiKey: string,
  apiSecret: string,
  roomId: string,
  userName: string,
  userId: string,
  isAdmin: boolean = false,
): Promise<GetJoinTokenResponse> {
  const req = {
    room_id: roomId,
    user_info: {
      name: userName,
      user_id: userId,
      is_admin: isAdmin,
      client_type: 1, // HYBRID_WEB (matches plugnmeet.ClientType enum value)
    },
  };

  return authRequest<GetJoinTokenResponse>(serverUrl, apiKey, apiSecret, 'room/getJoinToken', req);
}
