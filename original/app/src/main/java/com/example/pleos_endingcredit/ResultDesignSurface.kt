package com.example.pleos_endingcredit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.caverock.androidsvg.SVG
import kotlin.math.min
import kotlin.math.roundToInt

/** Source-coordinate native controls, shared by C1 and its two dialogs. */
internal open class ResultDesignSurface(context: Context, private val sourceWidth: Float,
    private val sourceHeight: Float, private val surfaceRadius: Float = 24f) : ViewGroup(context) {
    private data class Slot(val view: View, val x: Float, val y: Float, val w: Float, val h: Float)
    private data class TextStyle(val view: TextView, val size: Float, val weight: Int,
        val typeface: Typeface? = null, val selectedWeight: Int = weight, val astaSans: Boolean = false)
    private data class Shape(val drawable: GradientDrawable, val radius: Float, val stroke: Float,
        val strokeColor: Int, val dashed: Boolean)
    private val slots = mutableListOf<Slot>()
    private val textStyles = mutableListOf<TextStyle>()
    private val shapes = mutableListOf<Shape>()
    protected var drawingScale = 1f
    protected val surface = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val ink = 0xFF222222.toInt()
    protected val muted = 0xFF666666.toInt()

    init {
        setWillNotDraw(false)
        if (android.os.Build.VERSION.SDK_INT >= 29) isForceDarkAllowed = false
    }
    protected fun place(view: View, x: Float, y: Float, w: Float, h: Float): View {
        slots += Slot(view, x, y, w, h); addView(view); return view
    }
    protected fun removeSlot(view: View) {
        slots.removeAll { it.view === view }
        removeView(view)
    }
    protected fun moveSlot(view: View, x: Float, y: Float, w: Float, h: Float) {
        val index = slots.indexOfFirst { it.view === view }
        check(index >= 0) { "View must be placed before moving its slot" }
        slots[index] = Slot(view, x, y, w, h)
        requestLayout()
    }
    protected fun label(id: String, copy: String, size: Float = 26f, weight: Int = 400,
        color: Int = ink, centered: Boolean = false, typeface: Typeface? = null) = ReferenceDesignLabel(context).apply {
        tag = id; text = copy; setTextColor(color); includeFontPadding = false
        setPadding(0, 0, 0, 0); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL or if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
        textStyles += TextStyle(this, size, weight, typeface)
    }
    protected fun button(id: String, copy: String, click: () -> Unit,
        outlined: Boolean = false, solid: Boolean = false, size: Float = 24f,
        weight: Int = 700, selectedWeight: Int = weight, astaSans: Boolean = false) = ReferenceDesignButton(context).apply {
        tag = id; text = copy; contentDescription = copy; setTextColor(if (solid) Color.WHITE else ink)
        includeFontPadding = false; isAllCaps = false; gravity = Gravity.CENTER; maxLines = 1
        setPadding(0, 0, 0, 0); minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        insetTop = 0; insetBottom = 0; elevation = 0f; stateListAnimator = null
        backgroundTintList = null
        background = shape(if (solid) 0xFF333333.toInt() else Color.TRANSPARENT, 14f,
            if (outlined) 2f else 0f, 0xFF999999.toInt())
        rippleColor = ColorStateList.valueOf(0x22333333)
        textStyles += TextStyle(this, size, weight, selectedWeight = selectedWeight, astaSans = astaSans)
        addPressMotion()
        setOnClickListener { if (isEnabled) click() }
    }
    protected fun box(id: String, fill: Int, radius: Float = 0f, stroke: Float = 0f,
        strokeColor: Int = Color.TRANSPARENT, dashed: Boolean = false) = View(context).apply {
        tag = id; background = shape(fill, radius, stroke, strokeColor, dashed)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    protected fun shape(fill: Int, radius: Float, stroke: Float = 0f,
        strokeColor: Int = Color.TRANSPARENT, dashed: Boolean = false) = GradientDrawable().apply {
        setColor(fill); shapes += Shape(this, radius, stroke, strokeColor, dashed)
    }
    protected fun asset(file: String, id: String, directory: String = "c1") = object : View(context) {
        private val picture = assets.getOrPut("$directory/$file") {
            context.assets.open("$directory/$file").use { SVG.getFromInputStream(it) }.renderToPicture()
        }
        init { tag = id; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) { canvas.drawPicture(picture, RectF(0f, 0f, width.toFloat(), height.toFloat())) }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
            (w * sourceHeight / sourceWidth).roundToInt() else MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(w, h)
        drawingScale = min(w / sourceWidth, h / sourceHeight)
        surface.set((w - sourceWidth * drawingScale) / 2f, (h - sourceHeight * drawingScale) / 2f,
            (w + sourceWidth * drawingScale) / 2f, (h + sourceHeight * drawingScale) / 2f)
        val normal = requireNotNull(ResourcesCompat.getFont(context, R.font.noto_sans_kr_regular))
        val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.noto_sans_kr_bold))
        textStyles.forEach {
            val weight = if (it.view.isSelected) it.selectedWeight else it.weight
            it.view.typeface = it.typeface ?: when {
                it.astaSans -> AppTypography.font(context, weight)
                weight >= 700 -> bold
                else -> Typeface.create(normal, weight, false)
            }
            val slot = slots.first { slot -> slot.view === it.view }
            val fm = Paint(it.view.paint).apply { textSize = it.size }.fontMetrics
            val baseline = (slot.h - (fm.descent - fm.ascent)) / 2f - fm.ascent
            it.view.setReferenceTypography(it.size, it.size * 1.45f, baseline, drawingScale)
            it.view.alpha = if (it.view.isEnabled) 1f else .35f
        }
        shapes.forEach {
            it.drawable.cornerRadius = it.radius * drawingScale
            if (it.stroke > 0f) it.drawable.setStroke(px(it.stroke).coerceAtLeast(1), it.strokeColor,
                if (it.dashed) 9 * drawingScale else 0f, if (it.dashed) 6 * drawingScale else 0f)
        }
        slots.forEach { it.view.measure(exact(px(it.w)), exact(px(it.h))) }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        slots.forEach {
            val x = (surface.left + it.x * drawingScale).roundToInt()
            val y = (surface.top + it.y * drawingScale).roundToInt()
            it.view.layout(x, y, x + it.view.measuredWidth, y + it.view.measuredHeight)
        }
    }
    override fun onDraw(canvas: Canvas) {
        paint.color = Color.WHITE; paint.style = Paint.Style.FILL
        canvas.drawRoundRect(surface, surfaceRadius * drawingScale, surfaceRadius * drawingScale, paint)
    }
    private fun px(v: Float) = (v * drawingScale).roundToInt().coerceAtLeast(1)
    private fun exact(v: Int) = MeasureSpec.makeMeasureSpec(v, MeasureSpec.EXACTLY)
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    companion object { private val assets = mutableMapOf<String, Picture>() }
}
