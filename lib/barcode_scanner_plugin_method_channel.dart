import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'barcode_scanner_plugin_platform_interface.dart';

/// Lớp triển khai giao tiếp thực tế với Native bằng MethodChannel và EventChannel.
class MethodChannelBarcodeScannerPlugin extends BarcodeScannerPluginPlatform {
  /// Channel dùng để gửi lệnh (Call/Response)
  @visibleForTesting
  final methodChannel = const MethodChannel('barcode_scanner_plugin');

  /// Channel dùng để lắng nghe luồng dữ liệu (Stream)
  @visibleForTesting
  final eventChannel = const EventChannel('barcode_scanner_plugin/events');

  @override
  Stream<String> get barcodeStream {
    // Tạo mới BroadcastStream mỗi khi getter được gọi.
    // EventChannel đảm bảo chỉ có một subscription thực tế đến Native,
    // giúp tránh lỗi "Stream already subscribed"
    // khi widget rebuild hoặc multiple listeners đăng ký.
    return eventChannel.receiveBroadcastStream().cast<String>();
  }

  @override
  Future<String?> startCameraScan() async {
    // Gửi lệnh 'startCameraScan' xuống Kotlin và chờ kết quả trả về
    final barcode = await methodChannel.invokeMethod<String>('startCameraScan');
    return barcode;
  }
}
