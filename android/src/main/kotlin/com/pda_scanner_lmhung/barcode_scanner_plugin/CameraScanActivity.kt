package com.pda_scanner_lmhung.barcode_scanner_plugin

import android.Manifest
import androidx.camera.core.ExperimentalGetImage
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.camera.core.Camera
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
    private lateinit var zoomSlider: SeekBar

    private var camera: Camera? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null

    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private var isScanning = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Tạo bố cục giao diện bằng code (Programmatic UI Layout)
        val rootLayout = FrameLayout(this)

        // Khởi tạo màn hình Camera Preview
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        rootLayout.addView(previewView)

        // Vẽ thanh kéo Zoom (SeekBar) đè lên trên Camera
        zoomSlider = SeekBar(this).apply {
            max = 100 // Tương đương từ 0% đến 100% mức zoom
            progress = 0
            // Đặt thanh zoom nằm ở dưới đáy màn hình, cách lề một chút
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(60, 0, 60, 100) // Cách đáy 100px, cách 2 bên 60px
            }
            layoutParams = params
            setBackgroundColor(Color.parseColor("#44000000")) // Tạo nền mờ đen cho dễ nhìn
        }
        rootLayout.addView(zoomSlider)

        setContentView(rootLayout)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // 2. Cấu hình tính năng nhúm 2 ngón tay để Zoom (Pinch-to-zoom)
        setupPinchToZoom()

        // Kiểm tra quyền và khởi động
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

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        if (isScanning) {
                            isScanning = false
                            val intent = Intent().apply {
                                putExtra("SCAN_RESULT", barcode)
                            }
                            setResult(Activity.RESULT_OK, intent)
                            finish()
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // Lưu thực thể camera lại để điều khiển zoom
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

                // 3. Cấu hình sự kiện cho thanh kéo Slider Zoom
                setupZoomSlider()

            } catch (exc: Exception) {
                Toast.makeText(this, "Không thể khởi động camera: ${exc.message}", Toast.LENGTH_SHORT).show()
                finish()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupZoomSlider() {
        zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Chuyển đổi từ giá trị 0-100 thành tỷ lệ zoom tuyến tính 0.0 đến 1.0 của CameraX
                    val linearZoom = progress / 100f
                    camera?.cameraControl?.setLinearZoom(linearZoom)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupPinchToZoom() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cameraInfo = camera?.cameraInfo ?: return false
                val cameraControl = camera?.cameraControl ?: return false

                // Lấy trạng thái zoom hiện tại của camera máy
                val currentZoomState = cameraInfo.zoomState.value ?: return false
                val currentLinearZoom = currentZoomState.linearZoom

                // Tính toán tỷ lệ zoom mới dựa trên khoảng cách co giãn của 2 ngón tay
                val scaleFactor = detector.scaleFactor
                val targetLinearZoom = (currentLinearZoom * scaleFactor).coerceIn(0f, 1f)

                cameraControl.setLinearZoom(targetLinearZoom)

                // Cập nhật lại thanh Slider tiến trình tương ứng theo ngón tay
                zoomSlider.progress = (targetLinearZoom * 100).toInt()
                return true
            }
        })
    }

    // Đẩy sự kiện chạm màn hình vào bộ xử lý cử chỉ nhúm ngón tay
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector?.onTouchEvent(event)
        return super.onTouchEvent(event) || true
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

    private class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
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
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }
}