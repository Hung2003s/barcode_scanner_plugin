## 0.1.3

* **Android 13+ Compatibility**: Added `RECEIVER_EXPORTED` flag when registering BroadcastReceiver on Android 13 (API 33) and above to comply with new runtime broadcast receiver restrictions.
* `pubspec.yaml` version bump.

## 0.1.2

* **Improvement**: Removed cached stream variable in `barcodeStream` to avoid "Stream already subscribed" errors.
* **Removed `getPlatformVersion()`**: Xóa method lấy phiên bản OS không cần thiết khỏi toàn bộ codebase (Dart, Kotlin, Swift, tests) để giữ API plugin tập trung vào quét mã vạch.
* **Documentation**: Added comprehensive comments and KDoc documentation for all Dart, Kotlin, and Swift source files.
* Updated `pubspec.yaml` repository URL and version.

## 0.1.1

* **New Feature**: Added Camera Scanning support using CameraX and Google ML Kit.
* Support for regular smartphones without dedicated hardware scanners.
* Dual-mode scanning: Detects hardware PDA scan intents or uses the camera.
* Improved documentation and usage examples in README.

## 0.1.0

* **New Feature**: Added Camera Scanning support using CameraX and Google ML Kit.
* Support for regular smartphones without dedicated hardware scanners.
* Dual-mode scanning: Detects hardware PDA scan intents or uses the camera.
* Improved documentation and usage examples in README.

## 0.0.1

* Initial release.
* Support hardware barcode scanning via Android Broadcast Intent and EventChannel.
* Added Federated Plugin Architecture layout.
