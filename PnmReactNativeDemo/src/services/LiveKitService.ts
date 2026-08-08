import {Room, RoomEvent, ConnectionState, Track, LocalTrackPublication} from 'livekit-client';
import type {E2EEOptions, RoomOptions} from 'livekit-client';
import {RNKeyProvider, RNE2EEManager} from '@livekit/react-native';

/**
 * Callbacks for native media state changes.
 */
export interface LiveKitServiceCallbacks {
  onConnected?: () => void;
  onDisconnected?: (reason?: string) => void;
  onError?: (error: Error) => void;
  onTrackPublished?: (source: 'mic' | 'webcam' | 'screenshare') => void;
  onTrackUnpublished?: (source: 'mic' | 'webcam' | 'screenshare') => void;
  onMuted?: (source: 'mic' | 'webcam' | 'screenshare', muted: boolean) => void;
  onScreenShareRequest?: () => void;
}

/**
 * Manages a native LiveKit publisher connection.
 * Connects with the native twin token, publishes mic/webcam on demand.
 */
export class LiveKitService {
  private room: Room | null = null;
  private callbacks: LiveKitServiceCallbacks = {};
  private micPublication: LocalTrackPublication | undefined;
  private webcamPublication: LocalTrackPublication | undefined;
  private screenSharePublication: LocalTrackPublication | undefined;
  private keyProvider: RNKeyProvider | null = null;
  private e2eeManager: RNE2EEManager | null = null;

  setCallbacks(cbs: LiveKitServiceCallbacks): void {
    this.callbacks = cbs;
  }

  /**
   * Connect to LiveKit with the native twin token.
   * If already connected, tears down the previous connection first.
   */
  async connect(url: string, token: string, e2eeKey?: string): Promise<void> {
    // Re-initialization: tear down existing connection first
    if (this.room) {
      // Clean up old E2EE before disconnect
      if (this.e2eeManager) {
        this.e2eeManager = null;
      }
      if (this.keyProvider) {
        this.keyProvider.dispose();
        this.keyProvider = null;
      }
      await this.disconnect();
    }

    // Prepare room options
    const roomOptions: RoomOptions = {
      publishDefaults: {
        videoCodec: 'h264',
      },
    };

    // Set up E2EE if key is provided
    if (e2eeKey) {
      // Clean up any previous E2EE instances
      this.keyProvider?.dispose();
      this.e2eeManager = null;
      this.keyProvider = null;

      this.keyProvider = new RNKeyProvider({ sharedKey: true });
      await this.keyProvider.setSharedKey(e2eeKey);
      this.e2eeManager = new RNE2EEManager(this.keyProvider);

      roomOptions.e2ee = {
        keyProvider: this.keyProvider,
        e2eeManager: this.e2eeManager,
      } as E2EEOptions;
    }

    this.room = new Room(roomOptions);

    if (this.e2eeManager) {
      this.e2eeManager.setup(this.room);
    }

    this.room.on(RoomEvent.Disconnected, reason => {
      this.callbacks.onDisconnected?.(reason?.toString());
    });

    this.room.on(RoomEvent.ConnectionStateChanged, (state: ConnectionState) => {
      if (state === ConnectionState.Connected) {
        this.callbacks.onConnected?.();
      }
    });

    try {
      await this.room.connect(url, token, {
        autoSubscribe: false, // as we've only publisher permission
      });
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Publish microphone */
  async enableMic(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      this.micPublication = await this.room.localParticipant.setMicrophoneEnabled(
        true,
      );
      this.callbacks.onTrackPublished?.('mic');
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Unpublish microphone */
  async disableMic(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      const publication = this.room.localParticipant.getTrackPublication(Track.Source.Microphone);
      if (publication && this.micPublication) { // Ensure we have a publication to unpublish and a local reference
        await this.room.localParticipant.unpublishTrack(this.micPublication.track);
        this.micPublication = undefined;
      }
      this.callbacks.onTrackUnpublished?.('mic');
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Publish webcam */
  async enableWebcam(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      this.webcamPublication = await this.room.localParticipant.setCameraEnabled(
        true,
      );
      this.callbacks.onTrackPublished?.('webcam');
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Unpublish webcam */
  async disableWebcam(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      const publication = this.room.localParticipant.getTrackPublication(Track.Source.Camera);
      if (publication && this.webcamPublication) { // Ensure we have a publication to unpublish and a local reference
        await this.room.localParticipant.unpublishTrack(this.webcamPublication.track);
        this.webcamPublication = undefined;
      }
      this.callbacks.onTrackUnpublished?.('webcam');
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Mute microphone (keep track published, no audio) */
  async muteMic(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      await this.room.localParticipant.setMicrophoneEnabled(false);
      this.callbacks.onMuted?.('mic', true);
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Unmute microphone */
  async unmuteMic(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      await this.room.localParticipant.setMicrophoneEnabled(true);
      this.callbacks.onMuted?.('mic', false);
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Mute webcam (keep track published, no video) */
  async muteWebcam(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      await this.room.localParticipant.setCameraEnabled(false);
      this.callbacks.onMuted?.('webcam', true);
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Unmute webcam */
  async unmuteWebcam(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      await this.room.localParticipant.setCameraEnabled(true);
      this.callbacks.onMuted?.('webcam', false);
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Publish screen share */
  async enableScreenShare(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      const existing = this.room.localParticipant.getTrackPublication(Track.Source.ScreenShare);
      if (existing && this.screenSharePublication) {
        return; // duplicate guard
      }
      this.screenSharePublication = await this.room.localParticipant.setScreenShareEnabled(true);
      this.callbacks.onTrackPublished?.('screenshare');
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Unpublish screen share */
  async disableScreenShare(): Promise<void> {
    if (!this.room) {
      return;
    }
    try {
      await this.room.localParticipant.setScreenShareEnabled(false);
      this.screenSharePublication = undefined;
      this.callbacks.onTrackUnpublished?.('screenshare');
    } catch (err) {
      this.callbacks.onError?.(err as Error);
      throw err;
    }
  }

  /** Disconnect from LiveKit and release all media */
  async disconnect(): Promise<void> {
    if (this.room) {
      await this.room.disconnect(true);
      this.room = null;
    }
    if (this.e2eeManager) {
      this.e2eeManager = null;
    }
    if (this.keyProvider) {
      this.keyProvider.dispose();
      this.keyProvider = null;
    }
    this.micPublication = undefined;
    this.webcamPublication = undefined;
    this.screenSharePublication = undefined;
  }
}
