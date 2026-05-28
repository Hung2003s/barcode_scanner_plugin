import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'barcode_scanner_plugin_method_channel.dart';

abstract class BarcodeScannerPluginPlatform extends PlatformInterface {
  /// Token dùng để xác thực instance khi gọi setter, đảm bảo chỉ có
  /// instance hợp lệ mới có thể ghi đè (phục vụ Mock cho Unit Test).
  BarcodeScannerPluginPlatform() : super(token: _token);
  static final Object _token = Object();

  /// Instance mặc định sử dụng MethodChannel để giao tiếp với Native.
  /// Có thể ghi đè bằng MockPlatform trong môi trường test.
  static BarcodeScannerPluginPlatform _instance =
      MethodChannelBarcodeScannerPlugin();

  /// Getter để truy cập instance hiện tại (Singleton pattern).
  static BarcodeScannerPluginPlatform get instance => _instance;

  /// Setter cho phép ghi đè instance (dùng khi viết Mock/Test).
  /// Tự động xác thực token để ngăn gán instance không hợp lệ.
  static set instance(BarcodeScannerPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Stream phát ra mã vạch khi máy quét phần cứng (PDA) quét thành công.
  /// Sử dụng EventChannel để lắng nghe Broadcast Intents từ hệ thống.
  /// Dùng với StreamBuilder hoặc listen() để nhận dữ liệu real-time.
  Stream<String> get barcodeStream {
    throw UnimplementedError('barcodeStream has not been implemented.');
  }

  /// Mở màn hình camera và quét mã vạch sử dụng CameraX + Google ML Kit.
  /// Trả về mã vạch nếu quét thành công, hoặc null nếu người dùng hủy.
  /// Phù hợp với điện thoại thông minh không có máy quét phần cứng chuyên dụng.
  Future<String?> startCameraScan() {
    throw UnimplementedError('startCameraScan() has not been implemented.');
  }
}
