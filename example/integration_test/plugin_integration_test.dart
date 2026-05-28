// This is a basic Flutter integration test.
//
// Since integration tests run in a full Flutter application, they can interact
// with the host side of a plugin implementation, unlike Dart unit tests.
//
// For more information about Flutter integration tests, please see
// https://flutter.dev/to/integration-testing

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:barcode_scanner_plugin/barcode_scanner_plugin.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  // Integration test có thể mở rộng để kiểm tra camera scan và hardware scan
  testWidgets('Plugin khởi tạo thành công', (WidgetTester tester) async {
    final BarcodeScannerPlugin plugin = BarcodeScannerPlugin();
    expect(plugin, isNotNull);
  });
}
