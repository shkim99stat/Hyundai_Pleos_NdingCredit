package com.example.pleos_endingcredit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** A single 48 dp button when closed. The controls expand over the map. */
internal class DemoControlsView(
    context: Context,
    private val player: DemoPlayback,
    private val review: () -> Unit,
    private val finish: () -> Unit,
    reset: () -> Unit,
    endDemo: () -> Unit,
    private val insertVideo: (() -> Unit)? = null,
) : LinearLayout(context) {
    private val ink = 0xFF222222.toInt()
    private val blue = 0xFF245BC4.toInt()
    private var scenePicker: AlertDialog? = null
    private val status = TextView(context).apply {
        tag = "demo.status"
        textSize = 13f
        typeface = AppTypography.font(context, 600)
        setTextColor(ink)
    }
    private val panel = LinearLayout(context).apply {
        tag = "demo.panel"
        orientation = VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = rounded(Color.WHITE, 12)
        elevation = dp(5).toFloat()
        visibility = GONE
    }
    private val toggle = button("demo.toggle", "시연", Color.WHITE, 0xFF242732.toInt()) {
        if (!collapse()) {
            panel.visibility = VISIBLE
            refresh()
        }
    }
    private val primary = button("demo.review", "장소 확인", Color.WHITE, blue) {
        collapse()
        if (player.finalArrival) finish() else review()
    }
    private val next = button("demo.next", "다음 장소로 출발", Color.WHITE, blue) {
        collapse()
        player.advance()
    }
    private val pause = button("demo.pause", "일시정지") {
        player.togglePause()
        collapse()
    }

    init {
        tag = "demo.console"
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        addView(panel, LayoutParams(dp(220), LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })
        addView(toggle, LayoutParams(dp(48), dp(48)))
        if (insertVideo != null) {
            panel.addView(button("demo.video.insert", "영상 넣기", Color.WHITE, blue) {
                collapse()
                insertVideo.invoke()
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        } else {
            addRecordingActions(reset, endDemo)
        }
        refresh()
    }

    private fun addRecordingActions(reset: () -> Unit, endDemo: () -> Unit) {
        panel.addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(4), dp(2), dp(4), dp(6))
        })
        listOf(primary, next, pause).forEach { action ->
            panel.addView(action, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(4)
            })
        }
        val more = LinearLayout(context)
        val scenes = button("demo.scenes", "장면 선택") {
            collapse()
            scenePicker = AlertDialog.Builder(context).setTitle("시연 장면 선택")
                .setItems(player.legs.map { it.title }.toTypedArray()) { _, index ->
                    if (isAttachedToWindow && isShown) player.seek(index)
                }
                .create()
            scenePicker?.setOnDismissListener { scenePicker = null }
            scenePicker?.show()
        }
        val restart = button("demo.reset", "처음부터") {
            collapse()
            reset()
        }
        val end = button("demo.end", "시연 종료") {
            collapse()
            endDemo()
        }
        listOf(scenes, restart, end).forEach { action ->
            more.addView(action, LayoutParams(0, dp(48), 1f).apply {
                if (action != end) marginEnd = dp(4)
            })
        }
        panel.addView(more, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(4) })
    }

    override fun onDetachedFromWindow() {
        close()
        super.onDetachedFromWindow()
    }

    fun close() {
        collapse()
        scenePicker?.dismiss()
    }

    fun collapse(): Boolean {
        if (panel.visibility != VISIBLE) return false
        panel.visibility = GONE
        toggle.contentDescription = "시연 조작 열기"
        return true
    }

    fun refresh() {
        if (panel.visibility != VISIBLE) {
            toggle.contentDescription = "시연 조작 열기"
            return
        }
        toggle.contentDescription = "시연 조작 닫기"
        if (insertVideo != null) return
        val at = player.virtualAt
        val day = if (at >= DemoScenario.at("15T00:00")) "2일차" else "1일차"
        val time = timeFormat.format(Instant.ofEpochMilli(at).atZone(DemoScenario.zone))
        val motion = when {
            player.arrived -> "정차"
            player.paused -> "일시정지"
            DemoScenario.isNight(at) -> "야간 주행"
            else -> "주행"
        }
        status.text = "$day $time · $motion\n" + player.leg.title
        primary.visibility = if (player.arrived && player.currentStop?.excluded != true) VISIBLE else GONE
        primary.text = when {
            player.finalArrival -> "여행 끝내기"
            player.confirmed -> "방문 수정"
            else -> "장소 확인"
        }
        next.visibility = if (!player.finalArrival && (!player.arrived || player.confirmed)) VISIBLE else GONE
        next.text = when {
            !player.arrived -> "정차로 건너뛰기"
            player.scene == 3 -> "2일차 시작"
            player.scene == if (player.legs === DemoScenario.legs) 5 else 6 -> "귀경길로 출발"
            player.scene == 7 -> "밤길로 서울 출발"
            else -> "다음 장소로 출발"
        }
        pause.visibility = if (player.arrived) GONE else VISIBLE
        pause.text = if (player.paused) "계속 재생" else "일시정지"
    }

    private fun button(
        id: String,
        label: String,
        textColor: Int = ink,
        fill: Int = 0xFFF1F3F6.toInt(),
        action: () -> Unit,
    ) = AppCompatButton(context).apply {
        tag = id
        text = label
        textSize = 13f
        typeface = AppTypography.font(context, 600)
        setTextColor(textColor)
        isAllCaps = false
        gravity = Gravity.CENTER
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(4), 0, dp(4), 0)
        background = RippleDrawable(ColorStateList.valueOf(0x22496797), rounded(fill, 8), null)
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

