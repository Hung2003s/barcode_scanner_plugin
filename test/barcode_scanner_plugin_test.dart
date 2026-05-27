import 'package:flutter_test/flutter_test.dart';
import 'package:barcode_scanner_plugin/barcode_scanner_plugin.dart';
import 'package:barcode_scanner_plugin/barcode_scanner_plugin_platform_interface.dart';
import 'package:barcode_scanner_plugin/barcode_scanner_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockBarcodeScannerPluginPlatform
    with MockPlatformInterfaceMixin
    implements BarcodeScannerPluginPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');

  @override
  // TODO: implement barcodeStream
  Stream<String> get barcodeStream => throw UnimplementedError();

  @override
  Future<String?> startCameraScan() {
    // TODO: implement startCameraScan
    throw UnimplementedError();
  }
}

void main() {
  final BarcodeScannerPluginPlatform initialPlatform =
      BarcodeScannerPluginPlatform.instance;

  test('$MethodChannelBarcodeScannerPlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelBarcodeScannerPlugin>());
  });

  test('getPlatformVersion', () async {
    BarcodeScannerPlugin barcodeScannerPlugin = BarcodeScannerPlugin();
    MockBarcodeScannerPluginPlatform fakePlatform =
        MockBarcodeScannerPluginPlatform();
    BarcodeScannerPluginPlatform.instance = fakePlatform;

    expect(await barcodeScannerPlugin.getPlatformVersion(), '42');
  });
}
