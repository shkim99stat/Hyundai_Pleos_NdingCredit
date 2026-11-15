package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.View
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ArchiveDateRange(val start: LocalDate, val end: LocalDate) {
    fun includes(date: LocalDate?): Boolean = date != null && date in start..end
}

internal class F1_1CalendarView(
    context: Context,
    initial: ArchiveDateRange?,
    private val tripDates: Set<LocalDate>,
    private val count: (ArchiveDateRange?) -> Int,
    onClose: () -> Unit,
    onApply: (ArchiveDateRange?) -> Unit,
) : ResultDesignSurface(context, 1000f, 1080f, 28f) {
    private var range = initial
    private var first: LocalDate? = null
    private val today = LocalDate.now()
    private var month = YearMonth.from(initial?.end ?: today)
        set(value) {
            if (field != value) {
                field = value
                datesByCell = monthDates(value)
            }
        }
    private var datesByCell = monthDates(month)
    private val dark = 0xFF2B2E33.toInt()
    private val gray = 0xFF8C9199.toInt()
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val monthLabel = label("archive.calendar.month", "", 30f, 700, dark)
    private val selection = label("archive.calendar.selection", "", 22f, 500, gray)
    private val apply = button("archive.calendar.apply", "", { onApply(currentRange()) }, solid = true, size = 28f)
    private val days = List(42) { i ->
        button("archive.calendar.day.$i", "", { select(datesByCell[i]) }, size = 30f, weight = 400, selectedWeight = 700)
            .apply { letterSpacing = 0f }
    }

    init {
        tag = "archive.calendar"
        setTag(R.id.ending_credit_screen_id, "F1-1")
        place(label("archive.calendar.title", "날짜로 찾기", 34f, 700, dark), 56f, 37f, 750f, 57f)
        place(button("archive.calendar.close", "×", onClose, size = 40f, weight = 400)
            .apply { setTextColor(gray); contentDescription = "날짜 선택 닫기" }, 895f, 37f, 68f, 57f)
        val shortcuts = listOf("전체", "최근 3개월", "올해", "작년")
        val widths = listOf(93f, 151f, 93f, 93f)
        var x = 56f
        shortcuts.forEachIndexed { index, name ->
            place(button("archive.calendar.preset.$index", name, { preset(index) }, size = 22f, weight = 500)
                .apply { letterSpacing = 0f; setTextColor(dark); background = shape(0xFFEDF0F2.toInt(), 29f) },
                x, 116f, widths[index], 58f)
            x += widths[index] + 14f
        }
        place(monthLabel, 56f, 197f, 550f, 55f)
        place(button("archive.calendar.prev", "‹", { move(-1) }, size = 40f, weight = 500)
            .apply { contentDescription = "이전 달" }, 806f, 194f, 68f, 61f)
        place(button("archive.calendar.next", "›", { move(1) }, size = 40f, weight = 500)
            .apply { contentDescription = "다음 달" }, 892f, 194f, 68f, 61f)
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, day ->
            place(label("archive.calendar.week.$i", day, 22f, 500, weekdayColor(i, gray), centered = true),
                GRID_X + i * CELL, 261f, CELL, 43f)
        }
        days.forEachIndexed { i, view ->
            val cellX = GRID_X + (i % 7) * CELL
            val cellY = GRID_Y + (i / 7) * ROW
            place(view, cellX + (CELL - DAY) / 2, cellY, DAY, DAY)
        }
        place(selection, GRID_X, 810f, 888f, 43f)
        place(button("archive.calendar.reset", "초기화", { preset(0) }, size = 28f, weight = 500)
            .apply { letterSpacing = 0f; setTextColor(dark); background = shape(0xFFEDF0F2.toInt(), 20f) },
            56f, 944f, 303.8f, 88f)
        apply.letterSpacing = 0f
        apply.background = shape(dark, 20f)
        place(apply, 379.8f, 944f, 564.2f, 88f)
        update()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = canvas.save()
        canvas.translate(surface.left, surface.top)
        canvas.scale(drawingScale, drawingScale)
        val record = currentRange()
        if (record != null && record.start != record.end) {
            rangePaint.color = 0xFFDBDEE5.toInt()
            for (row in 0..5) {
                val columns = (0..6).filter { record.includes(datesByCell[row * 7 + it]) }
                if (columns.isEmpty()) continue
                val left = columns.first()
                val right = columns.last()
                val x1 = GRID_X + CELL * (left + if (datesByCell[row * 7 + left] == record.start) .5f else 0f)
                val x2 = GRID_X + CELL * (right + if (datesByCell[row * 7 + right] == record.end) .5f else 1f)
                val y = GRID_Y + row * ROW
                canvas.drawRect(x1, y, x2, y + DAY, rangePaint)
            }
        }
        rangePaint.color = dark
        days.indices.forEach { index ->
            val date = datesByCell[index]
            if (date in tripDates && date != record?.start && date != record?.end) {
                canvas.drawCircle(GRID_X + (index % 7 + .5f) * CELL, GRID_Y + (index / 7) * ROW + 64f, 4f, rangePaint)
            }
        }
        canvas.restoreToCount(state)
    }

    private fun monthDates(month: YearMonth): List<LocalDate?> {
        val firstColumn = month.atDay(1).dayOfWeek.value % 7
        val monthLength = month.lengthOfMonth()
        return List(42) { index ->
            val day = index - firstColumn + 1
            if (day in 1..monthLength) month.atDay(day) else null
        }
    }

    private fun currentRange() = range ?: first?.let { ArchiveDateRange(it, it) }

    private fun select(date: LocalDate?) {
        if (date == null) return
        val anchor = first
        if (anchor == null) {
            first = date
            range = null
        } else {
            range = ArchiveDateRange(minOf(anchor, date), maxOf(anchor, date))
            first = null
        }
        update()
    }

    private fun preset(index: Int) {
        first = null
        val thisYear = today.withDayOfYear(1)
        range = when (index) {
            1 -> ArchiveDateRange(today.minusMonths(3), today)
            2 -> ArchiveDateRange(thisYear, today)
            3 -> ArchiveDateRange(thisYear.minusYears(1), thisYear.minusDays(1))
            else -> null
        }
        month = YearMonth.from(range?.end ?: today)
        update()
    }

    private fun move(delta: Long) {
        month = month.plusMonths(delta)
        update()
    }

    private fun update() {
        val active = currentRange()
        monthLabel.text = month.format(MONTH)
        days.forEachIndexed { i, view ->
            val date = datesByCell[i]
            val endpoint = date != null && (date == active?.start || date == active?.end)
            view.visibility = if (date == null) INVISIBLE else VISIBLE
            view.text = date?.dayOfMonth?.toString().orEmpty()
            view.contentDescription = date?.format(DATE).orEmpty()
            view.isSelected = endpoint
            view.background = if (endpoint) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dark)
            } else null
            view.setTextColor(if (endpoint) Color.WHITE else weekdayColor(i % 7, dark))
        }
        val dates = active?.let {
            val end = if (it.end.year == it.start.year) it.end.format(SHORT_DATE) else it.end.format(DATE)
            if (it.start == it.end) it.start.format(DATE) else "${it.start.format(DATE)} – $end"
        }
        selection.text = listOfNotNull("시작일과 끝일을 차례로 눌러요", dates).joinToString("   ·   ")
        moveSlot(selection, GRID_X, if (datesByCell[35] != null) 899f else 810f, 888f, 43f)
        invalidate()
        apply.text = "${count(active)}편 보기"
        apply.contentDescription = apply.text
    }

    private fun weekdayColor(index: Int, weekday: Int) = when (index) {
        0 -> 0xFFD94040.toInt()
        6 -> 0xFF3366CC.toInt()
        else -> weekday
    }

    private companion object {
        const val GRID_X = 56f
        const val GRID_Y = 330f
        const val ROW = 96f
        const val DAY = 80f
        const val CELL = 888f / 7
        val MONTH = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREA)
        val SHORT_DATE = DateTimeFormatter.ofPattern("MM.dd", Locale.KOREA)
        val DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA)
    }
}
