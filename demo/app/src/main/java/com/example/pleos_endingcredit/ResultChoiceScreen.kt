package com.example.pleos_endingcredit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** Result mode selection for the legacy trip completion flow. */
internal class ResultChoiceScreen(private val context: Context) {
    private val blue = Color.rgb(53, 120, 250)
    private val ink = Color.rgb(25, 28, 32)
    private val muted = Color.rgb(111, 116, 121)
    private val line = Color.rgb(220, 225, 229)
    private val cardFill = Color.rgb(244, 247, 248)
    private val scale get() = (context.resources.configuration.screenWidthDp / 840f).coerceIn(.85f, 1.8f)
    private val wide get() = context.resources.configuration.screenWidthDp >= 720
    private val touch get() = u(48).coerceAtLeast(dp(48))

    fun create(trip: Trip, selected: ResultMode, select: (ResultMode) -> Unit,
        close: () -> Unit, next: () -> Unit): View = page {
        add(header("${trip.name}이 끝났어요", close))
        val recorded = TripRecordingRepository.get(context).load(trip.id)
        val summary = if (recorded == null) "${TripSample.distanceKm}km · ${TripSample.duration} · 들른 곳 ${TripSample.stops.size}곳" else {
            val minutes = recorded.movingMillis / 60_000
            "${String.format(java.util.Locale.KOREA, "%.1f", recorded.distanceMeters / 1000)}km · ${minutes / 60}시간 ${minutes % 60}분 · 들른 곳 ${recorded.visibleStops.size}곳"
        }
        add(text("$summary · 무엇을 볼까요?", 12, muted), 5)
        val options = LinearLayout(context).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        ResultMode.entries.forEachIndexed { index, mode ->
            val card = column {
                setPadding(u(17), u(20), u(17), u(18))
                background = rounded(cardFill, line, 10)
                addView(choiceArt(mode), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
                add(text(mode.title, 17, bold = true), 20)
                val description = when (mode) {
                    ResultMode.CREDIT -> "다녀온 길을 크레딧으로 보고\n여행 카드를 남겨요."
                    ResultMode.SPLIT -> "이동비를 간편하게\n인원수대로 나눠 보세요."
                    ResultMode.BOTH -> "크레딧과 이동비 정산을\n한 번에 함께 남겨보세요."
                }
                add(text(description, 12, muted).apply { minLines = 2; setLineSpacing(u(3).toFloat(), 1f) }, 9)
                add(button("시작") { select(mode); next() }.apply { contentDescription = "${mode.title} 시작" }, 16, touch)
                isSelected = mode == selected
            }
            val cardHeight = if (wide) (context.resources.configuration.screenHeightDp * .73f).toInt()
                .coerceAtLeast((370 * scale).toInt()) else (365 * scale).toInt()
            options.addView(card, LinearLayout.LayoutParams(if (wide) 0 else MATCH_PARENT, dp(cardHeight), if (wide) 1f else 0f).apply {
                if (index > 0) { if (wide) marginStart = u(12) else topMargin = u(12) }
            })
        }
        add(options, 22)
    }

    private fun choiceArt(mode: ResultMode) = FrameLayout(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (mode != ResultMode.SPLIT) addView(picture(R.drawable.ending_clapper),
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                if (mode == ResultMode.BOTH) { rightMargin = u(35); bottomMargin = u(22) }
            })
        if (mode == ResultMode.SPLIT) addView(picture(R.drawable.ending_coins), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if (mode == ResultMode.BOTH) addView(picture(R.drawable.ending_coins),
            FrameLayout.LayoutParams(u(105), u(125), Gravity.BOTTOM or Gravity.END))
    }

    private fun header(title: String, close: () -> Unit) = row {
        addView(text(title, 22, bold = true).apply { isAccessibilityHeading = true },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(button("×", transparent = true, action = close).apply {
            contentDescription = "닫기"; textSize = 27 * scale
        }, LinearLayout.LayoutParams(touch, touch))
    }

    private fun picture(asset: Int, crop: Boolean = false) = ImageView(context).apply {
        setImageResource(asset)
        scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun text(value: String, size: Int = 14, color: Int = ink, bold: Boolean = false, centered: Boolean = false) = TextView(context).apply {
        text = value; textSize = size * scale; setTextColor(color); includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = if (centered) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
    }

    private fun button(value: String, outlined: Boolean = false, transparent: Boolean = false,
        fill: Int = blue, action: () -> Unit) = MaterialButton(context).apply {
        text = value; isAllCaps = false; textSize = 14 * scale
        minimumHeight = touch; minHeight = touch; minWidth = touch
        elevation = 0f; stateListAnimator = null
        insetTop = 0; insetBottom = 0; cornerRadius = u(if (outlined) 6 else 12)
        setPadding(u(15), u(7), u(15), u(7))
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        backgroundTintList = ColorStateList(states, intArrayOf(Color.rgb(225, 230, 238),
            if (transparent) Color.TRANSPARENT else if (outlined) Color.WHITE else fill))
        setTextColor(ColorStateList(states, intArrayOf(muted, if (outlined || transparent) ink else Color.WHITE)))
        strokeWidth = if (outlined) dp(1) else 0; strokeColor = ColorStateList.valueOf(Color.rgb(170, 178, 189))
        addPressMotion()
        setOnClickListener { action() }
    }

    private fun page(content: LinearLayout.() -> Unit) = ScrollView(context).apply {
        isFillViewport = true; isVerticalScrollBarEnabled = false
        background = rounded(Color.WHITE)
        addView(column { setPadding(u(25), u(18), u(25), u(24)); content() }, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
    private fun column(content: LinearLayout.() -> Unit = {}) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; content() }
    private fun row(content: LinearLayout.() -> Unit = {}) = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; content() }
    private fun LinearLayout.add(view: View, top: Int = 0, height: Int = WRAP_CONTENT) =
        addView(view, LinearLayout.LayoutParams(MATCH_PARENT, height).apply { topMargin = u(top) })
    private fun rounded(fill: Int, stroke: Int? = null, radius: Int = 12) = GradientDrawable().apply {
        setColor(fill); cornerRadius = u(radius).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }
    private fun u(value: Int) = dp((value * scale).toInt())
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
}
