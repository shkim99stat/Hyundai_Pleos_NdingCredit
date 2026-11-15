package com.example.pleos_endingcredit

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.caverock.androidsvg.SVG
import kotlin.math.min
import kotlin.math.roundToInt

internal const val CONSENT_BLUE = 0xFF0066FF.toInt()
internal const val CONSENT_INK = 0xFF212121.toInt()
internal const val CONSENT_MUTED = 0xFF686A72.toInt()

/** Scales A0 design coordinates inside the actual app window. */
@SuppressLint("ViewConstructor")
internal open class ConsentDesignSurface(
    context: Context,
    color: Int,
    private val sourceWidth: Float = 1696f,
    private val assetDirectory: String = "a0",
) : ViewGroup(context) {
    private data class Slot(val view: View, val x: Float, val y: Float, val width: Float, val height: Float)
    private data class Label(val view: TextView, val size: Float, val line: Float, val baseline: Float)
    private data class Body(val view: TextView, val size: Float, val line: Float)
    private data class Shape(val background: GradientDrawable, val radius: Float, val stroke: Int?)
    private val slots = mutableListOf<Slot>()
    private val labels = mutableListOf<Label>()
    private val bodies = mutableListOf<Body>()
    private val shapes = mutableListOf<Shape>()
    private val buttons = mutableListOf<ReferenceDesignButton>()
    private val pictures = mutableMapOf<String, Picture>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    protected val surface = RectF()
    protected var scale = 1f
        private set

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun place(view: View, x: Float, y: Float, width: Float, height: Float) {
        slots += Slot(view, x, y, width, height)
        addView(view)
    }

    fun label(tag: String, text: String, x: Float, y: Float, w: Float, h: Float, size: Float,
        weight: Int = 400, color: Int = CONSENT_INK, centered: Boolean = false,
        noto: Boolean = false): TextView = ReferenceDesignLabel(context).apply {
        this.tag = tag
        this.text = text
        typeface = font(noto, weight)
        setTextColor(color)
        includeFontPadding = false
        letterSpacing = 0f
        maxLines = 1
        gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
        setPadding(0, 0, 0, 0)
        labels += Label(this, size, h, baseline(typeface, size, h, noto))
        place(this, x, y, w, h)
    }

    fun body(text: String, x: Float, y: Float, w: Float, h: Float, size: Float, line: Float) {
        val view = TextView(context).apply {
            this.text = text
            typeface = font(noto = true, weight = 400)
            setTextColor(CONSENT_INK)
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
        }
        bodies += Body(view, size, line)
        place(view, x, y, w, h)
    }

    fun button(tag: String, text: String, x: Float, y: Float, w: Float, h: Float,
        size: Float, fill: Int, noto: Boolean = false, weight: Int = 700,
        textColor: Int = Color.WHITE, stroke: Int? = null, lineHeight: Float = size * 1.45f,
        onClick: (() -> Unit)? = null
    ): ReferenceDesignButton = ReferenceDesignButton(context).apply {
        this.tag = tag
        this.text = text
        typeface = font(noto, weight)
        setTextColor(textColor)
        isAllCaps = false
        letterSpacing = 0f
        includeFontPadding = false
        gravity = Gravity.CENTER
        maxLines = 1
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        elevation = 0f
        stateListAnimator = null
        setPadding(0, 0, 0, 0)
        backgroundTintList = ColorStateList.valueOf(fill)
        strokeWidth = 0
        if (stroke != null) {
            strokeColor = ColorStateList.valueOf(stroke)
            strokeWidth = 1
        }
        isEnabled = onClick != null
        if (onClick != null) {
            addPressMotion()
            setOnClickListener { if (isEnabled) onClick() }
        }
        labels += Label(this, size, lineHeight, baseline(typeface, size, h, noto))
        buttons += this
        place(this, x, y, w, h)
    }

    fun box(x: Float, y: Float, w: Float, h: Float, fill: Int, radius: Float = 0f, stroke: Int? = null) {
        val drawable = GradientDrawable().apply { setColor(fill) }
        shapes += Shape(drawable, radius, stroke)
        place(View(context).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            background = drawable
        }, x, y, w, h)
    }

    fun action(tag: String, name: String, x: Float, y: Float, w: Float, h: Float, onClick: () -> Unit) {
        place(ImageButton(context).apply {
            this.tag = tag
            contentDescription = name
            background = null
            setOnClickListener { onClick() }
        }, x, y, w, h)
    }

    fun icon(name: String, x: Float, y: Float, w: Float, h: Float): View {
        val picture = pictures.getOrPut(name) {
            SVG.getFromAsset(context.assets, "$assetDirectory/$name").renderToPicture()
        }
        val view = object : View(context) {
            private val bounds = RectF()
            init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                bounds.set(0f, 0f, w.toFloat(), h.toFloat())
            }
            override fun onDraw(canvas: Canvas) = canvas.drawPicture(picture, bounds)
        }
        place(view, x, y, w, h)
        return view
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = resolveSize((width * 1184f / sourceWidth).roundToInt(), heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(width, height)
        scale = min(width / sourceWidth, height / 1184f)
        val w = sourceWidth * scale
        val h = 1184f * scale
        surface.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        labels.forEach { it.view.setReferenceTypography(it.size, it.line, it.baseline, scale) }
        bodies.forEach {
            it.view.setTextSize(TypedValue.COMPLEX_UNIT_PX, it.size * scale)
            it.view.setLineHeight(px(it.line))
        }
        shapes.forEach {
            it.background.cornerRadius = it.radius * scale
            it.stroke?.let { color -> it.background.setStroke(px(1f), color) }
        }
        buttons.forEach { it.cornerRadius = px(16f) }
        slots.forEach { it.view.measure(exact(px(it.width)), exact(px(it.height))) }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        slots.forEach {
            val x = (surface.left + it.x * scale).roundToInt()
            val y = (surface.top + it.y * scale).roundToInt()
            it.view.layout(x, y, x + it.view.measuredWidth, y + it.view.measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(surface, 24 * scale, 24 * scale, fill)
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    private fun px(value: Float) = (value * scale).roundToInt().coerceAtLeast(1)
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)

    private fun font(noto: Boolean, weight: Int): Typeface {
        if (!noto) return AppTypography.font(context, weight)
        val resource = if (weight >= 700) R.font.noto_sans_kr_bold else R.font.noto_sans_kr_regular
        return Typeface.create(requireNotNull(ResourcesCompat.getFont(context, resource)), weight, false)
    }

    private fun baseline(typeface: Typeface, size: Float, height: Float, noto: Boolean): Float {
        if (!noto) return height / 2f + .3555f * size
        val metrics = Paint().apply { this.typeface = typeface; textSize = size }.fontMetrics
        return (height - metrics.ascent - metrics.descent) / 2f
    }
}
