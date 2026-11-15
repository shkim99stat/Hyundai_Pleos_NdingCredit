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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import kotlin.math.min

internal class WireframeViews(private val context: Context) {
    private val resultChoice = ResultChoiceScreen(context)

    private enum class Action { SOLID, OUTLINE, TEXT, ON_DARK }

    private val ink = Color.rgb(32, 32, 32)
    private val dim = Color.rgb(88, 88, 88)
    private val border = Color.rgb(209, 209, 209)
    private val paper = Color.WHITE
    private val tile = Color.rgb(235, 235, 235)
    private val charcoal = Color.rgb(39, 39, 39)
    private val white = Color.WHITE
    private val mutedWhite = Color.rgb(212, 212, 212)
    private val padding get() = context.resources.getDimensionPixelSize(R.dimen.wireframe_padding)
    private val touch get() = context.resources.getDimensionPixelSize(R.dimen.wireframe_touch)
    private val mediaHeight get() = context.resources.getDimensionPixelSize(R.dimen.wireframe_media_height)
    private val textSize get() = context.resources.getDimension(R.dimen.wireframe_text) / scaledUnit()
    private val titleSize get() = context.resources.getDimension(R.dimen.wireframe_title) / scaledUnit()

    fun consent(onAccept: () -> Unit) = A0ConsentView(context, onAccept)

    fun home(start: () -> Unit, archive: () -> Unit) =
        A2HomeView(context, start, archive)

    fun start(initialName: String, initialPeople: String, initialDate: String,
        nameChanged: (String) -> Unit, peopleChanged: (String) -> Unit, dateChanged: (String) -> Unit,
        begin: () -> Unit) =
        A3TripStartView(context, initialName, initialPeople, initialDate, nameChanged, peopleChanged, dateChanged, begin)

    fun drivingPreview() = page(backgroundColor = tile) {
        elastic()
        add(label("주행 중에는 조작할 수 없어요.", centered = true, color = dim, bold = true))
        add(label("정차하면 여행 기록을 확인할 수 있어요.", centered = true, color = dim), 8)
        elastic()
    }

    fun choice(trip: Trip, selected: ResultMode, select: (ResultMode) -> Unit,
        close: () -> Unit, next: () -> Unit) = resultChoice.create(trip, selected, select, close, next)

    fun preparing(trip: Trip, skip: () -> Unit) = B2CreditPreparingView(
        context, CreditContent(trip, TripRecordingRepository.get(context).load(trip.id)), skip,
    )

    fun credit(
        trip: Trip, history: List<Trip>, frame: Int, elapsedMillis: Long, muted: Boolean, paused: Boolean,
        onTime: (Long) -> Unit, onChapter: (Int) -> Unit, onComplete: () -> Unit,
        onMute: (Boolean) -> Unit, onPause: (Boolean) -> Unit,
    ): B3CreditView {
        val index = history.indexOfFirst { it.id == trip.id }
        val tripNumber = if (index >= 0) history.size - index else null
        return B3CreditView(context,
            CreditContent(trip, TripRecordingRepository.get(context).load(trip.id), tripNumber),
            frame, elapsedMillis, muted, paused, onTime, onChapter, onComplete, onMute, onPause)
    }

    fun result(
        trip: Trip, close: () -> Unit, costs: () -> Unit,
        card: () -> Unit, replay: () -> Unit, editPlaces: () -> Unit,
        removePlace: (String) -> Unit,
        selectedDay: Int,
        onDay: (Int) -> Unit,
    ): View = C1TripResultView(context,
        TripResultContent(trip, TripRecordingRepository.get(context).load(trip.id)), close, costs, editPlaces,
        replay, card, removePlace, selectedDay, onDay)
    fun yearReview(year: Int, trips: List<Trip>, frame: Int, close: () -> Unit,
        previous: () -> Unit, next: () -> Unit) = darkPage {
        add(header("연말 결산  ${frame + 1} / $YEAR_FRAME_COUNT", "닫기", close, dark = true))
        elastic()
        when (frame) {
            0 -> add(darkHeadline("${year}년, 우리 차가 달린 이야기"))
            1 -> {
                val distance = trips.size * TripSample.distanceKm
                add(darkHeadline("여행 ${trips.size}번"))
                add(darkNote("함께 달린 거리 $distance km"), 10)
            }
            else -> add(darkHeadline("가장 멀리 함께한 하루"))
        }
        elastic()
        add(actionStrip(
            button("이전", Action.ON_DARK, enabled = frame > 0, onClick = previous),
            button(if (frame == YEAR_FRAME_COUNT - 1) "도감으로" else "다음", Action.ON_DARK, onClick = next),
        ), 16)
    }

    fun confirm(title: String, body: String, no: String, yes: String, dismiss: () -> Unit,
        accept: () -> Unit) = panel(title, dismiss) {
        add(label(body, color = dim), 12)
        add(actionStrip(button(no, Action.OUTLINE, onClick = dismiss), button(yes, onClick = accept)), 18)
    }

    fun costs(trip: Trip, dismiss: () -> Unit, peopleChanged: (Int) -> Unit) =
        ResultCostsView(context, TripResultContent(trip, TripRecordingRepository.get(context).load(trip.id)), dismiss, peopleChanged)
    fun cardPreview(trip: Trip, dismiss: () -> Unit, share: () -> Unit, replay: () -> Unit) =
        panel("여행 카드 미리보기", dismiss) {
            add(label(trip.name, bold = true), 14)
            add(photo(DemoContent.coverResource(trip), "${trip.name} 여행 카드"), 10, mediaHeight)
            add(label("${trip.createdOn} · ${TripSample.distanceKm} km", color = dim), 10)
            add(button("QR 공유 미리보기", Action.OUTLINE, onClick = share), 14)
            add(button("크레딧 다시 보기", Action.OUTLINE, onClick = replay), 10)
        }

    fun sharePreview(trip: Trip, dismiss: () -> Unit) = panel("동승자 공유", dismiss) {
        val recording = TripRecordingRepository.get(context).load(trip.id)
        center(ImageView(context).apply {
            tag = "share.qr"
            setImageBitmap(TripShare.qr(trip, recording))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "${trip.name} 여행 요약 QR 코드"
        }, 280, top = 16, height = dp(280))
        add(label(trip.name, bold = true, centered = true), 14)
        add(label("휴대폰으로 스캔하면 여행 요약을 확인할 수 있어요.", color = dim, centered = true), 8)
        add(label(TripShare.summary(trip, recording), color = dim, centered = true), 12)
    }

    fun overlay(panel: View, dismiss: () -> Unit) = FrameLayout(context).apply {
        setBackgroundColor(0x88000000.toInt())
        isClickable = true
        setOnClickListener { dismiss() }
        addView(ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            pad(18)
            addView(FrameLayout(context).apply {
                addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
            }, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun panel(title: String, dismiss: () -> Unit, content: LinearLayout.() -> Unit): View =
        limitWidth(column {
            background = shape(paper, radius = 14)
            isClickable = true
            pad(20)
            add(header(title, "닫기", dismiss))
            content()
        }, 900)

    private fun header(title: String, action: String? = null, onAction: () -> Unit = {},
        dark: Boolean = false): View = row {
        val heading = label(title, titleSize, bold = true, color = if (dark) white else ink)
        heading.isAccessibilityHeading = true
        addView(heading, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        action?.let {
            addView(button(it, if (dark) Action.ON_DARK else Action.TEXT, onClick = onAction),
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(10) })
        }
    }

    private fun button(
        title: String,
        action: Action = Action.SOLID,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) = MaterialButton(context).apply {
        text = title
        isAllCaps = false
        textSize = this@WireframeViews.textSize
        minimumHeight = touch
        minHeight = touch
        minWidth = touch
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(8)
        val fill = when (action) {
            Action.SOLID -> charcoal
            Action.OUTLINE -> paper
            Action.TEXT -> Color.TRANSPARENT
            Action.ON_DARK -> Color.rgb(60, 60, 60)
        }
        val disabled = if (action == Action.ON_DARK) charcoal else tile
        val foreground = if (action == Action.SOLID || action == Action.ON_DARK) white else ink
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        backgroundTintList = ColorStateList(states, intArrayOf(disabled, fill))
        setTextColor(ColorStateList(states, intArrayOf(if (action == Action.ON_DARK) mutedWhite else dim, foreground)))
        strokeWidth = if (action == Action.OUTLINE || action == Action.ON_DARK) dp(1) else 0
        strokeColor = ColorStateList.valueOf(if (action == Action.ON_DARK) dim else border)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        isEnabled = enabled
        setOnClickListener { onClick() }
    }

    private fun actionStrip(vararg buttons: View) = HorizontalScrollView(context).apply {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        addView(row {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            buttons.forEachIndexed { index, button ->
                addView(button, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    if (index > 0) marginStart = dp(8)
                })
            }
        })
    }

    private fun darkHeadline(value: String) = label(value, titleSize, bold = true, centered = true, color = white)
    private fun darkNote(value: String) = label(value, color = mutedWhite, centered = true)

    private fun photo(resource: Int, description: String) = ImageView(context).apply {
        setImageResource(resource)
        scaleType = ImageView.ScaleType.CENTER_CROP
        contentDescription = description
        background = shape(tile, radius = 12)
        clipToOutline = true
    }

    private fun label(value: String, size: Float = textSize, bold: Boolean = false,
        centered: Boolean = false, color: Int = ink) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = if (centered) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
    }

    private fun page(backgroundColor: Int = paper, content: LinearLayout.() -> Unit) =
        ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            background = shape(backgroundColor, radius = 12)
            addView(column {
                setPadding(padding, padding, padding, padding)
                isFocusableInTouchMode = true
                content()
            }, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

    private fun darkPage(content: LinearLayout.() -> Unit) = page(charcoal, content)

    private fun column(init: LinearLayout.() -> Unit = {}) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        init()
    }

    private fun row(init: LinearLayout.() -> Unit = {}) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        init()
    }

    private fun LinearLayout.center(view: View, maxWidth: Int, top: Int = 0, height: Int = WRAP_CONTENT) {
        addView(limitWidth(view, maxWidth, height != WRAP_CONTENT), LinearLayout.LayoutParams(MATCH_PARENT, height).apply {
            topMargin = dp(top)
            gravity = Gravity.CENTER_HORIZONTAL
        })
    }

    private fun limitWidth(view: View, maxWidth: Int, fillHeight: Boolean = false) = object : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = View.MeasureSpec.getSize(widthMeasureSpec)
            val width = if (available == 0) dp(maxWidth) else min(available, dp(maxWidth))
            super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightMeasureSpec)
        }
    }.apply { addView(view, FrameLayout.LayoutParams(MATCH_PARENT, if (fillHeight) MATCH_PARENT else WRAP_CONTENT, Gravity.CENTER)) }

    private fun LinearLayout.elastic() = addView(Space(context), LinearLayout.LayoutParams(1, 0, 1f))

    private fun LinearLayout.add(view: View, top: Int = 0, height: Int = WRAP_CONTENT) {
        addView(view, LinearLayout.LayoutParams(MATCH_PARENT, height).apply { topMargin = dp(top) })
    }

    private fun View.pad(all: Int) = dp(all).let { setPadding(it, it, it, it) }

    private fun shape(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
    }

    private fun scaledUnit(): Float = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP, 1f, context.resources.displayMetrics,
    )

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
}
