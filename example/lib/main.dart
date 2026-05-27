import 'package:flutter/material.dart';
import 'package:barcode_scanner_plugin/barcode_scanner_plugin.dart';

void main() => runApp(const MyApp());

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _plugin = BarcodeScannerPlugin();
  String _platformVersion = 'Unknown';

  @override
  void initState() {
    super.initState();
    // Test MethodChannel
    _plugin.getPlatformVersion().then((version) {
      if (mounted) setState(() => _platformVersion = version ?? 'Unknown');
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: Text('Plugin Version: $_platformVersion')),
        body: Center(
          // Test EventChannel qua StreamBuilder
          child: StreamBuilder<String>(
            stream: _plugin.barcodeStream,
            builder: (context, snapshot) {
              if (snapshot.hasData) {
                return Text(
                  'Mã vạch quét được:\n${snapshot.data}',
                  style: const TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
                  textAlign: TextAlign.center,
                );
              }
              return const Text('Hãy bấm nút trên máy quét để quét mã...');
            },
          ),
        ),
      ),
    );
  }
}
