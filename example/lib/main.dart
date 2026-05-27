import 'package:flutter/material.dart';
import 'package:barcode_scanner_plugin/barcode_scanner_plugin.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _barcodeScannerPlugin = BarcodeScannerPlugin();
  String _cameraScanResult = 'Chưa quét mã nào';

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Bộ Quét Mã Vạch Đa Năng'),
          backgroundColor: Colors.blueAccent,
          foregroundColor: Colors.white,
        ),
        body: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // ================= PHẦN 1: MÁY QUÉT CỨNG PDA (EVENT CHANNEL) =================
              Card(
                elevation: 4,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      const Row(
                        children: [
                          Icon(Icons.vibration, color: Colors.orange),
                          SizedBox(width: 8),
                          Text(
                            '1. Máy quét phần cứng (PDA)',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      StreamBuilder<String>(
                        stream: _barcodeScannerPlugin.barcodeStream,
                        builder: (context, snapshot) {
                          if (snapshot.hasData) {
                            return Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: Colors.green.withValues(alpha: 0.1),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                'Mã PDA: ${snapshot.data}',
                                style: const TextStyle(
                                  fontSize: 20,
                                  color: Colors.green,
                                  fontWeight: FontWeight.bold,
                                ),
                                textAlign: TextAlign.center,
                              ),
                            );
                          }
                          if (snapshot.hasError) {
                            return Text(
                              'Lỗi: ${snapshot.error}',
                              style: const TextStyle(color: Colors.red),
                            );
                          }
                          return const Text(
                            'Đang chờ bạn bấm nút quét cứng trên máy...',
                            style: TextStyle(
                              color: Colors.grey,
                              fontStyle: FontStyle.italic,
                            ),
                          );
                        },
                      ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 20),

              Card(
                elevation: 4,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      const Row(
                        children: [
                          Icon(Icons.camera_alt, color: Colors.blue),
                          SizedBox(width: 8),
                          Text(
                            '2. Máy quét bằng Camera phone',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      Text(
                        'Kết quả Cam: $_cameraScanResult',
                        style: TextStyle(
                          fontSize: 20,
                          color:
                              _cameraScanResult == 'Chưa quét mã nào' ||
                                  _cameraScanResult == 'Người dùng hủy quét'
                              ? Colors.grey
                              : Colors.blue,
                          fontWeight: FontWeight.bold,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton.icon(
                        onPressed: () async {
                          // Gọi hàm mở Activity quét bằng cameraX + ML Kit từ Native Kotlin
                          final result = await _barcodeScannerPlugin
                              .startCameraScan();

                          setState(() {
                            if (result != null && result.isNotEmpty) {
                              _cameraScanResult = result;
                            } else {
                              _cameraScanResult = 'Người dùng hủy quét';
                            }
                          });
                        },
                        icon: const Icon(Icons.photo_camera),
                        label: const Text('BẬT CAMERA QUÉT MÃ'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.blueAccent,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(
                            vertical: 12,
                            horizontal: 24,
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
