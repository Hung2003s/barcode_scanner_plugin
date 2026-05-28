package com.pda_scanner_lmhung.barcode_scanner_plugin

import android.Manifest
import androidx.camera.core.ExperimentalGetImage
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity quét mã vạch qua Camera sử dụng CameraX + Google ML Kit.
 *
 * Được khởi chạy từ BarcodeScannerPlugin.startCameraScan().
 * Khi quét thành công, trả kết quả về qua Intent extra "SCAN_RESULT"
 * và đóng Activity (finish) để quay lại Flutter.
 */
class CameraScanActivity : FragmentActivity() {
    private lateinit var cameraExecutor: ExecutorService   // Thread riêng xử lý phân tích ảnh
    private lateinit var previewView: PreviewView           // View hiển thị preview camera

    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private var isScanning = true   // Cờ khóa: tránh gửi kết quả nhiều lần khi quét liên tục

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tạo PreviewView bằng code (không cần file XML layout) để plugin gọn nhẹ
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        setContentView(previewView)

        // Thread riêng biệt để phân tích khung hình camera mà không chặn UI
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Kiểm tra và xin quyền Camera trước khi khởi động
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    /**
     * Khởi tạo CameraX:
     * - Preview: Hiển thị hình ảnh camera lên PreviewView.
     * - ImageAnalysis: Phân tích từng khung hình để tìm mã vạch qua ML Kit.
     * - CameraSelector: Sử dụng camera sau (DEFAULT_BACK_CAMERA).
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 1. Cấu hình Preview: hiển thị camera lên màn hình
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 2. Cấu hình ImageAnalysis: xử lý khung hình để phát hiện mã vạch
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Luôn xử lý khung hình mới nhất
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        // Khi tìm thấy mã vạch, khóa trạng thái để không gửi kết quả nhiều lần
                        if (isScanning) {
                            isScanning = false

                            // Đóng gói kết quả và gửi về BarcodeScannerPlugin
                            val intent = Intent().apply {
                                putExtra("SCAN_RESULT", barcode)
                            }
                            setResult(Activity.RESULT_OK, intent)
                            finish() // Đóng Activity, quay lại Flutter
                        }
                    })
                }

            // Luôn sử dụng camera sau
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // Gắn Preview + ImageAnalyzer vào Lifecycle của Activity
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Toast.makeText(this, "Không thể khởi động camera: ${exc.message}", Toast.LENGTH_SHORT).show()
                finish()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /** Kiểm tra tất cả quyền cần thiết đã được cấp chưa */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Xử lý kết quả sau khi người dùng cấp/từ chối quyền Camera */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Ứng dụng cần quyền Camera để quét mã vạch", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /** Giải phóng thread pool khi Activity bị hủy */
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    /**
     * Bộ phân tích khung hình Camera sử dụng Google ML Kit Barcode Scanning.
     *
     * Nhận từng khung hình từ CameraX, chuyển đổi sang InputImage,
     * và gửi đến ML Kit để nhận diện mã vạch. Khi phát hiện mã vạch,
     * gọi callback onBarcodeDetected để trả kết quả về Activity.
     */
    private class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        // Client ML Kit dùng để quét mã vạch (tái sử dụng cho tất cả khung hình)
        private val scanner = BarcodeScanning.getClient()

        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                // Chuyển đổi khung hình từ CameraX (ImageProxy) sang InputImage của ML Kit
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                // Gửi đến ML Kit để nhận diện mã vạch
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue
                            if (rawValue != null) {
                                onBarcodeDetected(rawValue) // Trả kết quả về Activity
                                break // Chỉ lấy mã vạch đầu tiên tìm thấy
                            }
                        }
                    }
                    .addOnFailureListener {
                        // Bỏ qua nếu khung hình hiện tại bị lỗi phân tích
                    }
                    .addOnCompleteListener {
                        // QUAN TRỌNG: Phải đóng imageProxy để CameraX giải phóng bộ nhớ
                        // và tiếp tục gửi khung hình mới. Nếu không đóng, camera sẽ bị treo.
                        imageProxy.close()
                    }
            } else {
                // Khung hình null, vẫn phải đóng để giải phóng tài nguyên
                imageProxy.close()
            }
        }
    }
}
