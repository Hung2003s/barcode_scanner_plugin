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
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  // Biến lưu trữ stream để tránh tạo mới liên tục mỗi khi gọi
  Stream<String>? _barcodeStream;

  @override
  Stream<String> get barcodeStream {
    // Chuyển đổi dữ liệu từ Native sang chuỗi String
    _barcodeStream ??= eventChannel.receiveBroadcastStream().cast<String>();
    return _barcodeStream!;
  }

  @override
  Future<String?> startCameraScan() async {
    // Gửi lệnh 'startCameraScan' xuống Kotlin và chờ kết quả trả về
    final barcode = await methodChannel.invokeMethod<String>('startCameraScan');
    return barcode;
  }
}
