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

class CameraScanActivity : FragmentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView

    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private var isScanning = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi tạo PreviewView bằng code giúp plugin gọn nhẹ, không cần file XML layout
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        setContentView(previewView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Kiểm tra và xin quyền truy cập Camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Cấu hình luồng hiển thị (Preview) lên màn hình
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Cấu hình luồng phân tích hình ảnh (Image Analysis) để quét mã vạch
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        if (isScanning) {
                            isScanning = false // Khóa trạng thái để không bắn kết quả liên tục

                            val intent = Intent().apply {
                                putExtra("SCAN_RESULT", barcode)
                            }
                            setResult(Activity.RESULT_OK, intent)
                            finish() // Đóng Activity và quay lại Flutter
                        }
                    })
                }

            // Mặc định luôn chọn Camera sau
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // Gắn luồng Camera vào Vòng đời (Lifecycle) của Activity
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Toast.makeText(this, "Không thể khởi động camera: ${exc.message}", Toast.LENGTH_SHORT).show()
                finish()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // Bộ phân tích hình ảnh kết hợp Google ML Kit
    private class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                // Chuyển đổi khung hình từ CameraX sang định dạng InputImage của ML Kit
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue
                            if (rawValue != null) {
                                onBarcodeDetected(rawValue)
                                break
                            }
                        }
                    }
                    .addOnFailureListener {
                        // Bỏ qua nếu khung hình hiện tại phân tích lỗi
                    }
                    .addOnCompleteListener {
                        // Cực kỳ quan trọng: Phải đóng imageProxy để CameraX giải phóng bộ nhớ và gửi khung hình tiếp theo
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
