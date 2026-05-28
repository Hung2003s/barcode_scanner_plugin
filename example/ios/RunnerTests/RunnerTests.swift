import Flutter
import UIKit
import XCTest


@testable import barcode_scanner_plugin

// This demonstrates a simple unit test of the Swift portion of this plugin's implementation.
//
// See https://developer.apple.com/documentation/xctest for more information about using XCTest.

class RunnerTests: XCTestCase {

  func testNotImplementedMethods() {
    let plugin = BarcodeScannerPlugin()

    let call = FlutterMethodCall(methodName: "startCameraScan", arguments: [])

    let resultExpectation = expectation(description: "result block must be called.")
    plugin.handle(call) { result in
      XCTAssertTrue(result is FlutterError)
      resultExpectation.fulfill()
    }
    waitForExpectations(timeout: 1)
  }

}
