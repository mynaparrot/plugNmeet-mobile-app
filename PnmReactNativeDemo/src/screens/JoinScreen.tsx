import React, {useState} from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Alert,
  ActivityIndicator,
  Switch,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';

import { isRoomActive, createRoom, getJoinToken } from '../services/plugNmeetApi';

const ROOM_OPTIONS = [
  'room01', 'room02', 'room03', 'room04', 'room05',
  'room06', 'room07', 'room08', 'room09', 'room10',
  'room11', 'room12', 'room13', 'room14', 'room15',
];

export interface HybridConfig {
  serverUrl: string;
  jwt: string;
}

interface JoinScreenProps {
  onJoin: (config: HybridConfig) => void;
}

const JoinScreen: React.FC<JoinScreenProps> = ({onJoin}) => {
  const [serverUrl, setServerUrl] = useState('https://demo.plugnmeet.com');
  const [apiKey, setApiKey] = useState('plugnmeet');
  const [apiSecret, setApiSecret] = useState('zumyyYWqv7KR2kUqvYdq4z4sXg7XTBD2ljT6');
  const [roomId, setRoomId] = useState(ROOM_OPTIONS[0]);
  const [customRoomId, setCustomRoomId] = useState('');
  const [useCustomRoom, setUseCustomRoom] = useState(false);
  const [userName, setUserName] = useState(
    'user-' + Math.floor(Math.random() * 100),
  );
  const [userType, setUserType] = useState<'admin' | 'participant'>(
    'admin',
  );
  const [loading, setLoading] = useState(false);

  const effectiveRoomId = useCustomRoom ? customRoomId : roomId;

  const isFormValid =
    serverUrl.trim() !== '' &&
    apiKey.trim() !== '' &&
    apiSecret.trim() !== '' &&
    effectiveRoomId.trim() !== '' &&
    userName.trim() !== '';

  const handleJoin = async () => {
    if (!isFormValid) {
      return;
    }

    const url = serverUrl.trim();
    const key = apiKey.trim();
    const secret = apiSecret.trim();
    const room = effectiveRoomId.trim();
    const name = userName.trim();
    const userId = String(Date.now());
    const isAdmin = userType === 'admin';

    setLoading(true);
    try {
      // 1. Check if room is active
      const activeRes = await isRoomActive(url, key, secret, room);
      if (activeRes.status && !activeRes.is_active) {
        // 2. Create room if not active
        const createRes = await createRoom(url, key, secret, room, 'Demo room');
        if (!createRes.status) {
          Alert.alert('Error', createRes.msg || 'Failed to create room');
          return;
        }
      } else if (!activeRes.status) {
        Alert.alert('Error', activeRes.msg || 'Failed to check room status');
        return;
      }

      // 3. Get join token with HYBRID_WEB client type
      const tokenRes = await getJoinToken(
        url,
        key,
        secret,
        room,
        name,
        userId,
        isAdmin,
      );
      if (!tokenRes.status || !tokenRes.token) {
        Alert.alert('Error', tokenRes.msg || 'Failed to get join token');
        return;
      }

      onJoin({serverUrl: url, jwt: tokenRes.token});
    } catch (err: any) {
      Alert.alert('Error', err.message || 'Something went wrong');
    } finally {
      setLoading(false);
    }
  };

  const selectRoom = (id: string) => {
    setRoomId(id);
    setUseCustomRoom(false);
  };

  const randomRoom = () => {
    const id = 'room-' + Math.random().toString(36).substring(7);
    setCustomRoomId(id);
    setUseCustomRoom(true);
  };

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.innerContainer}>
        <ScrollView contentContainerStyle={styles.scrollContainer}>
          {/* Demo warning banner */}
          <View style={styles.warningBanner}>
            <Text style={styles.warningText}>
              ⚠️ DEMO ONLY — Never embed API keys in production apps.
            </Text>
          </View>

          <Text style={styles.title}>PlugNMeet Demo</Text>
          <Text style={styles.subtitle}>Hybrid Native Publisher</Text>

          {/* Server URL */}
          <Text style={styles.label}>Server URL</Text>
          <TextInput
            style={styles.input}
            placeholder="https://your-plugnmeet-server.com"
            value={serverUrl}
            onChangeText={setServerUrl}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
          />

          {/* API Key */}
          <Text style={styles.label}>API Key</Text>
          <TextInput
            style={styles.input}
            placeholder="API Key"
            value={apiKey}
            onChangeText={setApiKey}
            autoCapitalize="none"
            autoCorrect={false}
          />

          {/* API Secret */}
          <Text style={styles.label}>API Secret</Text>
          <TextInput
            style={styles.input}
            placeholder="API Secret"
            value={apiSecret}
            onChangeText={setApiSecret}
            secureTextEntry
            autoCapitalize="none"
            autoCorrect={false}
          />

          {/* Room selection */}
          <Text style={styles.label}>Room</Text>
          {!useCustomRoom ? (
            <View style={styles.roomRow}>
              <View style={styles.roomSelectWrapper}>
                {ROOM_OPTIONS.map(id => (
                  <TouchableOpacity
                    key={id}
                    style={[
                      styles.roomChip,
                      roomId === id && styles.roomChipActive,
                    ]}
                    onPress={() => selectRoom(id)}>
                    <Text
                      style={[
                        styles.roomChipText,
                        roomId === id && styles.roomChipTextActive,
                      ]}>
                      {id}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
              <TouchableOpacity
                style={styles.linkButton}
                onPress={() => {
                  setUseCustomRoom(true);
                  setCustomRoomId(
                    'room-' + Math.random().toString(36).substring(7),
                  );
                }}>
                <Text style={styles.linkButtonText}>Custom</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <View style={styles.roomRow}>
              <TextInput
                style={[styles.input, styles.customRoomInput]}
                value={customRoomId}
                onChangeText={setCustomRoomId}
                autoCapitalize="none"
                autoCorrect={false}
              />
              <TouchableOpacity style={styles.linkButton} onPress={randomRoom}>
                <Text style={styles.linkButtonText}>Random</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.linkButton}
                onPress={() => setUseCustomRoom(false)}>
                <Text style={styles.linkButtonText}>List</Text>
              </TouchableOpacity>
            </View>
          )}

          {/* User Name */}
          <Text style={styles.label}>Name</Text>
          <TextInput
            style={styles.input}
            placeholder="Your name"
            value={userName}
            onChangeText={setUserName}
            autoCapitalize="words"
          />

          {/* User Type */}
          <View style={styles.switchContainer}>
            <Text style={styles.label}>
              User Type: {userType === 'admin' ? 'Admin' : 'Participant'}
            </Text>
            <Switch
              trackColor={{false: '#E8E8E8', true: '#007AFF'}}
              thumbColor={'#FFF'}
              onValueChange={checked =>
                setUserType(checked ? 'admin' : 'participant')
              }
              value={userType === 'admin'}
            />
          </View>

          {/* Join button */}
          <TouchableOpacity
            style={[styles.button, !isFormValid && styles.buttonDisabled]}
            onPress={handleJoin}
            disabled={!isFormValid || loading}>
            {loading ? (
              <ActivityIndicator color="#FFF" />
            ) : (
              <Text style={styles.buttonText}>Join Meeting</Text>
            )}
          </TouchableOpacity>

          <Text style={styles.footerText}>
            PlugNmeet
          </Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  innerContainer: {
    flex: 1,
  },
  scrollContainer: {
    flexGrow: 1,
    paddingHorizontal: 20,
    paddingVertical: 20,
  },
  warningBanner: {
    backgroundColor: '#FFF3CD',
    borderColor: '#FFCA2C',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 20,
  },
  warningText: {
    color: '#664D03',
    fontSize: 13,
    fontWeight: '600',
    textAlign: 'center',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    textAlign: 'center',
    color: '#333',
  },
  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 24,
    color: '#666',
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 6,
    marginTop: 4,
  },
  input: {
    backgroundColor: '#FFF',
    borderRadius: 8,
    paddingHorizontal: 15,
    paddingVertical: 12,
    fontSize: 16,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#DDD',
  },
  customRoomInput: {
    flex: 1,
    marginBottom: 0,
  },
  roomRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 14,
    gap: 8,
  },
  roomSelectWrapper: {
    flex: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  roomChip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#E8E8E8',
    borderWidth: 1,
    borderColor: '#DDD',
  },
  roomChipActive: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  roomChipText: {
    fontSize: 13,
    color: '#333',
  },
  roomChipTextActive: {
    color: '#FFF',
  },
  linkButton: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: '#666',
    borderRadius: 6,
  },
  linkButtonText: {
    color: '#FFF',
    fontSize: 14,
    fontWeight: '600',
  },
  button: {
    backgroundColor: '#007AFF',
    borderRadius: 8,
    paddingVertical: 15,
    alignItems: 'center',
    marginTop: 10,
  },
  buttonDisabled: {
    backgroundColor: '#A0CFFF',
  },
  buttonText: {
    color: '#FFF',
    fontSize: 18,
    fontWeight: '600',
  },
  footerText: {
    textAlign: 'center',
    color: '#999',
    fontSize: 12,
    marginTop: 16,
  },
  switchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 10,
    marginBottom: 10,
  },
});

export default JoinScreen;