package com.pda_scanner_lmhung.barcode_scanner_plugin

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

class BarcodeScannerPlugin: FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler, ActivityAware, PluginRegistry.ActivityResultListener {
    // ---- Channels giao tiếp với Flutter ----
    private lateinit var methodChannel: MethodChannel   // Gửi lệnh (Call/Response)
    private lateinit var eventChannel: EventChannel     // Luồng dữ liệu (Stream)

    // ---- State nội bộ ----
    private var eventSink: EventChannel.EventSink? = null      // Gửi dữ liệu stream lên Flutter
    private var context: Context? = null                        // Application context
    private var activity: Activity? = null                      // Activity hiện tại
    private var pendingResult: MethodChannel.Result? = null     // Callback kết quả camera scan
    private var barcodeReceiver: BroadcastReceiver? = null       // Lắng nghe broadcast từ PDA

    private val RC_CAMERA_SCAN = 1234    // RequestCode cho startActivityForResult camera

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext

        // 1. Khởi tạo MethodChannel: dùng để Flutter gọi xuống Native (call/invoke)
        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_plugin")
        methodChannel.setMethodCallHandler(this)

        // 2. Khởi tạo EventChannel: dùng để Native gửi dữ liệu stream (barcode) lên Flutter
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_plugin/events")
        eventChannel.setStreamHandler(this)
    }

    /**
     * Xử lý các lệnh gọi từ Flutter qua MethodChannel.
     *
     * Các method được hỗ trợ:
     * - "startCameraScan": Mở màn hình camera quét mã vạch, trả về kết quả qua callback.
     */
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startCameraScan" -> {
                // Lưu callback kết quả để trả về sau khi CameraScanActivity kết thúc
                this.pendingResult = result

                if (activity != null) {
                    // Mở Activity quét camera độc lập, đợi kết quả qua onActivityResult
                    val intent = Intent(activity, CameraScanActivity::class.java)
                    activity?.startActivityForResult(intent, RC_CAMERA_SCAN)
                } else {
                    result.error("NO_ACTIVITY", "Plugin không tìm thấy Activity hiện tại để khởi chạy Camera", null)
                }
            }
            else -> {
                // Method chưa được hỗ trợ
                result.notImplemented()
            }
        }
    }

    /**
     * Nhận kết quả trả về từ CameraScanActivity khi người dùng quét xong hoặc hủy.
     * - RESULT_OK: Trả mã vạch về Flutter qua pendingResult.
     * - Khác: Trả null (người dùng hủy/quét thất bại).
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == RC_CAMERA_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                val barcode = data?.getStringExtra("SCAN_RESULT")
                pendingResult?.success(barcode)
            } else {
                pendingResult?.success(null) // Người dùng bấm Back hoặc hủy
            }
            pendingResult = null
            return true
        }
        return false
    }

    // ==================== ActivityAware ====================
    // Lắng nghe vòng đời Activity để có thể startActivityForResult đúng cách.

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this) // Đăng ký lắng nghe kết quả từ CameraScanActivity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null // Activity cũ bị hủy do xoay màn hình
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    // ==================== StreamHandler (PDA Hardware Scanner) ====================

    /**
     * Khi Flutter bắt đầu lắng nghe stream (barcodeStream).
     * Đăng ký BroadcastReceiver để nhận intent từ máy quét PDA.
     */
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        registerBarcodeReceiver()
    }

    /**
     * Khi Flutter hủy lắng nghe stream.
     * Hủy đăng ký BroadcastReceiver để tránh rò rỉ bộ nhớ.
     */
    override fun onCancel(arguments: Any?) {
        unregisterBarcodeReceiver()
        eventSink = null
    }

    /**
     * Đăng ký BroadcastReceiver lắng nghe intent từ máy quét PDA.
     * Action: "com.barcode.action", Extra key: "data_string".
     * Khi có mã vạch, gửi lên Flutter qua EventSink.
     */
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

        // Kiểm tra nếu thiết bị đang chạy Android 13 (API 33) hoặc Android 14 (API 34) trở lên
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context?.registerReceiver(
                barcodeReceiver,
                filter,
                Context.RECEIVER_EXPORTED // Bắt buộc phải thêm cờ này cho Android mới
            )
        } else {
            // Đối với các dòng máy Android cũ hơn thì chạy lệnh tiêu chuẩn
            context?.registerReceiver(barcodeReceiver, filter)
        }
    }

    /**
     * Hủy đăng ký BroadcastReceiver để tránh rò rỉ bộ nhớ.
     * Xử lý exception an toàn phòng trường hợp receiver chưa được đăng ký.
     */
    private fun unregisterBarcodeReceiver() {
        try {
            context?.unregisterReceiver(barcodeReceiver)
        } catch (e: Exception) {
            // Không làm gì nếu receiver chưa được đăng ký
        }
        barcodeReceiver = null
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // Dọn dẹp tài nguyên khi plugin bị tách khỏi engine
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        context = null
    }
}
