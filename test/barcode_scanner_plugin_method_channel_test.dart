import 'package:flutter_test/flutter_test.dart';
import 'package:barcode_scanner_plugin/barcode_scanner_plugin_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('MethodChannelBarcodeScannerPlugin can be instantiated', () {
    final platform = MethodChannelBarcodeScannerPlugin();
    expect(platform, isNotNull);
  });
}
