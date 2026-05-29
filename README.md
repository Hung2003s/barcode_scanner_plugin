# barcode_scanner_plugin

A powerful Flutter plugin for barcode scanning, supporting both **dedicated PDA hardware scanners** (via Android Broadcast Intents) and **smartphone cameras** (via CameraX + Google ML Kit).

**Version 0.2.0** features a completely redesigned camera scanning UI with transparent frame, touch zoom wheel, flash toggle, and performance optimizations.

## Features

*   **Dual-Mode Scanning**: 
    *   **Hardware PDA**: Real-time stream from integrated scanners (Zebra, Honeywell, Sunmi, etc.) using `EventChannel`.
    *   **Camera Scan**: High-performance camera scanning using **CameraX** and **Google ML Kit** for regular smartphones.
*   **Redesigned Camera UI (v0.2.0)**:
    *   **Transparent scan frame**: Camera preview shows through the frame area (uses `clipOutRect` for hardware acceleration).
    *   **Blue semi-transparent overlay** (#2196F3) with gradient blur/feather edges around the frame.
    *   **Touch zoom wheel**: Drag vertically on the right side to zoom 1x-10x, with green indicator and +/- buttons.
    *   **Flash toggle**: Lightning bolt icon (yellow when on, white when off) with scale animation.
    *   **Animated scan line**: Green gradient line bouncing within the frame.
*   **Performance Optimized**:
    *   Zero allocations in `onDraw()` - all Paint objects pre-allocated.
    *   `postInvalidateOnAnimation()` for vsync-aligned rendering.
    *   Frame analysis throttled to 150ms to reduce CPU/battery usage.
*   **Stream-based API**: Simple `barcodeStream` for background hardware scanning.
*   **Easy Integration**: Optimized for Android handheld terminals and mobile devices.

## Installation

Add `barcode_scanner_plugin` to your `pubspec.yaml` file:

```yaml
dependencies:
  barcode_scanner_plugin:
    git:
      url: https://github.com/Hung2003s/barcode_scanner_plugin.git
```

Or from [pub.dev](https://pub.dev/packages/barcode_scanner_plugin):

```yaml
dependencies:
  barcode_scanner_plugin: ^0.2.0
```

## Usage

### 1. Import the package

```dart
import 'package:barcode_scanner_plugin/barcode_scanner_plugin.dart';
```

### 2. Initialize the plugin

```dart
final _barcodeScanner = BarcodeScannerPlugin();
```

### 3. Hardware PDA Scanning (Background Stream)

Ideal for devices with a physical scan button. It listens for Broadcast Intents in the background.

```dart
StreamBuilder<String>(
  stream: _barcodeScanner.barcodeStream,
  builder: (context, snapshot) {
    if (snapshot.hasData) {
      return Text('PDA Scanned: ${snapshot.data}');
    }
    return Text('Waiting for hardware scan...');
  },
)
```

### 4. Camera Scanning (Interactive)

Use this for devices without a hardware scanner or as a fallback. It opens a fullscreen camera with:
- **Transparent scan frame** in the center
- **Blue blur overlay** around the frame
- **Touch zoom wheel** on the right (1x-10x)
- **Flash toggle** button

```dart
final result = await _barcodeScanner.startCameraScan();
if (result != null) {
  print('Camera Scanned: $result');
}
```

> **Note**: The camera scanning feature is currently available on **Android only**. iOS support is planned for a future release.

## Android Configuration

### Permissions
The plugin requires Camera permission for the camera scanning feature. Add this to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

### Hardware Scanner Setup
Ensure your PDA device is configured to send **Broadcast Intents**. Common settings:
*   **Action**: `com.barcode.action`
*   **Data Key**: `data_string`

## Example

```dart
import 'package:flutter/material.dart';
import 'package:barcode_scanner_plugin/barcode_scanner_plugin.dart';

void main() => runApp(const MyApp());

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _plugin = BarcodeScannerPlugin();
  String _camResult = 'None';

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Barcode Scanner')),
        body: Column(
          children: [
            // Hardware Scanner Listener
            StreamBuilder<String>(
              stream: _plugin.barcodeStream,
              builder: (context, snapshot) =>
                  Text('PDA: ${snapshot.data ?? "Waiting..."}'),
            ),
            const Divider(),
            // Camera Scanner Button
            Text('Camera: $_camResult'),
            ElevatedButton(
              onPressed: () async {
                final code = await _plugin.startCameraScan();
                if (code != null) setState(() => _camResult = code);
              },
              child: const Text('Open Camera Scanner'),
            ),
          ],
        ),
      ),
    );
  }
}
```

## Camera UI Preview

When you call `startCameraScan()`, the fullscreen camera scanner includes:

| Element | Description |
|---------|-------------|
| 🔦 **Flash** | Lightning bolt icon, top-left corner |
| ❌ **Close** | X icon, top-right corner |
| 📷 **Scan Frame** | Transparent center area with white border |
| 🟢 **Corners** | Green L-shaped markers at frame corners |
| 🔵 **Overlay** | Blue (#2196F3) semi-transparent surround |
| 🌫️ **Blur Edge** | Gradient feather transition at frame boundary |
| 🔄 **Scan Line** | Animated green gradient line |
| 🔍 **Zoom Wheel** | Right side touch track with +/- buttons |
| 🔤 **Zoom Text** | Current zoom level display (1.0x - 10.0x) |

## Platform Support

| Platform | Support |
|----------|---------|
| Android  | ✅ Full support (PDA + Camera with redesigned UI) |
| iOS      | ⚠️ PDA stream only (Camera coming soon) |

## License

This project is licensed under the MIT License - see the LICENSE file for details.