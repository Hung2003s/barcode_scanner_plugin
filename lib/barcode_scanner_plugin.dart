import 'barcode_scanner_plugin_platform_interface.dart';

/// Lớp chính của plugin BarcodeScanner.
///
/// Cung cấp API đơn giản cho Flutter App gọi các chức năng quét mã vạch:
/// - Quét mã vạch bằng camera (CameraX + Google ML Kit).
/// - Quét mã vạch từ máy quét phần cứng PDA (Broadcast Intent).
class BarcodeScannerPlugin {
  /// Stream phát ra mã vạch quét được từ thiết bị PDA (máy quét phần cứng).
  ///
  /// Sử dụng [StreamBuilder] hoặc [listen()] để nhận dữ liệu real-time.
  /// ```dart
  /// _barcodeScanner.barcodeStream.listen((barcode) {
  ///   print('Scanned: $barcode');
  /// });
  /// ```
  Stream<String> get barcodeStream {
    return BarcodeScannerPluginPlatform.instance.barcodeStream;
  }

  /// Mở giao diện camera để quét mã vạch.
  ///
  /// Trả về mã vạch nếu quét thành công, hoặc `null` nếu người dùng hủy.
  /// Phù hợp với điện thoại thông minh không có máy quét phần cứng.
  ///
  /// ```dart
  /// final result = await _barcodeScanner.startCameraScan();
  /// if (result != null) {
  ///   print('Camera scanned: $result');
  /// }
  /// ```
  Future<String?> startCameraScan() {
    return BarcodeScannerPluginPlatform.instance.startCameraScan();
  }
}
