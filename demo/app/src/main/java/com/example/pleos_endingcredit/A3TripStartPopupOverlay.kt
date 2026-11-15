package com.example.pleos_endingcredit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min
import kotlin.math.roundToInt

/** Figma 935:81499 / 935:81743: the modal shell, with the existing A3 form inside.
 * Its dim layer belongs only to the app surface, outside Pleos Driving View/GNB.
 */
@SuppressLint("ViewConstructor")
internal class A3TripStartPopupOverlay(context: Context, form: View, backdrop: View? = null,
    private val titleOnly: Boolean = false, dismiss: () -> Unit) : FrameLayout(context), PopupFadeListener {
    private val blur = PopupBackdropBlur(backdrop)
    private val bounds = RectF()
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shape = GradientDrawable().apply { setColor(Color.WHITE) }
    private val card = FrameLayout(context).apply {
        tag = "trip.start.card"
        background = shape
        clipToOutline = true
        isClickable = true
        accessibilityPaneTitle = "이번 여행, 어떻게 부를까요?"
        addView(form, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    private var scale = 1f

    init {
        tag = "trip.start.overlay"
        setBackgroundColor(0x7A131417)
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusableInTouchMode = true
        setOnClickListener { dismiss() }
        form.findViewWithTag<View>("a3.close")?.setOnClickListener { dismiss() }
        addView(card)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(width, height)
        scale = width / 1700f
        val margin = (24f * resources.displayMetrics.density).roundToInt()
        val cardWidth = min((A3TripStartView.POPUP_WIDTH * scale).roundToInt(),
            (width - 2 * margin).coerceAtLeast(1))
        val desiredCardHeight = (A3TripStartView.popupHeight(titleOnly) * scale).roundToInt()
        val cardHeight = min(desiredCardHeight,
            (height - 2 * margin).coerceAtLeast(1))
        card.measure(exact(cardWidth), exact(cardHeight))
        val x = ((width - cardWidth) / 2f).roundToInt()
        val centerOffsetY = if (cardHeight == desiredCardHeight) 1f * scale else 0f
        val y = ((height - cardHeight) / 2f + centerOffsetY).roundToInt().coerceAtLeast(0)
        bounds.set(x.toFloat(), y.toFloat(), (x + cardWidth).toFloat(), (y + cardHeight).toFloat())
        shape.cornerRadius = 24f * scale
        // CSS/Figma blur 16 -> sigma 8; Android's legacy radius conversion.
        paper.setShadowLayer(((8f * scale - .5f) / .57735f).coerceAtLeast(.01f),
            0f, 12f * scale, 0x33000000)
        onPopupAlpha(alpha)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        card.layout(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(bounds, 24f * scale, 24f * scale, paper)
    }

    override fun onPopupAlpha(alpha: Float) = blur.update(10f * scale, alpha)

    override fun onDetachedFromWindow() {
        blur.update(0f, 0f)
        super.onDetachedFromWindow()
    }

    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)
}
