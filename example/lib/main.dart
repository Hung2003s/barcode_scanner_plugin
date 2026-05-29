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
  bool _isScanning = false;

  Future<void> _startCameraScan() async {
    setState(() => _isScanning = true);

    try {
      final result = await _barcodeScannerPlugin.startCameraScan();
      if (!mounted) return;

      setState(() {
        _isScanning = false;
        if (result != null && result.isNotEmpty) {
          _cameraScanResult = result;
        } else {
          _cameraScanResult = 'Người dùng hủy quét';
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isScanning = false;
        _cameraScanResult = 'Lỗi: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: Colors.blue,
        useMaterial3: true,
        brightness: Brightness.light,
      ),
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Quét Mã Vạch'),
          centerTitle: true,
          backgroundColor: const Color(0xFF1565C0),
          foregroundColor: Colors.white,
          elevation: 2,
        ),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // ===== Header =====
              const Icon(
                Icons.qr_code_scanner,
                size: 64,
                color: Color(0xFF1565C0),
              ),
              const SizedBox(height: 8),
              const Text(
                'Quét mã vạch bằng Camera',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 4),
              Text(
                'CameraX + ML Kit Barcode Scanning',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
              ),

              const SizedBox(height: 24),

              // ===== Kết quả quét =====
              Card(
                elevation: 2,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    vertical: 24,
                    horizontal: 16,
                  ),
                  child: Column(
                    children: [
                      const Text(
                        'KẾT QUẢ QUÉT',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: Colors.grey,
                          letterSpacing: 1.2,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: _getResultColor().withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: _getResultColor().withValues(alpha: 0.3),
                          ),
                        ),
                        child: Text(
                          _cameraScanResult,
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                            color: _getResultColor(),
                            fontFamily: 'monospace',
                            letterSpacing: 1.5,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 20),

              // ===== Nút quét =====
              SizedBox(
                height: 56,
                child: ElevatedButton.icon(
                  onPressed: _isScanning ? null : _startCameraScan,
                  icon: _isScanning
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.5,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.camera_alt_rounded, size: 26),
                  label: Text(
                    _isScanning ? 'ĐANG MỞ CAMERA...' : 'MỞ CAMERA QUÉT MÃ',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      letterSpacing: 0.5,
                    ),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF1565C0),
                    foregroundColor: Colors.white,
                    disabledBackgroundColor: Colors.grey.shade400,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 3,
                  ),
                ),
              ),

              const SizedBox(height: 28),

              // ===== Hướng dẫn =====
              const Text(
                'HƯỚNG DẪN SỬ DỤNG',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Colors.grey,
                  letterSpacing: 1.2,
                ),
              ),
              const SizedBox(height: 12),

              _buildGuideItem(
                icon: Icons.center_focus_strong,
                title: 'Đặt mã vạch vào khung',
                subtitle: 'Đưa mã vạch vào khung quét ở chính giữa màn hình',
              ),
              _buildGuideItem(
                icon: Icons.add_rounded,
                title: 'Zoom bằng nút cuộn bên phải',
                subtitle:
                    'Kéo thanh trượt dọc hoặc nhấn +/- để phóng to/thu nhỏ',
              ),
              _buildGuideItem(
                icon: Icons.flashlight_on_rounded,
                title: 'Bật/tắt đèn flash',
                subtitle: 'Nhấn biểu tượng tia sét (⚡) ở góc trên bên trái',
              ),
              _buildGuideItem(
                icon: Icons.close_rounded,
                title: 'Thoát',
                subtitle: 'Nhấn nút X ở góc trên bên phải để hủy',
              ),

              const SizedBox(height: 20),

              // ===== PDA Section =====
              Card(
                elevation: 1,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                clipBehavior: Clip.antiAlias,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            Icons.vibration,
                            color: Colors.orange.shade700,
                            size: 22,
                          ),
                          const SizedBox(width: 8),
                          const Text(
                            'Máy quét PDA (phần cứng)',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w600,
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
                              width: double.infinity,
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: Colors.orange.withValues(alpha: 0.1),
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(
                                  color: Colors.orange.withValues(alpha: 0.3),
                                ),
                              ),
                              child: Text(
                                '📦 ${snapshot.data}',
                                style: const TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  color: Color(0xFFE65100),
                                ),
                                textAlign: TextAlign.center,
                              ),
                            );
                          }
                          return Text(
                            'Đang chờ quét từ máy PDA...',
                            style: TextStyle(
                              color: Colors.grey.shade500,
                              fontStyle: FontStyle.italic,
                              fontSize: 14,
                            ),
                          );
                        },
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

  Color _getResultColor() {
    if (_cameraScanResult == 'Chưa quét mã nào') {
      return Colors.grey;
    } else if (_cameraScanResult == 'Người dùng hủy quét' ||
        _cameraScanResult.startsWith('Lỗi')) {
      return Colors.red;
    }
    return const Color(0xFF1565C0);
  }

  Widget _buildGuideItem({
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: const Color(0xFF1565C0).withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, size: 20, color: const Color(0xFF1565C0)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                  ),
                ),
                Text(
                  subtitle,
                  style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
