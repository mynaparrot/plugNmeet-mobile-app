import 'dart:math';
import 'package:flutter/material.dart';
import '../services/plugnmeet_api.dart';
import 'conference_screen.dart';

class JoinScreen extends StatefulWidget {
  const JoinScreen({super.key});

  @override
  State<JoinScreen> createState() => _JoinScreenState();
}

class _JoinScreenState extends State<JoinScreen> {
  final _serverCtrl = TextEditingController(text: 'https://demo.plugnmeet.com');
  final _apiKeyCtrl = TextEditingController(text: 'plugnmeet');
  final _apiSecretCtrl = TextEditingController(text: 'zumyyYWqv7KR2kUqvYdq4z4sXg7XTBD2ljT6');
  final _roomCtrl = TextEditingController(text: 'room01');
  final _nameCtrl = TextEditingController(text: 'user-${Random().nextInt(100)}');
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _serverCtrl.dispose();
    _apiKeyCtrl.dispose();
    _apiSecretCtrl.dispose();
    _roomCtrl.dispose();
    _nameCtrl.dispose();
    super.dispose();
  }

  Future<void> _join() async {
    final server = _serverCtrl.text.trim();
    final key = _apiKeyCtrl.text.trim();
    final secret = _apiSecretCtrl.text.trim();
    final room = _roomCtrl.text.trim();
    final name = _nameCtrl.text.trim();

    if (room.isEmpty || name.isEmpty) {
      setState(() => _error = 'Room ID and Name are required');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final activeRes = await isRoomActive(server, key, secret, room);
      if (activeRes['status'] == true && activeRes['is_active'] != true) {
        final createRes = await createRoom(server, key, secret, room, 'Demo room');
        if (createRes['status'] != true) {
          throw Exception(createRes['msg'] ?? 'Failed to create room');
        }
      } else if (activeRes['status'] != true) {
        throw Exception(activeRes['msg'] ?? 'Failed to check room status');
      }

      final userId = DateTime.now().millisecondsSinceEpoch.toString();
      final joinResult = await getJoinToken(server, key, secret, room, name, userId, true);

      final token = joinResult['token'] as String?;
      if (token == null) throw Exception('No token received');

      if (!mounted) return;

      Navigator.push(context, MaterialPageRoute(
        builder: (_) => ConferenceScreen(
          serverUrl: server,
          jwt: token,
        ),
      ));
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('plugNmeet Flutter Demo')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.amber.shade100,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.amber.shade400),
              ),
              child: const Text(
                'DEMO ONLY \u2014 Never embed API keys in production apps.',
                style: TextStyle(color: Color(0xFF664D03), fontSize: 13, fontWeight: FontWeight.w600),
                textAlign: TextAlign.center,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _serverCtrl,
              decoration: const InputDecoration(labelText: 'Server URL'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _apiKeyCtrl,
              decoration: const InputDecoration(labelText: 'API Key'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _apiSecretCtrl,
              decoration: const InputDecoration(labelText: 'API Secret'),
              obscureText: true,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _roomCtrl,
              decoration: const InputDecoration(labelText: 'Room ID'),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _nameCtrl,
              decoration: const InputDecoration(labelText: 'Your Name'),
            ),
            const SizedBox(height: 16),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(_error!, style: const TextStyle(color: Colors.red)),
              ),
            FilledButton(
              onPressed: _loading ? null : _join,
              child: _loading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Join'),
            ),
          ],
        ),
      ),
    );
  }
}
