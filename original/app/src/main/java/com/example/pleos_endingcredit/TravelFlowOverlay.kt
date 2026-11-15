package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** Centered overlay used by A2 crew selection and the F1-1 catalog calendar. */
internal class TravelFlowOverlay(context: Context, panel: View, private val sourceWidth: Float,
    private val sourceHeight: Float, dim: Boolean, private val backdrop: View? = null,
    onClose: () -> Unit) : FrameLayout(context), PopupFadeListener {
    private val blur = PopupBackdropBlur(backdrop)
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var scale = 1f

    init {
        setBackgroundColor(when {
            backdrop != null -> 0x7A131417
            dim -> 0x88000000.toInt()
            else -> Color.TRANSPARENT
        })
        if (backdrop != null) setWillNotDraw(false)
        setOnClickListener { onClose() }
        panel.isClickable = true
        addView(panel, LayoutParams(1, 1, Gravity.CENTER))
    }

    override fun onMeasure(w: Int, h: Int) {
        scale = minOf(MeasureSpec.getSize(w) / 1700f, MeasureSpec.getSize(h) / 1184f)
        getChildAt(0).layoutParams.apply {
            width = (sourceWidth * scale).toInt().coerceAtLeast(1)
            height = (sourceHeight * scale).toInt().coerceAtLeast(1)
        }
        super.onMeasure(w, h)
        if (backdrop != null) {
            paper.setShadowLayer(((8f * scale - .5f) / .57735f).coerceAtLeast(.01f),
                0f, 12f * scale, 0x33000000)
            onPopupAlpha(alpha)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (backdrop != null) {
            val panel = getChildAt(0)
            canvas.drawRoundRect(panel.left.toFloat(), panel.top.toFloat(), panel.right.toFloat(),
                panel.bottom.toFloat(), 24f * scale, 24f * scale, paper)
        }
    }

    override fun onPopupAlpha(alpha: Float) = blur.update(10f * scale, alpha)

    override fun onDetachedFromWindow() {
        blur.update(0f, 0f)
        super.onDetachedFromWindow()
    }
}
