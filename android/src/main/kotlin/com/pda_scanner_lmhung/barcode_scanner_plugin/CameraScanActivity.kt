package com.pda_scanner_lmhung.barcode_scanner_plugin

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraScanActivity - Full-screen barcode scanner using CameraX + Google ML Kit.
 *
 * Features / Tính năng:
 * - Real-time barcode scanning via rear camera / Quét mã vạch real-time bằng camera sau
 * - Transparent scan frame in center with blue blurred overlay / Khung quét trong suốt giữa màn hình
 * - Flash toggle (torch) / Bật/tắt đèn flash
 * - Zoom control via right-side touch wheel / Điều khiển zoom bằng thanh cuộn bên phải
 * - Animated scan line / Vạch quét hoạt ảnh
 * - Performance optimized with frame throttling / Tối ưu hiệu năng với giới hạn frame
 *
 * Layout structure / Cấu trúc layout:
 *   FrameLayout (root)
 *   ├── PreviewView (camera preview, fills entire screen)
 *   ├── ScannerOverlayView (scan frame + blur overlay)
 *   ├── Top bar (Flash + Close buttons)
 *   └── Right side zoom panel (wheel + +/- buttons)
 */
class CameraScanActivity : FragmentActivity() {

    // ========================================================================
    // Camera & execution / Camera và luồng xử lý
    // ========================================================================

    /**
     * Single-thread executor for barcode analysis.
     * All ML Kit processing runs on this thread to avoid thread-safety issues.
     *
     * Executor đơn luồng để phân tích mã vạch.
     * Tất cả xử lý ML Kit chạy trên luồng này để tránh vấn đề đồng bộ.
     */
    private lateinit var cameraExecutor: ExecutorService

    /**
     * CameraX PreviewView - displays the live camera feed.
     * Hiển thị luồng camera trực tiếp.
     */
    private lateinit var previewView: PreviewView

    /**
     * CameraX Camera instance for controlling zoom and flash.
     * Instance CameraX để điều khiển zoom và flash.
     */
    private var camera: Camera? = null

    // ========================================================================
    // UI components / Thành phần giao diện
    // ========================================================================

    /** Custom overlay with scan frame, blur edges, corner markers, scan line */
    private lateinit var scannerOverlay: ScannerOverlayView

    /** Flash toggle button (lightning bolt icon) in top-left */
    private lateinit var flashButton: ImageView

    /** Close button (X icon) in top-right */
    private lateinit var closeButton: ImageView

    /** Touch-sensitive zoom wheel view on the right side */
    private lateinit var zoomWheel: View

    /** Zoom level text display (e.g., "1.0x", "5.5x") */
    private lateinit var zoomValueText: TextView

    /** Zoom out button (-) */
    private lateinit var zoomMinusBtn: View

    /** Zoom in button (+) */
    private lateinit var zoomPlusBtn: View

    // ========================================================================
    // Pre-allocated drawables (cached — no Bitmap creation per toggle)
    // Drawable được cache sẵn — không tạo Bitmap mỗi lần bật/tắt
    // ========================================================================

    /** Cached flash drawable: OFF state / Drawable flash đã cache: trạng thái TẮT */
    private var flashOffDrawable: android.graphics.drawable.Drawable? = null

    /** Cached flash drawable: ON state / Drawable flash đã cache: trạng thái BẬT */
    private var flashOnDrawable: android.graphics.drawable.Drawable? = null

    /** Cached close (X) drawable / Drawable đóng (X) đã cache */
    private var closeDrawable: android.graphics.drawable.Drawable? = null

    // ========================================================================
    // State / Trạng thái
    // ========================================================================

    /**
     * Atomic flag to ensure barcode is only captured once.
     * Prevents multiple scan results from the same scan session.
     *
     * Cờ nguyên tử đảm bảo chỉ quét mã một lần.
     * Ngăn nhiều kết quả trùng lặp trong cùng một phiên quét.
     */
    private val isScanning = AtomicBoolean(true)

    /** Flash (torch) on/off state / Trạng thái đèn flash */
    private var isFlashOn = false

    /** Current zoom level (0.0 = 1x, 1.0 = 10x) / Mức zoom hiện tại */
    private var currentZoom = 0f

    /** Scan line animation / Hoạt ảnh vạch quét */
    private var scanLineAnimator: ValueAnimator? = null

    // ========================================================================
    // Permissions / Quyền
    // ========================================================================

    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    // ========================================================================
    // Activity Lifecycle / Vòng đời Activity
    // ========================================================================

    /**
     * Called when the activity is created.
     * Builds the UI, initializes camera executor, checks permissions.
     *
     * Được gọi khi Activity được tạo.
     * Xây dựng UI, khởi tạo executor camera, kiểm tra quyền.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build UI programmatically (no XML) for maximum performance
        // Xây dựng UI bằng code để tối ưu hiệu năng
        buildUI()

        // Single thread for barcode analysis to avoid synchronization issues
        // Đơn luồng cho phân tích mã vạch để tránh vấn đề đồng bộ
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    /**
     * Resume - restart scan line animation when returning to foreground.
     * Also ensures flash is off when resuming (safety).
     *
     * Khởi động lại hoạt ảnh vạch quét khi quay lại foreground.
     */
    override fun onResume() {
        super.onResume()
        if (::scannerOverlay.isInitialized) {
            scannerOverlay.startAnimation()
            startScanLineAnimation()
        }
    }

    /**
     * Pause - stop scan animation and turn off flash.
     * Prevents battery drain and camera resource conflicts.
     *
     * Dừng hoạt ảnh quét và tắt flash khi tạm dừng.
     */
    override fun onPause() {
        super.onPause()
        scanLineAnimator?.cancel()
        scannerOverlay.stopAnimation()
        if (isFlashOn) {
            isFlashOn = false
            camera?.cameraControl?.enableTorch(false)
        }
    }

    /**
     * Destroy - clean up resources.
     * Dọn dẹp tài nguyên khi Activity bị hủy.
     */
    override fun onDestroy() {
        super.onDestroy()
        scanLineAnimator?.cancel()
        cameraExecutor.shutdown()
    }

    // ========================================================================
    // UI Building / Xây dựng giao diện
    // ========================================================================

    /**
     * Build the complete UI programmatically.
     *
     * All views are created in code (no XML inflation) for two reasons:
     * 1. Performance: No XML parsing overhead / Không chi phí phân tích XML
     * 2. Flexibility: Easy to customize every aspect / Dễ tùy chỉnh mọi khía cạnh
     *
     * Layout hierarchy / Hệ thống phân cấp:
     *   FrameLayout (root)
     *   ├── PreviewView (camera preview)
     *   ├── ScannerOverlayView (scan overlay)
     *   ├── TopBar (LinearLayout: Flash + Close)
     *   └── ZoomPanel (FrameLayout: wheel + +/- buttons)
     */
    private fun buildUI() {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // 1. Camera preview - fills entire screen behind overlay
        // Camera preview - lấp đầy toàn màn hình phía sau overlay
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        rootLayout.addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 2. Scanner overlay - transparent frame with blur + borders
        // Scanner overlay - khung trong suốt với viền mờ + đường viền
        scannerOverlay = ScannerOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            post {
                updateFrameRect(width, height)
                startScanLineAnimation()
            }
        }
        rootLayout.addView(scannerOverlay)

        // 3. Top bar with Flash + Close buttons
        rootLayout.addView(createTopBar())

        // 4. Right side zoom panel
        rootLayout.addView(createZoomPanel())

        setContentView(rootLayout)
    }

    /**
     * Create the top bar containing flash and close buttons.
     *
     * Tạo thanh trên cùng chứa nút flash và nút đóng.
     *
     * Layout: LinearLayout horizontal
     *   [FlashButton] ---spacer--- [CloseButton]
     */
    private fun createTopBar(): View {
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                160
            ).apply {
                gravity = Gravity.TOP
                setMargins(0, 48, 0, 0)
            }
            setPadding(32, 0, 32, 0)
        }

        // Pre-create cached drawables cho flash và close buttons
        flashOffDrawable = createFlashDrawable(false)
        flashOnDrawable = createFlashDrawable(true)
        closeDrawable = createCloseDrawable()

        // Flash toggle button - lightning bolt icon
        // Nút bật/tắt flash - biểu tượng tia sét
        flashButton = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(110, 110).apply {
                marginStart = 16
                marginEnd = 16
            }
            setImageDrawable(flashOffDrawable)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Flash"
            setOnClickListener { toggleFlash() }
        }
        topBar.addView(flashButton)

        // Spacer to push buttons to left and right edges
        // Khoảng trống đẩy 2 nút về 2 bên
        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        // Close (cancel) button - X icon
        // Nút đóng (hủy) - biểu tượng X
        closeButton = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(110, 110).apply {
                marginStart = 16
                marginEnd = 16
            }
            setImageDrawable(closeDrawable)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Close"
            setOnClickListener { finishWithCancel() }
        }
        topBar.addView(closeButton)

        return topBar
    }

    /**
     * Create the zoom control panel on the right side of the screen.
     *
     * Contains / Bao gồm:
     * - Zoom value text at top / Giá trị zoom ở trên cùng
     * - (+) button / Nút tăng zoom
     * - Touch wheel with visual indicator / Bánh xe zoom với chỉ thị
     * - (-) button / Nút giảm zoom
     * - Semi-transparent pill background / Nền hình viên thuốc mờ
     */
    private fun createZoomPanel(): View {
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(160, 520).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setMargins(0, 0, 20, 0)
            }
        }

        // Semi-transparent pill background for the zoom panel
        // Nền hình viên thuốc mờ cho panel zoom
        val bg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(80, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = createPillBackground()
            alpha = 0.7f
        }
        container.addView(bg)

        // Zoom level text (e.g., "1.0x", "5.5x", "10.0x")
        // Chữ hiển thị mức zoom
        zoomValueText = TextView(this).apply {
            text = "1.0x"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 44
            ).apply { topMargin = 6 }
        }
        container.addView(zoomValueText)

        // Zoom in button (+)
        zoomPlusBtn = createRoundButton("+") {
            setZoom((currentZoom + 0.1f).coerceAtMost(1f))
        }
        container.addView(zoomPlusBtn.apply {
            layoutParams = FrameLayout.LayoutParams(60, 60).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 52
            }
        })

        // Touch-sensitive zoom wheel view (custom drawn)
        // Thanh trượt zoom cảm ứng (vẽ custom)
        zoomWheel = createZoomWheel()
        container.addView(zoomWheel.apply {
            layoutParams = FrameLayout.LayoutParams(80, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })

        // Zoom out button (-)
        zoomMinusBtn = createRoundButton("−") {
            setZoom((currentZoom - 0.1f).coerceAtLeast(0f))
        }
        container.addView(zoomMinusBtn.apply {
            layoutParams = FrameLayout.LayoutParams(60, 60).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 8
            }
        })

        // Re-add +/- buttons on top for proper z-ordering
        // Thêm lại 2 nút lên trên để đúng thứ tự z
        container.removeView(zoomPlusBtn)
        container.removeView(zoomMinusBtn)
        container.addView(zoomPlusBtn)
        container.addView(zoomMinusBtn)

        return container
    }

    /**
     * Create the custom zoom wheel view.
     *
     * Optimized: All Paint objects are pre-allocated as fields (zero allocation in onDraw).
     * Draws / Vẽ:
     * - Vertical track line / Đường ray dọc
     * - Active track (green) / Phần track đã chọn (màu xanh)
     * - Thumb indicator (green circle with white dot) / Núm chỉ thị
     * - Tick marks at 10% intervals / Vạch chia tại mỗi 10%
     */
    private fun createZoomWheel(): View {
        return object : View(this) {
            // --- Pre-allocated Paints (zero GC in onDraw) ---
            private val trackPaint = Paint().apply {
                color = Color.argb(60, 255, 255, 255)
                strokeWidth = 2f
                isAntiAlias = true
            }
            private val activePaint = Paint().apply {
                color = Color.parseColor("#00E676")
                strokeWidth = 3f
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
            private val thumbPaint = Paint().apply {
                color = Color.parseColor("#00E676")
                isAntiAlias = true
            }
            private val innerPaint = Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
            }
            private val tickPaint = Paint().apply {
                color = Color.argb(40, 255, 255, 255)
                strokeWidth = 1f
                isAntiAlias = true
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val midX = w / 2f

                // Calculate indicator Y position based on current zoom
                // top (zoom=1) → y = h*0.15, bottom (zoom=0) → y = h*0.85
                val indicatorY = h * 0.15f + (h * 0.7f) * (1f - currentZoom)

                // 1. Full track line (subtle white)
                canvas.drawLine(midX, h * 0.15f, midX, h * 0.85f, trackPaint)

                // 2. Active track (green - from thumb to bottom)
                canvas.drawLine(midX, indicatorY, midX, h * 0.85f, activePaint)

                // 3. Thumb outer circle (green)
                canvas.drawCircle(midX, indicatorY, 14f, thumbPaint)

                // 4. Thumb inner circle (white dot)
                canvas.drawCircle(midX, indicatorY, 6f, innerPaint)

                // 5. Subtle tick marks at every 10% zoom
                val tickCount = 10
                val tickRange = h * 0.7f
                for (i in 0..tickCount) {
                    val ty = h * 0.15f + tickRange * (i.toFloat() / tickCount)
                    canvas.drawLine(midX - 6f, ty, midX + 6f, ty, tickPaint)
                }
            }
        }.apply {
            // Handle touch events: map finger Y position to zoom level
            // Xử lý cảm ứng: ánh xạ vị trí Y của ngón tay thành mức zoom
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                    val zoom = calcZoomFromY(event.y, height)
                    setZoom(zoom)
                }
                true
            }
        }
    }

    /**
     * Handle touch events on the zoom wheel.
     * Optimized: removed unused lastWheelY tracking.
     *
     * Xử lý sự kiện chạm trên bánh xe zoom.
     */
    private fun handleWheelTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val zoom = calcZoomFromY(event.y, zoomWheel.height)
                setZoom(zoom)
            }
            MotionEvent.ACTION_MOVE -> {
                val zoom = calcZoomFromY(event.y, zoomWheel.height)
                setZoom(zoom)
            }
        }
    }

    /**
     * Convert a Y coordinate to a zoom value (0.0 - 1.0).
     *
     * The zoom wheel has padding at top (15%) and bottom (15%) of its height.
     * Only the middle 70% is active for zoom control.
     * Top of active area = zoom 1.0 (10x), Bottom = zoom 0.0 (1x).
     *
     * Chuyển đổi tọa độ Y thành giá trị zoom (0.0 - 1.0).
     */
    private fun calcZoomFromY(y: Float, height: Int): Float {
        val h = height.toFloat()
        val minY = h * 0.15f
        val maxY = h * 0.85f
        val clampedY = y.coerceIn(minY, maxY)
        return 1f - (clampedY - minY) / (maxY - minY)
    }

    /**
     * Apply a zoom value to the camera and update UI.
     *
     * CameraX uses linear zoom (0.0 = 1x, 1.0 = 10x).
     * Display zoom shows 1.0x to 10.0x for user readability.
     *
     * Áp dụng giá trị zoom vào camera và cập nhật UI.
     */
    private fun setZoom(zoom: Float) {
        currentZoom = zoom.coerceIn(0f, 1f)
        camera?.cameraControl?.setLinearZoom(currentZoom)
        val displayZoom = 1f + currentZoom * 9f
        zoomValueText.text = String.format("%.1fx", displayZoom)
        zoomWheel.invalidate()
    }

    /**
     * Create a circular button with text (+ or -).
     *
     * Tạo nút tròn với chữ (+ hoặc -).
     * Background: semi-transparent black circle
     * Text: white, bold, centered
     */
    private fun createRoundButton(text: String, onClick: () -> Unit): View {
        return object : View(this) {
            private val bgPaint = Paint().apply {
                color = Color.argb(100, 0, 0, 0)
                isAntiAlias = true
            }
            private val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 32f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }
            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val r = minOf(cx, cy)
                canvas.drawCircle(cx, cy, r, bgPaint)
                // Center text vertically
                canvas.drawText(text, cx, cy - ((textPaint.descent() + textPaint.ascent()) / 2f), textPaint)
            }
        }.apply { setOnClickListener { onClick() } }
    }

    /**
     * Create a lightning bolt icon for the flash button.
     *
     * When flash is ON: Yellow (#FFEB3B) fill + stroke
     * When flash is OFF: White fill + semi-transparent stroke
     *
     * Tạo biểu tượng tia sét cho nút flash.
     */
    private fun createFlashDrawable(on: Boolean): android.graphics.drawable.Drawable {
        val dpSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 60f, resources.displayMetrics
        ).toInt()
        val bitmap = Bitmap.createBitmap(dpSize, dpSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = if (on) Color.parseColor("#FFEB3B") else Color.WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Draw lightning bolt path
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val s = bitmap.width / 12f
        val path = Path().apply {
            moveTo(cx + 2f * s, cy - 4f * s)
            lineTo(cx - 2f * s, cy + 1f * s)
            lineTo(cx + 0.5f * s, cy + 1f * s)
            lineTo(cx - 2f * s, cy + 4f * s)
            lineTo(cx + 2f * s, cy - 1f * s)
            lineTo(cx - 0.5f * s, cy - 1f * s)
            close()
        }
        canvas.drawPath(path, paint)

        // Outline stroke for better visibility
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = if (on) Color.parseColor("#FFEB3B") else Color.argb(80, 255, 255, 255)
        canvas.drawPath(path, paint)

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    /**
     * Create an X icon for the close button.
     * Tạo biểu tượng X cho nút đóng.
     */
    private fun createCloseDrawable(): android.graphics.drawable.Drawable {
        val size = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 54f, resources.displayMetrics
        ).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        val pad = size / 5f
        canvas.drawLine(pad, pad, size - pad, size - pad, paint)
        canvas.drawLine(size - pad, pad, pad, size - pad, paint)
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    /**
     * Create a semi-transparent pill-shaped background for the zoom panel.
     * Tạo nền hình viên thuốc mờ cho panel zoom.
     */
    private fun createPillBackground(): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            private val paint = Paint().apply {
                color = Color.argb(80, 0, 0, 0)
                isAntiAlias = true
            }
            override fun draw(canvas: Canvas) {
                val r = bounds.width() / 2f
                canvas.drawRoundRect(
                    bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), bounds.bottom.toFloat(),
                    r, r, paint
                )
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    // ========================================================================
    // Flash (Torch) Control / Điều khiển đèn flash
    // ========================================================================

    /**
     * Toggle the camera flash (torch mode) on/off.
     *
     * Optimized: uses cached drawables instead of recreating Bitmaps each toggle.
     *
     * Flow / Luồng xử lý:
     * 1. Check camera is ready / Kiểm tra camera sẵn sàng
     * 2. Check device has flash unit / Kiểm tra thiết bị có flash
     * 3. Toggle flash state / Đảo trạng thái flash
     * 4. Update icon (yellow when on, white when off) + scale animation
     *
     * Bật/tắt đèn flash camera.
     */
    private fun toggleFlash() {
        val cameraInfo = camera?.cameraInfo
        if (cameraInfo == null) {
            Toast.makeText(this, "Camera chưa sẵn sàng", Toast.LENGTH_SHORT).show()
            return
        }

        if (!cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "Thiết bị không hỗ trợ đèn flash", Toast.LENGTH_SHORT).show()
            return
        }

        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)

        // Use cached drawable — no Bitmap allocation
        flashButton.setImageDrawable(if (isFlashOn) flashOnDrawable else flashOffDrawable)
        flashButton.animate()
            .scaleX(1.3f).scaleY(1.3f).setDuration(100)
            .withEndAction {
                flashButton.animate()
                    .scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            .start()
    }

    /**
     * Finish the activity with RESULT_CANCELED (user pressed close/back).
     * Kết thúc Activity với RESULT_CANCELED (người dùng nhấn đóng/quay lại).
     */
    private fun finishWithCancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    // ========================================================================
    // Camera Setup / Thiết lập Camera
    // ========================================================================

    /**
     * Start CameraX with preview and barcode analysis.
     *
     * Uses / Sử dụng:
     * - Preview use case: Display camera feed / Hiển thị luồng camera
     * - ImageAnalysis: Scan barcodes with ML Kit / Quét mã vạch với ML Kit
     * - STRATEGY_KEEP_ONLY_LATEST: Only process latest frame, drop stale ones
     * - Single-thread executor for analysis / Executor đơn luồng cho phân tích
     *
     * Khởi động CameraX với preview và phân tích mã vạch.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview: shows camera feed on PreviewView
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Image analysis: barcode detection with performance optimizations
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        if (isScanning.compareAndSet(true, false)) {
                            runOnUiThread {
                                val intent = Intent().apply {
                                    putExtra("SCAN_RESULT", barcode)
                                }
                                setResult(Activity.RESULT_OK, intent)
                                finish()
                            }
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Toast.makeText(this, "Không thể khởi động camera: ${exc.message}", Toast.LENGTH_SHORT).show()
                finish()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // ========================================================================
    // Scan Line Animation / Hoạt ảnh vạch quét
    // ========================================================================

    /**
     * Start the scan line animation.
     *
     * Uses ValueAnimator to drive the scan line up and down within the frame.
     * The animation is infinite with a 2-second cycle.
     *
     * Khởi động hoạt ảnh vạch quét.
     * Dùng ValueAnimator để di chuyển vạch quét lên xuống trong khung.
     */
    private fun startScanLineAnimation() {
        val frameRect = scannerOverlay.frameRect
        if (frameRect.isEmpty) return

        scanLineAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { scannerOverlay.updateScanLine() }
            start()
        }
    }

    // ========================================================================
    // Permissions / Quyền
    // ========================================================================

    /**
     * Check if all required permissions are granted.
     * Kiểm tra tất cả quyền cần thiết đã được cấp.
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Handle permission request result.
     * Xử lý kết quả yêu cầu quyền.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this, "Ứng dụng cần quyền Camera để quét mã vạch", Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    // ========================================================================
    // Barcode Analyzer (Optimized) / Bộ phân tích mã vạch (tối ưu)
    // ========================================================================

    /**
     * Optimized barcode analyzer for CameraX ImageAnalysis.
     *
     * Performance optimizations / Tối ưu hiệu năng:
     * - Throttled: Only analyzes every 150ms to save CPU/battery
     * - KEEP_ONLY_LATEST: Skips stale frames automatically
     * - Limited barcode formats: Only common formats for faster matching
     * - Atomic flag: Prevents multiple detection callbacks
     * - Proper imageProxy.close(): Prevents memory leaks
     *
     * Barcode formats supported / Định dạng mã vạch hỗ trợ:
     * CODE_128, CODE_39, EAN_13, EAN_8, QR_CODE, UPC_A, UPC_E,
     * ITF, CODABAR, DATA_MATRIX, PDF417
     */
    private class BarcodeAnalyzer(
        private val onBarcodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        /**
         * ML Kit barcode scanner with restricted format set for faster scanning.
         * Chỉ quét các định dạng phổ biến để tăng tốc độ.
         */
        private val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ITF,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODABAR,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417
                )
                .build()
        )

        /** Analysis throttle interval in ms / Khoảng thời gian giữa các lần phân tích (ms) */
        private val ANALYSIS_INTERVAL_MS = 150L

        /** Timestamp of last analysis for throttling / Mốc thời gian phân tích cuối */
        private var lastAnalysisTime = 0L

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            // ============================================================
            // Step 1: Throttle - skip if too soon since last analysis
            // Bước 1: Giới hạn - bỏ qua nếu quá sớm so với lần phân tích trước
            // ============================================================
            val now = System.currentTimeMillis()
            if (now - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
                imageProxy.close()
                return
            }
            lastAnalysisTime = now

            // ============================================================
            // Step 2: Get the camera frame image
            // Bước 2: Lấy hình ảnh từ camera
            // ============================================================
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            // ============================================================
            // Step 3: Process with ML Kit barcode scanner
            // Bước 3: Xử lý với ML Kit để tìm mã vạch
            // ============================================================
            try {
                val inputImage = InputImage.fromMediaImage(
                    mediaImage, imageProxy.imageInfo.rotationDegrees
                )
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        // Found barcode(s) - return first valid result
                        // Tìm thấy mã vạch - trả về kết quả đầu tiên
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue
                            if (rawValue != null) {
                                onBarcodeDetected(rawValue)
                                break
                            }
                        }
                    }
                    .addOnFailureListener {
                        // Silently handle analysis failures
                        // Bỏ qua lỗi phân tích
                    }
                    .addOnCompleteListener {
                        // MUST close the imageProxy to free camera resources
                        // BẮT BUỘC phải close imageProxy để giải phóng tài nguyên camera
                        imageProxy.close()
                    }
            } catch (e: Exception) {
                // Ensure imageProxy is closed even on exception
                // Đảm bảo imageProxy được đóng ngay cả khi có exception
                imageProxy.close()
            }
        }
    }
}