package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.roundToInt

/** A leftward drag reveals the delete action. The action itself requires a tap. */
internal class C1SwipeRow(
    context: Context,
    private val place: ResultPlace,
    private val foreground: View,
    private val onReveal: (C1SwipeRow) -> Unit,
    remove: (String) -> Unit,
) : FrameLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragStartOffset = 0f
    private var dragging = false
    private var revealed = false
    private val removeBackground = GradientDrawable().apply { setColor(0xFFFF3B30.toInt()) }
    private val delete = TextView(context).apply {
        tag = "c1.swipe.delete.${place.id}"
        text = "×"
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = removeBackground
        contentDescription = "${place.name} 삭제"
        visibility = INVISIBLE
        isFocusable = true
        setOnClickListener { if (revealed) remove(place.id) }
    }

    init {
        tag = "c1.swipe.row.${place.id}"
        isClickable = true
        foreground.setBackgroundColor(Color.WHITE)
        addView(delete, LayoutParams(0, 0, Gravity.RIGHT or Gravity.CENTER_VERTICAL))
        addView(foreground, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ViewCompat.addAccessibilityAction(this, "삭제 버튼 표시") { _, _ ->
            onReveal(this)
            settle(true)
            true
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val scale = MeasureSpec.getSize(widthMeasureSpec) / DESIGN_WIDTH
        val params = delete.layoutParams
        params.width = (112 * scale).roundToInt().coerceAtLeast(1)
        params.height = (112 * scale).roundToInt().coerceAtLeast(1)
        removeBackground.cornerRadius = 10 * scale
        delete.setTextSize(TypedValue.COMPLEX_UNIT_PX, 40 * scale)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(event)
            MotionEvent.ACTION_MOVE -> if (canDrag(event)) {
                startDrag()
                return true
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(event)
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && canDrag(event)) startDrag()
                if (dragging) {
                    foreground.translationX = (dragStartOffset + event.x - downX)
                        .coerceIn(-delete.width.toFloat(), 0f)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) settle(foreground.translationX <= -delete.width * .4f)
                else performClick()
            }
            MotionEvent.ACTION_CANCEL -> if (dragging) settle(revealed)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (revealed) settle(false)
        return true
    }

    fun close() = settle(false)

    private fun begin(event: MotionEvent) {
        downX = event.x
        downY = event.y
        dragStartOffset = foreground.translationX
        dragging = false
    }

    private fun canDrag(event: MotionEvent): Boolean {
        val dx = event.x - downX
        return abs(dx) > touchSlop && abs(dx) > abs(event.y - downY) && (dx < 0 || revealed)
    }

    private fun startDrag() {
        if (dragging) return
        dragging = true
        foreground.animate().cancel()
        onReveal(this)
        delete.visibility = VISIBLE
        parent.requestDisallowInterceptTouchEvent(true)
    }

    private fun settle(open: Boolean) {
        dragging = false
        revealed = open
        foreground.animate().cancel()
        if (open) delete.visibility = VISIBLE
        foreground.animate()
            .translationX(if (open) -delete.width.toFloat() else 0f)
            .setDuration(150)
            .withEndAction { if (!revealed && !dragging) delete.visibility = INVISIBLE }
            .start()
    }

    companion object { const val DESIGN_WIDTH = 642f }
}
