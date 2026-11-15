package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** C1 map only: full-height map, with frosted horizontal bands under the information.
 * Text/buttons are siblings drawn afterwards and never enter the blur capture.
 * A small cached bitmap keeps this compatible with API 28 and avoids full-size allocations.
 */
internal class ResultMapBackdrop(context: Context, map: View) : FrameLayout(context) {
    private var blurred: Bitmap? = null
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)
    private var dirty = true
    private var capturing = false
    private var lastCapture = 0L
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frost = Paint().apply { color = 0xADFFFFFF.toInt() }
    private val destination = Rect()
    private val outline = GradientDrawable().apply { setColor(0xFFF0F0F0.toInt()) }

    init {
        tag = "c1.map.backdrop"
        background = outline; clipToOutline = true
        addView(map, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        outline.cornerRadius = 16f * w / DESIGN_WIDTH
        dirty = true
    }

    override fun onDescendantInvalidated(child: View, target: View) {
        if (!capturing) dirty = true
        super.onDescendantInvalidated(child, target)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            refreshBlur()
        }
        return handled
    }

    fun refreshBlur() { dirty = true; invalidate() }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (dirty) {
            val elapsed = SystemClock.uptimeMillis() - lastCapture
            if (blurred == null || elapsed >= 120L) captureBlur()
            else postInvalidateDelayed(120L - elapsed)
        }
        val image = blurred ?: return
        destination.set(0, 0, width, height)
        // The title/date end at y=164.25; the lower band starts above the y=821 distance.
        drawBand(canvas, image, 0, (TOP_END * height / DESIGN_HEIGHT).roundToInt())
        drawBand(canvas, image, (BOTTOM_START * height / DESIGN_HEIGHT).roundToInt(), height)
    }

    private fun drawBand(canvas: Canvas, image: Bitmap, top: Int, bottom: Int) {
        val checkpoint = canvas.save()
        canvas.clipRect(0, top, width, bottom)
        canvas.drawBitmap(image, null, destination, imagePaint)
        canvas.drawRect(0f, top.toFloat(), width.toFloat(), bottom.toFloat(), frost)
        canvas.restoreToCount(checkpoint)
    }

    private fun captureBlur() {
        val w = (width / 4).coerceIn(1, 320)
        val h = (height.toFloat() * w / width).roundToInt().coerceAtLeast(1)
        var bitmap = blurred
        if (bitmap == null || bitmap.width != w || bitmap.height != h) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            blurred = bitmap
            pixels = IntArray(w * h); scratch = IntArray(w * h)
        }
        capturing = true
        try {
            bitmap.eraseColor(0xFFF0F0F0.toInt())
            val capture = Canvas(bitmap)
            capture.scale(w.toFloat() / width, h.toFloat() / height)
            super.dispatchDraw(capture)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val radius = (12f * w / DESIGN_WIDTH).roundToInt().coerceAtLeast(1)
            repeat(3) {
                boxBlur(pixels, scratch, w, h, radius, horizontal = true)
                boxBlur(scratch, pixels, w, h, radius, horizontal = false)
            }
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            dirty = false; lastCapture = SystemClock.uptimeMillis()
        } finally { capturing = false }
    }

    /** Separable sliding windows: work grows with pixel count, not radius squared. */
    private fun boxBlur(input: IntArray, output: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val count = 2 * radius + 1
        fun index(line: Int, position: Int) = if (horizontal) line * w + position else position * w + line
        repeat(lines) { line ->
            var red = 0; var green = 0; var blue = 0
            for (offset in -radius..radius) {
                val color = input[index(line, offset.coerceIn(0, length - 1))]
                red += Color.red(color); green += Color.green(color); blue += Color.blue(color)
            }
            repeat(length) { position ->
                output[index(line, position)] = Color.rgb(red / count, green / count, blue / count)
                val leaving = input[index(line, (position - radius).coerceIn(0, length - 1))]
                val entering = input[index(line, (position + radius + 1).coerceIn(0, length - 1))]
                red += Color.red(entering) - Color.red(leaving)
                green += Color.green(entering) - Color.green(leaving)
                blue += Color.blue(entering) - Color.blue(leaving)
            }
        }
    }

    override fun onDetachedFromWindow() {
        blurred?.recycle(); blurred = null; pixels = IntArray(0); scratch = IntArray(0)
        dirty = true
        super.onDetachedFromWindow()
    }

    companion object {
        private const val DESIGN_WIDTH = 874f
        private const val DESIGN_HEIGHT = 1180f
        const val TOP_END = 184f
        const val BOTTOM_START = 790f
    }
}
