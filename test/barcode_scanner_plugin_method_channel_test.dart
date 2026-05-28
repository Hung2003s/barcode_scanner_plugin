import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:barcode_scanner_plugin/barcode_scanner_plugin_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelBarcodeScannerPlugin platform =
      MethodChannelBarcodeScannerPlugin();

  // Test có thể mở rộng ở đây để kiểm tra startCameraScan và barcodeStream
}
