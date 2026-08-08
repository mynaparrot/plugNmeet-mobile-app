import 'package:flutter/material.dart';
import 'screens/join_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const PnmFlutterDemoApp());
}

class PnmFlutterDemoApp extends StatelessWidget {
  const PnmFlutterDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PnmFlutterDemo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: Colors.indigo,
        useMaterial3: true,
        brightness: Brightness.dark,
      ),
      home: const JoinScreen(),
    );
  }
}
