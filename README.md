# barcode_scanner_plugin

A powerful Flutter plugin for barcode scanning, supporting both **dedicated PDA hardware scanners** (via Android Broadcast Intents) and **smartphone cameras** (via CameraX + Google ML Kit).

## Features

*   **Dual-Mode Scanning**: 
    *   **Hardware PDA**: Real-time stream from integrated scanners (Zebra, Honeywell, Sunmi, etc.) using `EventChannel`.
    *   **Camera Scan**: High-performance camera scanning using **CameraX** and **Google ML Kit** for regular smartphones.
*   **Camera Frame & Zoom**: Fullscreen camera preview with interactive zoom controls:
    *   **Pinch-to-Zoom**: Use two-finger pinch gesture to zoom in/out.
    *   **Zoom Slider**: SeekBar slider at the bottom of the screen for precise zoom control (0x–1x).
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
  barcode_scanner_plugin: ^0.1.5
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

Use this for devices without a hardware scanner or as a fallback. It opens a fullscreen camera preview with zoom support.

```dart
final result = await _barcodeScanner.startCameraScan();
if (result != null) {
  print('Camera Scanned: $result');
}
```

> **Note**: The camera scanning feature (including zoom) is currently available on **Android only**. iOS support is planned for a future release.

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
        appBar: AppBar(title: const Text('Dual Barcode Scanner')),
        body: Column(
          children: [
            // Hardware Scanner Listener
            StreamBuilder<String>(
              stream: _plugin.barcodeStream,
              builder: (context, snapshot) => Text('PDA: ${snapshot.data ?? "Waiting..."}'),
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

## Platform Support

| Platform | Support |
|----------|---------|
| Android  | ✅ Full support (PDA + Camera with Zoom) |
| iOS      | ⚠️ PDA stream only (Camera coming soon) |

## License

This project is licensed under the MIT License - see the LICENSE file for details.