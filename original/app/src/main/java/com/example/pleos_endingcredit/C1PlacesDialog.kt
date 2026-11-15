package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.min
import kotlin.math.roundToInt

/** The C1-2 draft is committed only by the save button. */
internal class ResultPlacesView(
    context: Context,
    initial: List<ResultPlace>,
    private val dismiss: () -> Unit,
    private val save: (List<ResultPlace>) -> Unit,
    private val record: TripRecording? = null,
) : ResultDesignSurface(context, 1100f, HEIGHT) {
    private val draft = initial.toMutableList()
    private val scroll = C1PlaceScroll(context, 1005f, 6.49f, 108.96f)
    private var selected = draft.firstOrNull()?.id
    private var nameDialog: androidx.appcompat.app.AlertDialog? = null
    private val blue = 0xFF0066FF.toInt()

    init {
        tag = "c1.places.dialog"
        accessibilityPaneTitle = "장소 수정하기"
        place(label("c1.places.editor.title", "장소 수정하기", 34f, 700, 0xFF333333.toInt()),
            44f, 40f, 500f, 41f)
        place(label("c1.places.editor.hint", "이름을 누르면 고르기 · ×로 빼기", 20f, color = 0xFF737373.toInt())
            .apply { gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL },
            751f, 52.5f, 305f, 24f)
        scroll.tag = "c1.places.scroll"
        place(scroll, 44f, 127f, 1005f, 519.15f)
        place(View(context).apply {
            tag = "c1.places.fade"
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00FFFFFF, Color.WHITE))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 0f, 516.96f, 1100f, 129.19f)
        place(button("c1.places.cancel", "그대로 둘게요", dismiss, outlined = true, size = 26f),
            524f, 670.15f, 242f, 75f)
        place(button("c1.places.done", "이대로 저장하기", { save(draft.toList()) }, solid = true, size = 26f)
            .apply { background = shape(blue, 14f) }, 782f, 670.15f, 262f, 71f)
        populate()
    }

    private fun populate() {
        val previousScroll = scroll.scrollY
        val rows = object : ResultDesignSurface(context, 1005f, (draft.size * 135f - 24f).coerceAtLeast(120f)) {
            init {
                if (draft.isEmpty()) {
                    place(label("c1.places.empty", "카드에 표시할 장소가 없어요", color = muted), 0f, 18f, 900f, 40f)
                }
                draft.forEachIndexed { index, stop ->
                    val y = index * 135f
                    val active = stop.id == selected
                    val outline = if (active) blue else 0xFFE0E0E0.toInt()
                    place(box("c1.places.row.${stop.id}", if (active) 0xFFE5EFFF.toInt() else 0xFFF1F1F1.toInt(),
                        10f, 1f, outline), 0f, y, 978f, 111f)
                    place(label("c1.places.name.${stop.id}", stop.name, 32f, 600,
                        typeface = AppTypography.font(context, 600)).apply {
                        isFocusable = true
                        contentDescription = "${stop.name} 고르기"
                        setOnClickListener {
                            selected = stop.id
                            populate()
                            editName(stop)
                        }
                    }, 132.67f, y + 10.55f, 737f, 37.7f)
                    place(label("c1.places.address.${stop.id}", C1Places.address(stop, record), 24f,
                        color = 0xFF6E6E6E.toInt()), 132.34f, y + 60.55f, 740f, 37.7f)
                    val removeY = y + if (index < 2) 33.5f else 39.55f
                    place(label("c1.places.remove.${stop.id}", "×", 26f, 500,
                        if (active) blue else 0xFFA4A4A4.toInt(), centered = true).apply {
                        background = shape(Color.TRANSPARENT, 24f, 2f,
                            if (active) blue else 0xFFC0C0C0.toInt())
                        isFocusable = true
                        contentDescription = "${stop.name} 빼기"
                        setOnClickListener {
                            draft.removeAt(index)
                            if (selected == stop.id) selected = draft.getOrNull(index.coerceAtMost(draft.lastIndex))?.id
                            populate()
                        }
                    }, 899f, removeY, 44f, 44f)
                }
            }
            override fun onDraw(canvas: Canvas) = Unit
        }
        scroll.removeAllViews()
        scroll.addView(rows, FrameLayout.LayoutParams(-1, -2))
        if (previousScroll > 0) scroll.post { scroll.scrollTo(0, previousScroll) }
    }

    private fun editName(stop: ResultPlace) {
        nameDialog?.dismiss()
        val input = EditText(context).apply {
            tag = "c1.places.name.input"
            setText(stop.name)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(80))
            maxLines = 1
            hint = "장소 이름"
        }
        val dialog = MaterialAlertDialogBuilder(context).setTitle("장소 이름 확인").setView(input)
            .setNegativeButton("취소", null).setPositiveButton("저장", null).create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    input.error = "장소 이름을 입력해 주세요"
                    return@setOnClickListener
                }
                val index = draft.indexOfFirst { it.id == stop.id }
                if (index >= 0) draft[index] = stop.copy(name = name, category = "other", needsReview = false)
                populate()
                dialog.dismiss()
            }
        }
        nameDialog = dialog
        dialog.show()
    }

    override fun onDetachedFromWindow() {
        nameDialog?.dismiss()
        nameDialog = null
        super.onDetachedFromWindow()
    }

    companion object { const val HEIGHT = 785f }
}

internal class C1PlacesOverlay(context: Context, private val panel: View, backdrop: View?,
    dismiss: () -> Unit,
) : FrameLayout(context), PopupFadeListener {
    private val blur = PopupBackdropBlur(backdrop)
    private var blurRadius = 0f
    private val bounds = RectF()
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var radius = 24f

    init {
        tag = "c1.places.overlay"
        setBackgroundColor(0x8C14141A.toInt())
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusableInTouchMode = true
        setOnClickListener { dismiss() }
        panel.isClickable = true
        addView(panel)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        val scale = min(width / 1696f, height / 1180f)
        setMeasuredDimension(width, height)
        val cardWidth = (1100f * scale).roundToInt().coerceAtLeast(1)
        val cardHeight = (ResultPlacesView.HEIGHT * scale).roundToInt().coerceAtLeast(1)
        val left = (width - cardWidth) / 2f
        val top = (height - cardHeight) / 2f + 32.5f * scale
        bounds.set(left, top, left + cardWidth, top + cardHeight)
        panel.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY))
        radius = 24f * scale
        paper.setShadowLayer(((8f * scale - .5f) / .57735f).coerceAtLeast(.01f),
            0f, 12f * scale, 0x33000000)
        blurRadius = 10f * scale
        onPopupAlpha(alpha)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val x = bounds.left.roundToInt()
        val y = bounds.top.roundToInt()
        panel.layout(x, y, x + panel.measuredWidth, y + panel.measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(bounds, radius, radius, paper)
    }

    override fun onPopupAlpha(alpha: Float) = blur.update(blurRadius, alpha)

    override fun onDetachedFromWindow() {
        blur.update(0f, 0f)
        super.onDetachedFromWindow()
    }
}
