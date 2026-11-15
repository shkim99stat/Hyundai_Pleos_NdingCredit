package com.example.pleos_endingcredit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

/** Preparation frame shared by both credit choices. */
internal class B2CreditPreparingView(
    context: Context, content: CreditContent, skip: () -> Unit,
) : ConsentDesignSurface(context, 0xFF2B2B2B.toInt(), 1700f) {
    init {
        tag = "b2.preparing"
        if (android.os.Build.VERSION.SDK_INT >= 29) isForceDarkAllowed = false
        label("b2.heading", "크레딧을 준비하고 있어요", 128f, 487.5f, 1444f, 58f,
            40f, 700, 0xFFEDEDED.toInt(), centered = true, noto = true)
        val distance = if (content.distanceKm == null) "" else " · ${content.distanceText}km"
        label("b2.description", content.title + distance, 128f, 565.5f, 1444f, 38f,
            26f, color = 0xFFB3B3B3.toInt(), centered = true, noto = true)
        place(CreditPreparationProgress(context), 640f, 623.5f, 420f, 6f)
        action("b2.skip", "크레딧 건너뛰기", 686f, 1049f, 328f, 89f, skip)
        label("b2.skipLabel", "크레딧 건너뛰기", 686f, 1075f, 328f, 36f,
            24f, 500, 0xFFB3B3B3.toInt(), centered = true, noto = true).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        outline.strokeWidth = 2f * scale
        canvas.drawRoundRect(surface.left + scale, surface.top + scale, surface.right - scale,
            surface.bottom - scale, 23f * scale, 23f * scale, outline)
    }

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444444.toInt()
        style = Paint.Style.STROKE
    }

    companion object { const val DURATION_MILLIS = 2_500L }
}

private class CreditPreparationProgress(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = B2CreditPreparingView.DURATION_MILLIS
        interpolator = LinearInterpolator()
        addUpdateListener { progress = it.animatedValue as Float; invalidate() }
    }

    init {
        tag = "b2.progress"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        paint.color = 0xFF595959.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        paint.color = 0xFFEDEDED.toInt()
        canvas.drawRoundRect(0f, 0f, width * progress, height.toFloat(), radius, radius, paint)
    }
}