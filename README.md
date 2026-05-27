# barcode_scanner_plugin

A Flutter plugin for PDA hardware barcode scanners. This plugin uses `EventChannel` to provide a real-time stream of barcode data, making it easy to integrate with your Flutter application using `StreamBuilder` or standard stream listeners.

## Features

*   **Real-time Scanning**: Receive barcode data immediately when the hardware button is pressed.
*   **Stream-based API**: Simple `barcodeStream` to handle incoming data.
*   **Easy Integration**: Designed specifically for Android PDA devices with integrated scanners.

## Installation

Add `barcode_scanner_plugin` to your `pubspec.yaml` file:

```yaml
dependencies:
  barcode_scanner_plugin:
    git:
      url: https://github.com/your-username/barcode_scanner_plugin.git
```

*(Note: Replace with your actual repository URL or local path)*

## Usage

### 1. Import the package

```dart
import 'package:barcode_scanner_plugin/barcode_scanner_plugin.dart';
```

### 2. Initialize the plugin

```dart
final _barcodeScanner = BarcodeScannerPlugin();
```

### 3. Listen to scanned barcodes

You can use a `StreamBuilder` for a reactive UI:

```dart
StreamBuilder<String>(
  stream: _barcodeScanner.barcodeStream,
  builder: (context, snapshot) {
    if (snapshot.hasData) {
      return Text('Scanned Code: ${snapshot.data}');
    }
    return Text('Waiting for scan...');
  },
)
```

Or listen to the stream manually:

```dart
_barcodeScanner.barcodeStream.listen((barcode) {
  print('Scanned: $barcode');
});
```

### 4. Get Platform Version

```dart
String? version = await _barcodeScanner.getPlatformVersion();
```

## Example

Check the `example` folder for a complete implementation.

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

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Barcode Scanner Example')),
        body: Center(
          child: StreamBuilder<String>(
            stream: _plugin.barcodeStream,
            builder: (context, snapshot) {
              return Text(
                snapshot.hasData 
                    ? 'Result: ${snapshot.data}' 
                    : 'Scan something!',
                style: const TextStyle(fontSize: 20),
              );
            },
          ),
        ),
      ),
    );
  }
}
```

## Permissions

Ensure your Android PDA device has the necessary permissions and the scanning service is enabled in the device settings.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
