import Flutter
import UIKit

/// Plugin chính cho iOS của Barcode Scanner.
///
/// Tính năng quét camera trên iOS sẽ được phát triển trong phiên bản tiếp theo.
/// Hiện tại plugin hỗ trợ đầy đủ trên nền tảng Android.
public class BarcodeScannerPlugin: NSObject, FlutterPlugin {
  /// Đăng ký plugin với Flutter engine.
  /// - Parameter registrar: Đối tượng dùng để đăng ký MethodChannel.
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "barcode_scanner_plugin", binaryMessenger: registrar.messenger())
    let instance = BarcodeScannerPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  /// Xử lý các method gọi từ Flutter qua MethodChannel.
  /// - Parameters:
  ///   - call: Thông tin về method và tham số từ Flutter.
  ///   - result: Callback để trả kết quả về Flutter.
  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    default:
      // Method chưa được hỗ trợ trên iOS
      result(FlutterMethodNotImplemented)
    }
  }
}
