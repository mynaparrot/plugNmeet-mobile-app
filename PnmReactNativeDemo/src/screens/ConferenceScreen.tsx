import React, {useRef, useEffect, useCallback, useState} from 'react';
import {View, Text, StyleSheet, Platform, findNodeHandle, NativeModules} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import WebView, {type WebViewMessageEvent} from 'react-native-webview';
import { AudioSession, AndroidAudioTypePresets } from '@livekit/react-native';
import { ScreenCapturePickerView } from '@livekit/react-native-webrtc';
import { fromJsonString, create, toJsonString, type MessageInitShape } from '@bufbuild/protobuf';
import {
  NativeBridgeMsgSchema,
  NativeBridgeMsg,
  NativeBridgeActions,
  NativeMediaSource,
  NativeTrackKind,
} from 'plugnmeet-protocol-js';

import { LiveKitService } from '../services/LiveKitService';
import type { HybridConfig } from './JoinScreen';

interface ConferenceScreenProps {
  config: HybridConfig;
  onSessionEnded?: () => void;
}

const ConferenceScreen: React.FC<ConferenceScreenProps> = ({config, onSessionEnded}) => {
  const webviewRef = useRef<any>(null);
  const lkServiceRef = useRef<LiveKitService>(new LiveKitService());
  const nativeUserIdRef = useRef<string>('');
  const heartbeatTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastPingRef = useRef<number>(0);
  const screenCapturePickerRef = useRef<any>(null);
  const [bridgeStatus, setBridgeStatus] = useState<string>('waiting'); // waiting | connected | error

  // Sends a structured message back to the web application.
  const sendToWeb = useCallback((
    action: NativeBridgeActions,
    payload?: MessageInitShape<typeof NativeBridgeMsgSchema>['payload'],
  ) => {
    const msgObj = create(NativeBridgeMsgSchema, {
      action,
      payload,
    });
    const raw = toJsonString(NativeBridgeMsgSchema, msgObj);
    webviewRef.current?.postMessage(raw);
  }, []);

  // Handles incoming messages from the WebView.

  const handleBridgeMessage = useCallback(
    async (rawData: string) => {
      console.log('[NativeBridge] Received raw data:', rawData);
      let msg: NativeBridgeMsg;
      try {
        msg = fromJsonString(NativeBridgeMsgSchema, rawData);
        console.log('[NativeBridge] Parsed message:', msg);
      } catch (err) {
        console.warn('[NativeBridge] Failed to parse message:', rawData, err);
        return; // not valid message – ignore
      }

      if (!msg.action) {
        console.warn('[NativeBridge] Received message with no action:', msg);
        return;
      }

      const lk = lkServiceRef.current;

      switch (msg.action) {
        // Handshake: Initialize the publisher and monitor heartbeats.
        case NativeBridgeActions.INITIALIZE_NATIVE_PUBLISHER: {
          if (msg.payload?.case !== 'initializeNativePublisher') {
            sendToWeb(NativeBridgeActions.NATIVE_ERROR, {
              case: 'error',
              value: {
                msg: 'Expected initializeNativePublisher payload',
                context: 'INITIALIZE_NATIVE_PUBLISHER action',
              },
            });
            return;
          }
          const p = msg.payload.value;
          if (!p?.livekitUrl || !p?.token) {
            sendToWeb(NativeBridgeActions.NATIVE_ERROR, {
              case: 'error',
              value: {
                msg: 'Missing livekit_url or token',
                context: 'INITIALIZE_NATIVE_PUBLISHER action',
              },
            });
            return;
          }

          try {
            nativeUserIdRef.current = p.nativeUserId || '';
            const e2eeEnabled = p.e2ee?.enabled ?? false;
            const e2eeKey = p.e2ee?.key;
            await lk.connect(
              p.livekitUrl,
              p.token,
              e2eeEnabled && e2eeKey ? e2eeKey : undefined,
            );
            bridgeStatus && setBridgeStatus('connected');

            // Start heartbeat watchdog
            lastPingRef.current = Date.now();
            heartbeatTimerRef.current = setInterval(() => {
              const elapsed = Date.now() - lastPingRef.current;
              if (elapsed > 30000) {
                // Web app hasn't pinged in 30s → teardown
                lk.disconnect();
                if (heartbeatTimerRef.current) {
                  clearInterval(heartbeatTimerRef.current);
                  heartbeatTimerRef.current = null;
                }
              }
            }, 5000);
          } catch (err: any) {
            setBridgeStatus('error');
            sendToWeb(NativeBridgeActions.NATIVE_ERROR, {
              case: 'error',
              value: {
                msg: err.message || 'Failed to connect LiveKit',
                context: 'LiveKit connection',
              },
            });
          }
          break;
        }

        // Publish: Enable mic or webcam.
        case NativeBridgeActions.PUBLISH_NATIVE_MEDIA: {
          if (msg.payload?.case !== 'mediaSource') {
            console.warn('[NativeBridge] Expected mediaSource payload');
            return;
          }
          const source = msg.payload.value.source;
          try {
            if (source === NativeMediaSource.MIC) {
              await lk.enableMic();
            } else if (source === NativeMediaSource.WEBCAM) {
              await lk.enableWebcam();
            } else if (source === NativeMediaSource.SCREENSHARE) {
              if (Platform.OS === 'ios') {
                const reactTag = findNodeHandle(screenCapturePickerRef.current);
                if (reactTag) {
                  await NativeModules.ScreenCapturePickerViewManager.show(reactTag);
                }
              }
              await lk.enableScreenShare();
            }
            sendToWeb(NativeBridgeActions.NATIVE_TRACK_PUBLISHED, {
              case: 'trackState',
              value: {
                userId: nativeUserIdRef.current,
                kind: source === NativeMediaSource.MIC ? NativeTrackKind.AUDIO : NativeTrackKind.VIDEO,
                source,
              },
            });
          } catch (err: any) {
            sendToWeb(NativeBridgeActions.NATIVE_ERROR, {
              case: 'error',
              value: {
                msg: err.message || 'Failed to publish',
                context: `Publishing ${NativeMediaSource[source]}`,
              },
            });
          }
          break;
        }

        // Unpublish: Disable mic or webcam.
        case NativeBridgeActions.UNPUBLISH_NATIVE_MEDIA: {
          console.log('[NativeBridge] Handling UNPUBLISH_NATIVE_MEDIA');
          if (msg.payload?.case !== 'mediaSource') {
            console.warn('[NativeBridge] Expected mediaSource payload');
            return;
          }
          const source = msg.payload.value.source;
          console.log(`[NativeBridge] Unpublishing source: ${source}`);
          try {
            if (source === NativeMediaSource.MIC) {
              console.log('[NativeBridge] Calling lk.disableMic()');
              await lk.disableMic();
            } else if (source === NativeMediaSource.WEBCAM) {
              console.log('[NativeBridge] Calling lk.disableWebcam()');
              await lk.disableWebcam();
            } else if (source === NativeMediaSource.SCREENSHARE) {
              console.log('[NativeBridge] Calling lk.disableScreenShare()');
              await lk.disableScreenShare();
            }
            console.log('[NativeBridge] Unpublish successful, sending NATIVE_TRACK_UNPUBLISHED');
            sendToWeb(NativeBridgeActions.NATIVE_TRACK_UNPUBLISHED, {
              case: 'trackState',
              value: {
                userId: nativeUserIdRef.current,
                kind: source === NativeMediaSource.MIC ? NativeTrackKind.AUDIO : NativeTrackKind.VIDEO,
                source,
              },
            });
          } catch (err: any) {
            console.error('[NativeBridge] Failed to unpublish:', err);
            sendToWeb(NativeBridgeActions.NATIVE_ERROR, {
              case: 'error',
              value: {
                msg: err.message || 'Failed to unpublish',
                context: `Unpublishing ${NativeMediaSource[source]}`,
              },
            });
          }
          break;
        }

        // Mute: Stop sending audio/video tracks.
        case NativeBridgeActions.MUTE_NATIVE_MEDIA: {
          if (msg.payload?.case !== 'mediaSource') {
            console.warn('[NativeBridge] Expected mediaSource payload');
            return;
          }
          const source = msg.payload.value.source;
          try {
            if (source === NativeMediaSource.MIC) {
              await lk.muteMic();
            } else if (source === NativeMediaSource.WEBCAM) {
              await lk.muteWebcam();
            } else if (source === NativeMediaSource.SCREENSHARE) {
              await lk.disableScreenShare();
            }
            sendToWeb(NativeBridgeActions.NATIVE_MEDIA_MUTED, {
              case: 'mediaMuted',
              value: {
                source,
                muted: true,
              },
            });
          } catch (err: any) {
            sendToWeb(NativeBridgeActions.NATIVE_ERROR, {
              case: 'error',
              value: {
                msg: err.message || 'Failed to mute',
                context: `Muting ${NativeMediaSource[source]}`,
              },
            });
          }
          break;
        }

        // Unmute: Resume sending audio/video tracks.
        case NativeBridgeActions.UNMUTE_NATIVE_MEDIA: {
          if (msg.payload?.case !== 'mediaSource') {
            console.warn('[NativeBridge] Expected mediaSource payload');
            return;
          }
          const source = msg.payload.value.source;
          try {
            if (source === NativeMediaSource.MIC) {
              await lk.unmuteMic();
            } else if (source === NativeMediaSource.WEBCAM) {
              await lk.unmuteWebcam();
            } else if (source === NativeMediaSource.SCREENSHARE) {
              await lk.enableScreenShare();
            }
            sendToWeb(NativeBridgeActions.NATIVE_MEDIA_MUTED, {
              case: 'mediaMuted',
              value: {
                source,
                muted: false,
              },
            });
          } catch (err: any) {
            sendToWeb(NativeBridgeActions.NATIVE_ERROR, {
              case: 'error',
              value: {
                msg: err.message || 'Failed to unmute',
                context: `Unmuting ${NativeMediaSource[source]}`,
              },
            });
          }
          break;
        }

        // Heartbeat: Respond to web pings to keep the connection alive.
        case NativeBridgeActions.NATIVE_HEARTBEAT_PING: {
          if (msg.payload?.case !== 'heartbeat') {
            console.warn('[NativeBridge] Expected heartbeat payload');
            return;
          }
          lastPingRef.current = msg.payload.value.ts ? Number(msg.payload.value.ts) : Date.now();
          sendToWeb(NativeBridgeActions.NATIVE_HEARTBEAT_PONG, {
            case: 'heartbeat',
            value: {
              ts: BigInt(Date.now()).toString(),
            },
          });
          break;
        }

        // Teardown: Disconnect and clean up resources.
        case NativeBridgeActions.TEARDOWN_NATIVE_PUBLISHER: {
          await lk.disconnect();
          if (heartbeatTimerRef.current) {
            clearInterval(heartbeatTimerRef.current);
            heartbeatTimerRef.current = null;
          }
          setBridgeStatus('waiting');
          onSessionEnded?.();
          break;
        }

        default:
          // unknown action — silently ignored
          break;
      }
    },
    [sendToWeb, bridgeStatus],
  );

  // Setup callback listeners for local track and connection status.

  useEffect(() => {
    const lk = lkServiceRef.current;
    lk.setCallbacks({
      onConnected: () => {
        setBridgeStatus('connected');
      },
      onDisconnected: (_reason) => {
        setBridgeStatus('waiting');
      },
      onError: (err) => {
        setBridgeStatus('error');
        sendToWeb(NativeBridgeActions.NATIVE_ERROR, {
          case: 'error',
          value: {
            msg: err.message,
            context: 'LiveKit service error',
          },
        });
      },
      onTrackPublished: (source) => {
        sendToWeb(NativeBridgeActions.NATIVE_TRACK_PUBLISHED, {
          case: 'trackState',
          value: {
            userId: nativeUserIdRef.current,
            kind: source === 'mic' ? NativeTrackKind.AUDIO : NativeTrackKind.VIDEO,
            source: source === 'mic' ? NativeMediaSource.MIC : source === 'webcam' ? NativeMediaSource.WEBCAM : NativeMediaSource.SCREENSHARE,
          },
        });
      },
      onTrackUnpublished: (source) => {
        sendToWeb(NativeBridgeActions.NATIVE_TRACK_UNPUBLISHED, {
          case: 'trackState',
          value: {
            userId: nativeUserIdRef.current,
            kind: source === 'mic' ? NativeTrackKind.AUDIO : NativeTrackKind.VIDEO,
            source: source === 'mic' ? NativeMediaSource.MIC : source === 'webcam' ? NativeMediaSource.WEBCAM : NativeMediaSource.SCREENSHARE,
          },
        });
      },
    });

    return () => {
      lk.disconnect().then();
      if (heartbeatTimerRef.current) {
        clearInterval(heartbeatTimerRef.current);
      }
    };
  }, [sendToWeb]);

  // Receives raw messages from the WebView and routes them to the bridge handler.

  const onMessage = useCallback(
    (event: WebViewMessageEvent) => {
      handleBridgeMessage(event.nativeEvent.data).then();
    },
    [handleBridgeMessage],
  );

  // Configure and manage native device audio sessions.

  useEffect(() => {
    const startAudio = async () => {
      await AudioSession.configureAudio({
        android: {
          audioTypeOptions: AndroidAudioTypePresets.communication,
        },
      });
      await AudioSession.startAudioSession();
    };

    startAudio().then();
    return () => {
      AudioSession.stopAudioSession().then();
    };
  }, []);

  // Build connection URI and render UI elements.

  const webviewUri = `${config.serverUrl}/?access_token=${config.jwt}`;

  const statusDotStyle =
    bridgeStatus === 'connected'
      ? styles.statusDotConnected
      : bridgeStatus === 'error'
      ? styles.statusDotError
      : styles.statusDotWaiting;

  return (
    <SafeAreaView style={styles.container}>
      {/* Bridge status indicator */}
      <View style={styles.statusBar}>
        <View
          style={[styles.statusDot, statusDotStyle]}
        />
        <Text style={styles.statusText}>
          Native publisher:{' '}
          {bridgeStatus === 'connected'
            ? 'Connected'
            : // ... (rest of the file is the same)
            bridgeStatus === 'error'
            ? 'Error'
            : 'Waiting for handshake...'}
        </Text>
      </View>

      {/* WebView */}
      <WebView
        ref={webviewRef}
        source={{ uri: webviewUri }}
        onMessage={onMessage}
        style={styles.webview}
        javaScriptEnabled={true}
        domStorageEnabled={true}
        mediaPlaybackRequiresUserAction={false}
        allowsInlineMediaPlayback={true}
        startInLoadingState={true}
      />

      {Platform.OS === 'ios' && (
        <ScreenCapturePickerView ref={screenCapturePickerRef} />
      )}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  warningBanner: {
    backgroundColor: '#FFF3CD',
    borderColor: '#FFCA2C',
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  warningText: {
    color: '#664D03',
    fontSize: 11,
    fontWeight: '600',
    textAlign: 'center',
  },
  statusBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#FFF',
    borderBottomWidth: 1,
    borderBottomColor: '#DDD',
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
  },
  statusDotConnected: {
    backgroundColor: '#4CAF50',
  },
  statusDotError: {
    backgroundColor: '#F44336',
  },
  statusDotWaiting: {
    backgroundColor: '#FFC107',
  },
  statusText: {
    fontSize: 12,
    color: '#666',
  },
  webview: {
    flex: 1,
  },
});

export default ConferenceScreen;
