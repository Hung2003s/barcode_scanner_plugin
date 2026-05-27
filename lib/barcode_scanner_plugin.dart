import 'barcode_scanner_plugin_platform_interface.dart';

class BarcodeScannerPlugin {
  /// Gọi lệnh MethodChannel thông qua interface
  Future<String?> getPlatformVersion() {
    return BarcodeScannerPluginPlatform.instance.getPlatformVersion();
  }

  /// Cung cấp Stream để developer sử dụng với StreamBuilder hoặc listen()
  Stream<String> get barcodeStream {
    return BarcodeScannerPluginPlatform.instance.barcodeStream;
  }
}
