package com.pda_scanner_lmhung.barcode_scanner_plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class BarcodeScannerPlugin: FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel

    private var eventSink: EventChannel.EventSink? = null
    private var context: Context? = null
    private var barcodeReceiver: BroadcastReceiver? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext

        // 1. Cấu hình MethodChannel (Trùng tên với file _method_channel.dart)
        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_plugin")
        methodChannel.setMethodCallHandler(this)

        // 2. Cấu hình EventChannel (Trùng tên với file _method_channel.dart)
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_plugin/events")
        eventChannel.setStreamHandler(this)
    }

    // Xử lý các lệnh từ Flutter gửi xuống
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method == "getPlatformVersion") {
            result.success("Android ${android.os.Build.VERSION.RELEASE}")
        } else {
            result.notImplemented()
        }
    }

    // Khi Flutter bắt đầu listen Stream
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        registerBarcodeReceiver()
    }

    // Khi Flutter hủy listen Stream (hoặc dispose widget)
    override fun onCancel(arguments: Any?) {
        unregisterBarcodeReceiver()
        eventSink = null
    }

    private fun registerBarcodeReceiver() {
        if (context == null || eventSink == null) return

        barcodeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // TODO: Thay đổi "data_string" tùy thuộc vào hãng máy quét của bạn
                // Ví dụ: Zebra sử dụng "com.symbol.datawedge.data_string"
                val barcode = intent?.getStringExtra("data_string")

                if (barcode != null) {
                    eventSink?.success(barcode) // Đẩy mã vạch lên Flutter công khai
                }
            }
        }

        // TODO: Thay "com.barcode.action" bằng Action Intent cấu hình trên máy quét
        val filter = IntentFilter("com.barcode.action")
        context?.registerReceiver(barcodeReceiver, filter)
    }

    private fun unregisterBarcodeReceiver() {
        try {
            context?.unregisterReceiver(barcodeReceiver)
        } catch (e: Exception) {
            // Xử lý nếu receiver chưa từng được đăng ký
        }
        barcodeReceiver = null
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        context = null
    }
}