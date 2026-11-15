package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.button.MaterialButton
import kotlin.math.min
import kotlin.math.roundToInt

internal class ResultCostsView(context: Context, content: TripResultContent,
    dismiss: () -> Unit, peopleChanged: (Int) -> Unit,
) : ResultDesignSurface(context, 1100f, 702.3f) {
    private var people = content.splitPeople
    init {
        tag = "c1.costs.dialog"; accessibilityPaneTitle = "이동비 내역"
        place(label("c1.costs.title", "이동비 내역", 32f, 700), 44f, 56.6f, 700f, 46.4f)
        place(button("c1.costs.close", "닫기", dismiss).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        }, 950f, 48f, 106f, 64f)
        fun money(value: Int?) = value?.let(TripSample::won) ?: "기록 없음"
        fun row(id: String, name: String, value: String, y: Float) {
            place(label("c1.costs.$id.label", name), 50f, y + 16f, 680f, 38f)
            place(label("c1.costs.$id.value", value, 27f, 700).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL },
                750f, y + 16f, 300f, 39f)
            place(box("c1.costs.$id.line", 0xFFDDDDDD.toInt()), 44f, y + 71f, 1012f, 2f)
        }
        row("energy", if (content.demo) "전기료 · 97.2kWh × 300원" else "전기료", money(content.energy), 136f)
        place(label("c1.costs.energy.note", if (content.demo)
            "486km ÷ 충전 이후 전비 5.0km/kWh · 충전 단가 300원/kWh" else "확인된 전비·충전 단가 기록이 없어요",
            21f, color = muted), 44f, 228.9f, 1012f, 31f)
        row("toll", "예상 통행료 (내비)", money(content.toll), 279.58f)
        row("parking", "주차비", money(content.parking), 372.72f)
        place(label("c1.costs.people.label", "나누는 인원 (운전자 포함)"), 50f, 475.86f, 760f, 38f)
        place(label("c1.costs.people.note", if (content.maxPeople >= 2)
            "함께 탄 ${content.maxPeople}명 중 · 2명 이상, 함께 탄 인원 이하" else "함께 탄 인원이 1명이에요", 20f, color = muted),
            50f, 519.86f, 760f, 38f)
        place(box("c1.costs.stepper", Color.TRANSPARENT, 14f, 3f, 0xFFAAAAAA.toInt()), 869.6f, 481.86f, 180.4f, 67.3f)
        val count = label("c1.costs.people", "", 28f, 700, centered = true)
        val total = label("c1.costs.total", "", 27f, 700).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        lateinit var minus: MaterialButton
        lateinit var plus: MaterialButton
        fun update() {
            count.text = "${people}명"
            total.text = content.total?.let { "${TripSample.won(it)} → 1인 ${TripSample.won(requireNotNull(content.perPerson(people)))}" }
                ?: "이동비 기록 없음"
            minus.isEnabled = people > content.minPeople; plus.isEnabled = people < content.maxPeople
            minus.alpha = if (minus.isEnabled) 1f else .35f; plus.alpha = if (plus.isEnabled) 1f else .35f
            count.contentDescription = "분담 인원 ${people}명"
            requestLayout()
        }
        minus = button("c1.costs.minus", "−", {
            if (people > content.minPeople) { people--; peopleChanged(people); update() }
        }, size = 34f).apply { contentDescription = "분담 인원 줄이기" }
        plus = button("c1.costs.plus", "+", {
            if (people < content.maxPeople) { people++; peopleChanged(people); update() }
        }, size = 34f).apply { contentDescription = "분담 인원 늘리기" }
        place(minus, 870f, 482f, 60f, 67f); place(count, 930f, 482f, 60f, 67f); place(plus, 990f, 482f, 60f, 67f)
        place(box("c1.costs.people.line", 0xFFDDDDDD.toInt()), 44f, 565.16f, 1012f, 2f)
        place(label("c1.costs.total.label", "합계", 27f, 700), 50f, 603f, 160f, 40f)
        place(total, 350f, 603f, 700f, 40f)
        update()
    }
}

/** App-only dim and centred card for the cost details. */
internal class ResultDialogOverlay(context: Context, panel: View, sourceHeight: Float, dismiss: () -> Unit) : FrameLayout(context) {
    private val bounds = RectF()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var radius = 24f
    private val panelHeight = sourceHeight
    private val card = panel.apply { isClickable = true }
    init {
        tag = "c1.dialog.overlay"; setBackgroundColor(0x59000000); setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null); isClickable = true; isFocusableInTouchMode = true
        setOnClickListener { dismiss() }; addView(card)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val h = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(w, h)
        val scale = min(w / 1696f, h / 1180f)
        val cw = (1100 * scale).roundToInt().coerceAtLeast(1)
        val ch = (panelHeight * scale).roundToInt().coerceAtLeast(1)
        card.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY))
        bounds.set((w-cw)/2f, (h-ch)/2f, (w+cw)/2f, (h+ch)/2f)
        radius = 24 * scale; shadow.setShadowLayer((16 * scale).coerceAtLeast(1f), 0f, 12 * scale, 0x33000000)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        card.layout(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.left.roundToInt()+card.measuredWidth,
            bounds.top.roundToInt()+card.measuredHeight)
    }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawRoundRect(bounds, radius, radius, shadow) }
}
