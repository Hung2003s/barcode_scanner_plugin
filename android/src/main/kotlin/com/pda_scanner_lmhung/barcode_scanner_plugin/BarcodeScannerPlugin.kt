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

/**
 * BarcodeScannerPlugin - The bridge between Flutter and native Android barcode scanning.
 *
 * This plugin supports TWO scanning modes:
 * 1. Camera scan: Opens CameraScanActivity (CameraX + ML Kit) to scan barcodes via phone camera.
 * 2. PDA hardware scan: Listens for broadcast intents from PDA hardware scanners.
 *
 * Giao diện giữa Flutter và native Android để quét mã vạch.
 * Hỗ trợ 2 chế độ: quét bằng camera và quét bằng máy quét cứng PDA.
 */
class BarcodeScannerPlugin: FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler, ActivityAware, PluginRegistry.ActivityResultListener {

    // ========================================================================
    // Communication channels / Kênh giao tiếp với Flutter
    // ========================================================================

    /**
     * MethodChannel: Used for command/response calls from Flutter to native.
     * Example: Flutter calls "startCameraScan" → native opens camera → returns result.
     *
     * Dùng cho các lệnh gọi từ Flutter xuống native và nhận kết quả trả về.
     */
    private lateinit var methodChannel: MethodChannel

    /**
     * EventChannel: Used for continuous stream data from native to Flutter.
     * Example: PDA hardware scanner continuously sends barcodes to Flutter via stream.
     *
     * Dùng cho luồng dữ liệu liên tục từ native lên Flutter (ví dụ: máy quét PDA).
     */
    private lateinit var eventChannel: EventChannel

    // ========================================================================
    // Internal state / Trạng thái nội bộ
    // ========================================================================

    /**
     * EventSink: The callback used to push event data to Flutter's Dart side.
     * Only valid while Flutter is listening to the stream.
     *
     * Callback để gửi dữ liệu event lên phía Flutter (Dart).
     */
    private var eventSink: EventChannel.EventSink? = null

    /** Application context / Context ứng dụng */
    private var context: Context? = null

    /** Current Activity / Activity hiện tại */
    private var activity: Activity? = null

    /**
     * Pending result callback for camera scan.
     * When Flutter calls startCameraScan(), the result is stored here until
     * CameraScanActivity finishes and returns the barcode.
     *
     * Callback kết quả đang chờ khi quét camera.
     */
    private var pendingResult: MethodChannel.Result? = null

    /**
     * BroadcastReceiver for PDA hardware scanner.
     * Listens for system broadcasts with barcode data from physical scanner buttons.
     *
     * Receiver lắng nghe broadcast từ máy quét PDA phần cứng.
     */
    private var barcodeReceiver: BroadcastReceiver? = null

    /** Request code for startActivityForResult to launch CameraScanActivity */
    private val RC_CAMERA_SCAN = 1234

    // ========================================================================
    // FlutterPlugin lifecycle / Vòng đời plugin Flutter
    // ========================================================================

    /**
     * Called when the plugin is attached to the Flutter engine.
     * Initializes communication channels with Flutter.
     *
     * Được gọi khi plugin được gắn vào Flutter engine.
     * Khởi tạo các kênh giao tiếp (MethodChannel, EventChannel).
     */
    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext

        // Initialize MethodChannel for call/response communication
        // Khởi tạo MethodChannel để giao tiếp dạng gọi/trả lời
        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_plugin")
        methodChannel.setMethodCallHandler(this)

        // Initialize EventChannel for streaming data (PDA hardware scanner)
        // Khởi tạo EventChannel để gửi dữ liệu stream (máy quét PDA)
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_plugin/events")
        eventChannel.setStreamHandler(this)
    }

    /**
     * Called when the plugin is detached from the Flutter engine.
     * Cleans up resources to avoid memory leaks.
     *
     * Được gọi khi plugin bị tách khỏi Flutter engine.
     * Dọn dẹp tài nguyên để tránh rò rỉ bộ nhớ.
     */
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        context = null
    }

    // ========================================================================
    // MethodChannel handler / Xử lý lệnh từ Flutter
    // ========================================================================

    /**
     * Handle method calls from Flutter via MethodChannel.
     *
     * Supported methods / Các method được hỗ trợ:
     * - "startCameraScan": Opens camera scan activity / Mở màn hình quét camera
     *
     * @param call The method call from Flutter / Lệnh gọi từ Flutter
     * @param result The callback to send result back / Callback để gửi kết quả về
     */
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startCameraScan" -> {
                // ============================================================
                // startCameraScan: Launch CameraX + ML Kit barcode scanner
                //
                // Flow / Luồng xử lý:
                // 1. Store result callback for later use / Lưu callback để dùng sau
                // 2. Start CameraScanActivity and wait for result / Mở Activity quét
                // 3. onActivityResult returns the barcode or null / Trả kết quả về
                // ============================================================
                this.pendingResult = result

                if (activity != null) {
                    val intent = Intent(activity, CameraScanActivity::class.java)
                    activity?.startActivityForResult(intent, RC_CAMERA_SCAN)
                } else {
                    // No activity available - this can happen if the plugin
                    // hasn't been attached to an activity yet.
                    // Không có Activity - có thể plugin chưa được gắn vào Activity.
                    result.error("NO_ACTIVITY",
                        "Plugin không tìm thấy Activity hiện tại để khởi chạy Camera", null)
                }
            }
            else -> {
                // Method not supported / Method chưa được hỗ trợ
                result.notImplemented()
            }
        }
    }

    // ========================================================================
    // Activity result handler / Xử lý kết quả từ Activity
    // ========================================================================

    /**
     * Handle result returned from CameraScanActivity.
     *
     * When CameraScanActivity finishes (either with a barcode or cancelled),
     * this method receives the result and sends it back to Flutter.
     *
     * Nhận kết quả trả về từ CameraScanActivity khi quét xong hoặc hủy.
     *
     * @param requestCode The request code (should match RC_CAMERA_SCAN)
     * @param resultCode RESULT_OK if barcode was found, RESULT_CANCELED if user cancelled
     * @param data Intent containing "SCAN_RESULT" extra with the barcode string
     * @return true if handled, false otherwise
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == RC_CAMERA_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                // Barcode found! Return it to Flutter / Tìm thấy mã vạch! Trả về Flutter
                val barcode = data?.getStringExtra("SCAN_RESULT")
                pendingResult?.success(barcode)
            } else {
                // User cancelled or error / Người dùng hủy hoặc có lỗi
                pendingResult?.success(null)
            }
            pendingResult = null
            return true
        }
        return false
    }

    // ========================================================================
    // ActivityAware implementation / Lắng nghe vòng đời Activity
    // ========================================================================
    //
    // These methods track the Activity lifecycle so the plugin can properly
    // launch CameraScanActivity with startActivityForResult.
    //
    // Các phương thức này theo dõi vòng đời Activity để có thể mở
    // CameraScanActivity đúng cách.

    /**
     * Called when the plugin is attached to an Activity.
     * Registers the activity result listener.
     *
     * Được gọi khi plugin được gắn vào một Activity.
     * Đăng ký lắng nghe kết quả từ CameraScanActivity.
     */
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    /**
     * Called when the Activity is detached for a configuration change (e.g. rotation).
     * The old Activity will be replaced with a new one.
     *
     * Được gọi khi Activity bị hủy do thay đổi cấu hình (ví dụ xoay màn hình).
     */
    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    /**
     * Called when a new Activity is attached after a configuration change.
     *
     * Được gọi khi Activity mới được gắn lại sau khi thay đổi cấu hình.
     */
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    /**
     * Called when the plugin is detached from the Activity (e.g. app goes to background).
     *
     * Được gọi khi plugin bị tách khỏi Activity (ví dụ app vào nền).
     */
    override fun onDetachedFromActivity() {
        activity = null
    }

    // ========================================================================
    // EventChannel (Stream) handler for PDA hardware scanner
    // ========================================================================
    //
    // The PDA hardware scanner sends barcode data via system broadcast intents.
    // When Flutter subscribes to barcodeStream, we register a BroadcastReceiver
    // to listen for these intents and forward the data to Flutter.
    //
    // Máy quét PDA gửi dữ liệu mã vạch qua system broadcast intents.
    // Khi Flutter đăng ký lắng nghe barcodeStream, chúng ta đăng ký
    // BroadcastReceiver để nhận các intent và gửi dữ liệu lên Flutter.

    /**
     * Called when Flutter starts listening to the event stream (barcodeStream).
     * Registers the BroadcastReceiver for PDA hardware scanner intents.
     *
     * Được gọi khi Flutter bắt đầu lắng nghe stream (barcodeStream).
     * Đăng ký BroadcastReceiver để nhận intent từ máy quét PDA.
     *
     * @param arguments Optional arguments from Flutter / Tham số tùy chọn từ Flutter
     * @param events EventSink to push data to Flutter / EventSink để gửi dữ liệu lên Flutter
     */
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        registerBarcodeReceiver()
    }

    /**
     * Called when Flutter stops listening to the event stream.
     * Unregisters the BroadcastReceiver to free resources.
     *
     * Được gọi khi Flutter ngừng lắng nghe stream.
     * Hủy đăng ký BroadcastReceiver để giải phóng tài nguyên.
     */
    override fun onCancel(arguments: Any?) {
        unregisterBarcodeReceiver()
        eventSink = null
    }

    /**
     * Register a BroadcastReceiver for PDA hardware scanner intents.
     *
     * PDA scanners typically send broadcasts with action "com.barcode.action"
     * and barcode data in the "data_string" extra field.
     *
     * For Android 13+ (API 33), RECEIVER_EXPORTED flag is required.
     *
     * Đăng ký BroadcastReceiver lắng nghe intent từ máy quét PDA.
     * Action: "com.barcode.action", Extra key: "data_string".
     * Trên Android 13+ cần cờ RECEIVER_EXPORTED.
     */
    private fun registerBarcodeReceiver() {
        if (context == null || eventSink == null) return

        barcodeReceiver = object : BroadcastReceiver() {
            /**
             * Called when a broadcast intent is received from the PDA scanner.
             * Extracts the barcode data and sends it to Flutter via EventSink.
             *
             * Được gọi khi nhận được broadcast từ máy quét PDA.
             * Lấy dữ liệu mã vạch và gửi lên Flutter qua EventSink.
             */
            override fun onReceive(context: Context?, intent: Intent?) {
                val barcode = intent?.getStringExtra("data_string")
                if (barcode != null) {
                    eventSink?.success(barcode)
                }
            }
        }

        val filter = IntentFilter("com.barcode.action")

        // Android 13+ requires RECEIVER_EXPORTED for registering
        // system broadcasts that may come from other apps.
        // Android 13+ yêu cầu cờ RECEIVER_EXPORTED.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context?.registerReceiver(barcodeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context?.registerReceiver(barcodeReceiver, filter)
        }
    }

    /**
     * Unregister the BroadcastReceiver to prevent memory leaks.
     * Handles the case where the receiver was never registered.
     *
     * Hủy đăng ký BroadcastReceiver để tránh rò rỉ bộ nhớ.
     * Xử lý an toàn trường hợp receiver chưa được đăng ký.
     */
    private fun unregisterBarcodeReceiver() {
        try {
            context?.unregisterReceiver(barcodeReceiver)
        } catch (e: Exception) {
            // Receiver was not registered, which is safe to ignore
            // Receiver chưa được đăng ký, ignore lỗi này
        }
        barcodeReceiver = null
    }
}