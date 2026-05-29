package com.pda_scanner_lmhung.barcode_scanner_plugin

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View

/**
 * ScannerOverlayView - Custom View that draws the barcode scanning overlay.
 *
 * This view is drawn ON TOP of the camera preview and creates the visual
 * scanning UI including the transparent frame, blur edges, corners, and
 * animated scan line.
 *
 * Lớp View tùy chỉnh vẽ lớp phủ quét mã vạch LÊN TRÊN camera preview.
 *
 * Features / Tính năng:
 * - Semi-transparent blue overlay (#2196F3 at 53% alpha) / Lớp phủ xanh dương mờ
 * - Transparent scanning frame in center (camera shows through) / Khung quét trong suốt
 * - Gradient blur/feather edges around the frame / Viền mờ gradient xung quanh khung
 * - Green corner markers (L-shapes) / Đánh dấu góc màu xanh lá
 * - Animated green scan line with gradient / Vạch quét động với gradient xanh
 * - Hint text below the frame / Chữ hướng dẫn dưới khung
 *
 * Performance / Hiệu năng:
 * - Zero allocations in onDraw() (all paints pre-allocated) / Không cấp phát trong onDraw
 * - postInvalidateOnAnimation() syncs with vsync / Đồng bộ với vsync
 * - clipOutRect for hardware-accelerated overlay cutout
 * - Early return for off-screen scan line / Bỏ qua vạch quét ngoài màn hình
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ========================================================================
    // Frame dimensions / Kích thước khung quét
    // ========================================================================

    /**
     * The scanning frame rectangle (centered on screen).
     * This is the transparent area where the camera preview shows through.
     *
     * Hình chữ nhật khung quét (căn giữa màn hình).
     * Đây là vùng trong suốt cho phép nhìn thấy camera preview.
     */
    var frameRect: RectF = RectF()
        private set

    // ========================================================================
    // Colors / Màu sắc
    // ========================================================================

    /** Base blue color without alpha / Màu xanh dương cơ bản (không alpha) */
    private val overlayBaseColor = Color.parseColor("#2196F3")

    /** Current overlay color with alpha (semi-transparent blue) / Màu xanh dương mờ */
    private var overlayColor = Color.parseColor("#882196F3")

    // ========================================================================
    // Pre-allocated Paints (zero allocation in onDraw) / Paint được cấp phát trước
    // ========================================================================

    /** Dark blue overlay covering the ENTIRE screen except the frame / Lớp phủ xanh toàn màn hình trừ khung */
    private val overlayPaint = Paint().apply {
        color = overlayColor
        style = Paint.Style.FILL
    }

    /** White border around the scanning frame / Viền trắng quanh khung quét */
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    /** Green corner L-shape markers / Đánh dấu góc hình chữ L màu xanh lá */
    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    /** Animated scanning line (green gradient) / Vạch quét động (gradient xanh) */
    private val scanLinePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /** Hint text below the frame / Chữ hướng dẫn dưới khung */
    private val hintPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    // ========================================================================
    // Blur edge paints (4 directional + 4 corners) / Paint viền mờ
    // ========================================================================

    /** Gradient paints for the 4 edges (top, bottom, left, right) / Paint gradient 4 cạnh */
    private val topBlurPaint = Paint()
    private val bottomBlurPaint = Paint()
    private val leftBlurPaint = Paint()
    private val rightBlurPaint = Paint()

    /** Radial gradient paints for the 4 corners / Paint gradient radial 4 góc */
    private val cornerBlurPaints = Array(4) { Paint() }

    // ========================================================================
    // Reusable geometry objects / Đối tượng hình học dùng lại
    // ========================================================================

    /** Reusable RectF to avoid allocation / RectF dùng lại để tránh cấp phát */
    private val tempRect = RectF()

    /** Reusable Path for clipOutRect fallback on API < 26 / Path dùng lại cho API cũ */
    private val clipPath = Path()

    // ========================================================================
    // Animation state / Trạng thái hoạt ảnh
    // ========================================================================

    /** Width of the blur/gradient edge in pixels / Độ rộng viền mờ (pixel) */
    private var blurEdgeWidth = 80f

    /** Current Y position of the scan line / Vị trí Y hiện tại của vạch quét */
    private var scanLineY = 0f

    /** Scan line direction (1 = down, -1 = up) / Hướng vạch quét (1 = xuống, -1 = lên) */
    private var scanLineDirection = 1f

    /** Whether the scan line animation is running / Hoạt ảnh vạch quét đang chạy? */
    private var isAnimating = true

    /** Length of corner markers in pixels / Chiều dài đánh dấu góc (pixel) */
    private val cornerLength = 60f

    /** Gradient for the scan line / Gradient cho vạch quét */
    private var scanLineGradient: LinearGradient? = null

    /**
     * Init block: ensures onDraw is called even when the view is invisible.
     * By default, Android may skip drawing for views that don't have a background.
     * This flag forces the system to call onDraw() every frame.
     *
     * Đảm bảo onDraw luôn được gọi ngay cả khi view không có background.
     */
    init {
        setWillNotDraw(false)
    }

    // ========================================================================
    // Frame setup / Thiết lập khung quét
    // ========================================================================

    /**
     * Calculate and set the scanning frame rectangle based on preview size.
     *
     * The frame is centered on screen with:
     * - Width: 75% of screen width (max 700px) / Chiều rộng: 75% màn hình (tối đa 700px)
     * - Height: 45% of frame width (landscape barcode ratio) / Chiều cao: 45% chiều rộng khung
     * - Blur edge: 6% of frame width (clamped 40-120px) / Viền mờ: 6% chiều rộng khung
     *
     * Also creates the scan line gradient and blur edge shaders.
     *
     * Tính toán và thiết lập hình chữ nhật khung quét dựa trên kích thước preview.
     *
     * @param previewWidth Width of the camera preview / Chiều rộng camera preview
     * @param previewHeight Height of the camera preview / Chiều cao camera preview
     */
    fun updateFrameRect(previewWidth: Int, previewHeight: Int) {
        val frameWidth = (previewWidth * 0.75f).coerceAtMost(700f)
        val frameHeight = frameWidth * 0.45f

        val centerX = previewWidth / 2f
        val centerY = previewHeight / 2f

        frameRect = RectF(
            centerX - frameWidth / 2f,
            centerY - frameHeight / 2f,
            centerX + frameWidth / 2f,
            centerY + frameHeight / 2f
        )

        scanLineY = frameRect.top

        // Create gradient for animated scan line (green, fades at edges)
        // Tạo gradient cho vạch quét (màu xanh, mờ dần ở 2 đầu)
        scanLineGradient = LinearGradient(
            frameRect.left, 0f, frameRect.right, 0f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.parseColor("#CC00E676"),
                Color.parseColor("#FF00E676"),
                Color.parseColor("#CC00E676"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )
        scanLinePaint.shader = scanLineGradient

        // Blur edge width: 6% of frame width, clamped between 40-120px
        // Độ rộng viền mờ: 6% chiều rộng khung, giới hạn 40-120px
        blurEdgeWidth = (frameWidth * 0.06f).coerceIn(40f, 120f)

        // Rebuild all gradient shaders for blur edges
        // Xây dựng lại tất cả shader gradient cho viền mờ
        buildBlurShaders()

        invalidate()
    }

    // ========================================================================
    // Blur shader builder / Xây dựng shader viền mờ
    // ========================================================================

    /**
     * Build (or rebuild) the gradient shaders for the blur/feather edges.
     *
     * Creates 4 LinearGradients for the edges and 4 RadialGradients for the corners.
     * Each gradient transitions from TRANSPARENT (at the frame edge) to overlayColor (outward).
     *
     * This creates a smooth "feathered" transition from the transparent frame
     * to the dark blue overlay, simulating a Gaussian blur effect.
     *
     * Xây dựng (hoặc xây lại) shader gradient cho viền mờ.
     * Tạo 4 LinearGradient cho các cạnh và 4 RadialGradient cho các góc.
     */
    private fun buildBlurShaders() {
        val b = blurEdgeWidth
        val l = frameRect.left
        val t = frameRect.top
        val r = frameRect.right
        val btm = frameRect.bottom

        // Top edge: transparent at frame → blue outward / Cạnh trên: trong suốt ở khung → xanh ra ngoài
        topBlurPaint.shader = LinearGradient(
            0f, t, 0f, t - b,
            intArrayOf(Color.TRANSPARENT, overlayColor),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )

        // Bottom edge: transparent at frame → blue outward / Cạnh dưới
        bottomBlurPaint.shader = LinearGradient(
            0f, btm, 0f, btm + b,
            intArrayOf(Color.TRANSPARENT, overlayColor),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )

        // Left edge: transparent at frame → blue outward / Cạnh trái
        leftBlurPaint.shader = LinearGradient(
            l, 0f, l - b, 0f,
            intArrayOf(Color.TRANSPARENT, overlayColor),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )

        // Right edge: transparent at frame → blue outward / Cạnh phải
        rightBlurPaint.shader = LinearGradient(
            r, 0f, r + b, 0f,
            intArrayOf(Color.TRANSPARENT, overlayColor),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )

        // Corner transitions: radial for smooth diagonal corners / Chuyển tiếp góc: radial
        val cornerColors = intArrayOf(Color.TRANSPARENT, overlayColor, overlayColor)
        val cornerPositions = floatArrayOf(0f, 0.5f, 1f)

        // [0]=TL, [1]=TR, [2]=BL, [3]=BR
        cornerBlurPaints[0].shader = RadialGradient(l, t, b, cornerColors, cornerPositions, Shader.TileMode.CLAMP)
        cornerBlurPaints[1].shader = RadialGradient(r, t, b, cornerColors, cornerPositions, Shader.TileMode.CLAMP)
        cornerBlurPaints[2].shader = RadialGradient(l, btm, b, cornerColors, cornerPositions, Shader.TileMode.CLAMP)
        cornerBlurPaints[3].shader = RadialGradient(r, btm, b, cornerColors, cornerPositions, Shader.TileMode.CLAMP)
    }

    // ========================================================================
    // onDraw - The main rendering method / Phương thức render chính
    // ========================================================================

    /**
     * Draw the scanning overlay.
     *
     * The key technique / Kỹ thuật chính:
     * - clipOutRect() removes the frame area from the canvas clip
     * - The overlay paint is only applied OUTSIDE the frame
     * - The frame area remains completely transparent → camera shows through
     * - After clip restore, border + corners + scan line are drawn inside
     *
     * Draw order / Thứ tự vẽ:
     * 1. Save canvas state
     * 2. Clip OUT the frame rectangle / Cắt bỏ khung khỏi vùng vẽ
     * 3. Draw dark blue overlay (only outside) / Vẽ lớp phủ xanh (chỉ bên ngoài)
     * 4. Draw blur/gradient edges (outside) / Vẽ viền mờ (bên ngoài)
     * 5. Restore canvas / Khôi phục canvas
     * 6. Draw white border / Vẽ viền trắng
     * 7. Draw corner markers / Vẽ đánh dấu góc
     * 8. Draw animated scan line / Vẽ vạch quét động
     * 9. Draw hint text / Vẽ chữ hướng dẫn
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (frameRect.isEmpty) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Step 1: Save canvas state - we'll modify the clip region
        // Lưu trạng thái canvas - chúng ta sẽ thay đổi vùng clip
        val saveCount = canvas.save()

        // Step 2: Clip OUT the frame so overlay only paints outside
        // Cắt bỏ khung quét khỏi vùng vẽ
        clipOutFrame(canvas)

        // Step 3: Draw dark blue overlay (only outside the frame)
        // Vẽ lớp phủ xanh dương (chỉ bên ngoài khung)
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        // Step 4: Draw blur/gradient edges (naturally outside the frame)
        // Vẽ viền mờ gradient (tự động nằm ngoài khung)
        drawBlurEdges(canvas)

        // Step 5: Restore canvas - remove the clipOutRect
        // Khôi phục canvas - xóa clipOutRect
        canvas.restoreToCount(saveCount)

        // Step 6-9: Draw elements inside/over the frame
        // Vẽ các phần tử trong/trên khung
        canvas.drawRect(frameRect, borderPaint)         // 6. White border
        drawCorners(canvas)                              // 7. Corner markers
        if (isAnimating) drawScanLine(canvas)            // 8. Scan line
        canvas.drawText(                                 // 9. Hint text
            "Đặt mã vạch vào khung để quét",
            frameRect.centerX(),
            frameRect.bottom + 80f,
            hintPaint
        )
    }

    // ========================================================================
    // Clip operations / Thao tác clip
    // ========================================================================

    /**
     * Clip the frame rectangle OUT of the canvas.
     *
     * After this call, all drawing operations will NOT affect the pixel area
     * inside the frame. This is how the transparent hole is created:
     * the camera preview below this overlay view shows through untouched.
     *
     * Implementation / Cài đặt:
     * - API 26+ (Oreo): canvas.clipOutRect() - native hardware-accelerated
     * - Below API 26: Path with INVERSE_EVEN_ODD fill type (fallback)
     *
     * Cắt bỏ hình chữ nhật khung quét khỏi canvas.
     */
    private fun clipOutFrame(canvas: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Native clipOutRect - hardware accelerated / Tăng tốc phần cứng
            canvas.clipOutRect(frameRect)
        } else {
            // Fallback: Path with inverse fill / Dùng Path với fill ngược
            clipPath.reset()
            clipPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            clipPath.addRect(frameRect, Path.Direction.CCW)
            clipPath.setFillType(Path.FillType.INVERSE_EVEN_ODD)
            canvas.clipPath(clipPath)
        }
    }

    // ========================================================================
    // Blur edge drawing / Vẽ viền mờ
    // ========================================================================

    /**
     * Draw the gradient blur/feather edges around the frame.
     *
     * Draws 4 rectangles (top, bottom, left, right) with linear gradients
     * and 4 corner patches with radial gradients.
     *
     * All paints and shaders are pre-allocated - zero allocations in this method.
     *
     * Vẽ viền mờ gradient xung quanh khung quét.
     * Sử dụng pre-allocated paints và shaders - không cấp phát.
     */
    private fun drawBlurEdges(canvas: Canvas) {
        val b = blurEdgeWidth
        val l = frameRect.left
        val t = frameRect.top
        val r = frameRect.right
        val btm = frameRect.bottom

        // Edge rectangles with linear gradients / Hình chữ nhật cạnh với linear gradient
        tempRect.set(l, t - b, r, t)
        canvas.drawRect(tempRect, topBlurPaint)

        tempRect.set(l, btm, r, btm + b)
        canvas.drawRect(tempRect, bottomBlurPaint)

        tempRect.set(l - b, t - b, l, btm + b)
        canvas.drawRect(tempRect, leftBlurPaint)

        tempRect.set(r, t - b, r + b, btm + b)
        canvas.drawRect(tempRect, rightBlurPaint)

        // Corner patches with radial gradients / Góc với radial gradient
        val cp = cornerBlurPaints
        tempRect.set(l - b, t - b, l, t); canvas.drawRect(tempRect, cp[0])  // TL
        tempRect.set(r, t - b, r + b, t); canvas.drawRect(tempRect, cp[1])  // TR
        tempRect.set(l - b, btm, l, btm + b); canvas.drawRect(tempRect, cp[2])  // BL
        tempRect.set(r, btm, r + b, btm + b); canvas.drawRect(tempRect, cp[3])  // BR
    }

    // ========================================================================
    // Corner markers / Đánh dấu góc
    // ========================================================================

    /**
     * Draw 4 green L-shaped corner markers at the frame corners.
     * Vẽ 4 đánh dấu góc hình chữ L màu xanh lá tại 4 góc khung.
     */
    private fun drawCorners(canvas: Canvas) {
        val l = frameRect.left
        val t = frameRect.top
        val r = frameRect.right
        val b = frameRect.bottom
        val len = cornerLength

        // Top-left / Góc trên-trái
        canvas.drawLine(l, t, l + len, t, cornerPaint)
        canvas.drawLine(l, t, l, t + len, cornerPaint)
        // Top-right / Góc trên-phải
        canvas.drawLine(r, t, r - len, t, cornerPaint)
        canvas.drawLine(r, t, r, t + len, cornerPaint)
        // Bottom-left / Góc dưới-trái
        canvas.drawLine(l, b, l + len, b, cornerPaint)
        canvas.drawLine(l, b, l, b - len, cornerPaint)
        // Bottom-right / Góc dưới-phải
        canvas.drawLine(r, b, r - len, b, cornerPaint)
        canvas.drawLine(r, b, r, b - len, cornerPaint)
    }

    // ========================================================================
    // Scan line / Vạch quét
    // ========================================================================

    /**
     * Draw the animated green scan line.
     *
     * The scan line is a horizontal rectangle with a green gradient.
     * It moves up and down within the frame area.
     *
     * Optimization: Early return if the scan line is outside the visible
     * frame area (performance improvement for edge cases).
     *
     * Vẽ vạch quét động màu xanh lá.
     * Hình chữ nhật ngang với gradient xanh, di chuyển lên xuống trong khung.
     * Tối ưu: bỏ qua nếu vạch quét nằm ngoài vùng hiển thị.
     */
    private fun drawScanLine(canvas: Canvas) {
        val lineHeight = 4f
        val scanTop = scanLineY - lineHeight / 2f
        val scanBottom = scanLineY + lineHeight / 2f

        // Skip if scan line is outside the frame (performance optimization)
        // Bỏ qua nếu vạch quét nằm ngoài khung (tối ưu hiệu năng)
        if (scanBottom < frameRect.top || scanTop > frameRect.bottom) return

        canvas.drawRect(
            frameRect.left + 10f, scanTop,
            frameRect.right - 10f, scanBottom,
            scanLinePaint
        )
    }

    // ========================================================================
    // Animation methods / Phương thức hoạt ảnh
    // ========================================================================

    /**
     * Update the scan line position for animation.
     *
     * Called by ValueAnimator from CameraScanActivity.
     * The scan line bounces between the top and bottom of the frame.
     *
     * Uses postInvalidateOnAnimation() to trigger redraw on the next
     * vsync signal, which is more efficient than invalidate().
     *
     * Cập nhật vị trí vạch quét cho hoạt ảnh.
     * Vạch quét di chuyển lên xuống giữa 2 mép khung (bouncing).
     * Dùng postInvalidateOnAnimation() để đồng bộ với vsync.
     */
    fun updateScanLine() {
        if (!isAnimating || frameRect.isEmpty) return

        val speed = 6f
        scanLineY += speed * scanLineDirection

        // Reverse direction at frame boundaries / Đảo hướng tại biên khung
        if (scanLineY >= frameRect.bottom) {
            scanLineDirection = -1f
            scanLineY = frameRect.bottom
        } else if (scanLineY <= frameRect.top) {
            scanLineDirection = 1f
            scanLineY = frameRect.top
        }

        // Schedule redraw on next vsync / Lên lịch vẽ lại ở vsync tiếp theo
        postInvalidateOnAnimation()
    }

    /**
     * Start the scan line animation (reset to top).
     * Khởi động hoạt ảnh vạch quét (đặt lại vị trí đầu).
     */
    fun startAnimation() {
        isAnimating = true
        scanLineY = frameRect.top
        scanLineDirection = 1f
    }

    /**
     * Stop the scan line animation.
     * Dừng hoạt ảnh vạch quét.
     */
    fun stopAnimation() {
        isAnimating = false
        postInvalidateOnAnimation()
    }

    /**
     * Change the overlay alpha value while keeping the blue color.
     *
     * This allows dynamically adjusting the overlay darkness without
     * changing the blue hue. The blur shaders are rebuilt with the
     * new color.
     *
     * Thay đổi độ trong suốt của lớp phủ nhưng giữ màu xanh dương.
     * Các shader viền mờ được xây dựng lại với màu mới.
     *
     * @param alpha The new alpha value (0-255) / Giá trị alpha mới (0-255)
     */
    fun setOverlayAlpha(alpha: Int) {
        overlayColor = Color.argb(
            alpha,
            Color.red(overlayBaseColor),
            Color.green(overlayBaseColor),
            Color.blue(overlayBaseColor)
        )
        overlayPaint.color = overlayColor
        buildBlurShaders()
        postInvalidateOnAnimation()
    }
}