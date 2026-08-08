import React, {useState} from 'react';
import {StatusBar, useColorScheme} from 'react-native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import JoinScreen, {type HybridConfig} from './src/screens/JoinScreen';
import ConferenceScreen from './src/screens/ConferenceScreen';

function App(): React.JSX.Element {
  const isDarkMode = useColorScheme() === 'dark';
  const [config, setConfig] = useState<HybridConfig | null>(null);

  const handleJoin = (newConfig: HybridConfig) => {
    setConfig(newConfig);
  };

  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      {config ? (
        <ConferenceScreen config={config} onSessionEnded={() => setConfig(null)} />
      ) : (
        <JoinScreen onJoin={handleJoin} />
      )}
    </SafeAreaProvider>
  );
}

export default App;
