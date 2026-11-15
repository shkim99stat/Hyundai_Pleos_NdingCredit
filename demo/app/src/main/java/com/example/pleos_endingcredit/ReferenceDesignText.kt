package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/** Single-line native controls shaped at the source size before uniform scaling. */
internal interface ReferenceDesignText {
    var designSize: Float
    var drawingScale: Float
    var designBaseline: Float
}

internal class ReferenceDesignLabel(context: Context) : AppCompatTextView(context), ReferenceDesignText {
    override var designSize = 0f
    override var drawingScale = 1f
    override var designBaseline = 0f
    private val drawingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)

    override fun getBaseline() = (designBaseline * drawingScale).roundToInt()
    override fun onDraw(canvas: Canvas) = drawReferenceText(canvas, this, this, drawingPaint)
}

internal class ReferenceDesignButton(context: Context) : MaterialButton(context), ReferenceDesignText {
    override var designSize = 0f
    override var drawingScale = 1f
    override var designBaseline = 0f
    private val drawingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)

    override fun getBaseline() = (designBaseline * drawingScale).roundToInt()
    override fun onDraw(canvas: Canvas) = drawReferenceText(canvas, this, this, drawingPaint)
}

internal fun TextView.setReferenceTypography(size: Float, lineHeight: Float, baseline: Float, scale: Float) {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale)
    setLineHeight((lineHeight * scale).roundToInt())
    (this as ReferenceDesignText).apply {
        designSize = size
        drawingScale = scale
        designBaseline = baseline
    }
}

private fun drawReferenceText(canvas: Canvas, view: TextView, metrics: ReferenceDesignText, paint: TextPaint) {
    if (metrics.designSize <= 0f) return
    paint.set(view.paint)
    paint.textSize = metrics.designSize
    paint.flags = paint.flags or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
    paint.hinting = Paint.HINTING_OFF
    paint.color = view.currentTextColor
    val width = view.width / metrics.drawingScale
    val copy = if (view.ellipsize != null)
        TextUtils.ellipsize(view.text, paint, width, view.ellipsize).toString()
    else view.text.toString()
    val textWidth = paint.measureText(copy)
    val x = when (Gravity.getAbsoluteGravity(view.gravity, view.layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK) {
        Gravity.CENTER_HORIZONTAL -> (width - textWidth) / 2f
        Gravity.END and Gravity.HORIZONTAL_GRAVITY_MASK -> width - textWidth
        else -> 0f
    }
    val saved = canvas.save()
    canvas.scale(metrics.drawingScale, metrics.drawingScale)
    canvas.drawText(copy, x, metrics.designBaseline, paint)
    canvas.restoreToCount(saved)
}
