import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'barcode_scanner_plugin_method_channel.dart';

abstract class BarcodeScannerPluginPlatform extends PlatformInterface {
  /// Khởi tạo token để xác thực instance
  BarcodeScannerPluginPlatform() : super(token: _token);
  static final Object _token = Object();

  /// Mặc định sẽ sử dụng MethodChannel implementation
  static BarcodeScannerPluginPlatform _instance =
      MethodChannelBarcodeScannerPlugin();

  /// Getter để truy cập vào instance hiện tại
  static BarcodeScannerPluginPlatform get instance => _instance;

  /// Setter để cho phép ghi đè instance (dùng khi viết Mock/Test)
  static set instance(BarcodeScannerPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Hàm ví dụ sử dụng MethodChannel (vd: Lấy phiên bản Android)
  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  /// Luồng sự kiện sử dụng EventChannel để hứng dữ liệu quét
  Stream<String> get barcodeStream {
    throw UnimplementedError('barcodeStream has not been implemented.');
  }

  /// Hàm gọi camera
  Future<String?> startCameraScan() {
    throw UnimplementedError('startCameraScan() has not been implemented.');
  }
}
