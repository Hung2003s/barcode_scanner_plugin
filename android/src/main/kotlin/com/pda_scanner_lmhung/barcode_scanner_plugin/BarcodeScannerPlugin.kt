package com.pda_scanner_lmhung.barcode_scanner_plugin

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

class BarcodeScannerPlugin: FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler, ActivityAware, PluginRegistry.ActivityResultListener {
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel

    private var eventSink: EventChannel.EventSink? = null
    private var context: Context? = null
    private var activity: Activity? = null
    private var pendingResult: MethodChannel.Result? = null
    private var barcodeReceiver: BroadcastReceiver? = null

    private val RC_CAMERA_SCAN = 1234

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext

        // 1. Cấu hình MethodChannel
        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_plugin")
        methodChannel.setMethodCallHandler(this)

        // 2. Cấu hình EventChannel
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_plugin/events")
        eventChannel.setStreamHandler(this)
    }

    // Xử lý các lệnh từ Flutter gửi xuống
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "startCameraScan" -> {
                // Lưu lại kết quả để trả về sau khi quét xong qua Camera
                this.pendingResult = result

                if (activity != null) {
                    // Mở màn hình quét Camera độc lập
                    val intent = Intent(activity, CameraScanActivity::class.java)
                    activity?.startActivityForResult(intent, RC_CAMERA_SCAN)
                } else {
                    result.error("NO_ACTIVITY", "Plugin không tìm thấy Activity hiện tại để khởi chạy Camera", null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    // Hứng dữ liệu mã vạch trả về từ CameraScanActivity khi nó đóng (finish)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == RC_CAMERA_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                val barcode = data?.getStringExtra("SCAN_RESULT")
                pendingResult?.success(barcode) // Trả mã vạch về cho Flutter qua MethodChannel
            } else {
                pendingResult?.success(null) // Người dùng bấm back hoặc hủy quét
            }
            pendingResult = null
            return true
        }
        return false
    }

    // --- Triển khai các hàm của ActivityAware để lắng nghe vòng đời ứng dụng ---
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this) // Đăng ký bộ lắng nghe kết quả Activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    // --- Triển khai StreamHandler phục vụ máy quét phần cứng (Broadcast) ---
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        registerBarcodeReceiver()
    }

    override fun onCancel(arguments: Any?) {
        unregisterBarcodeReceiver()
        eventSink = null
    }

    private fun registerBarcodeReceiver() {
        if (context == null || eventSink == null) return

        barcodeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val barcode = intent?.getStringExtra("data_string")
                if (barcode != null) {
                    eventSink?.success(barcode)
                }
            }
        }

        val filter = IntentFilter("com.barcode.action")
        context?.registerReceiver(barcodeReceiver, filter)
    }

    private fun unregisterBarcodeReceiver() {
        try {
            context?.unregisterReceiver(barcodeReceiver)
        } catch (e: Exception) {
            // Hờ hờ, không làm gì cả nếu receiver chưa đăng ký
        }
        barcodeReceiver = null
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        context = null
    }
}