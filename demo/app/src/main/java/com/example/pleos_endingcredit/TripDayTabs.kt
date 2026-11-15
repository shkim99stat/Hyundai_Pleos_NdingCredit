package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.widget.PopupMenu

internal class TripDayTabs(
    context: Context,
    tagPrefix: String,
    private val days: Int,
    initialDay: Int,
    private val dateLabel: (Int) -> String,
    private val onDay: (Int) -> Unit,
) : ResultDesignSurface(context, WIDTH, HEIGHT) {
    private val buttons = mutableListOf<ReferenceDesignButton>()
    private val fills = mutableListOf<GradientDrawable>()
    private var selectedDay = initialDay.coerceIn(0, days)
    private var menu: PopupMenu? = null

    init {
        tag = "$tagPrefix.tabs"
        place(box("$tagPrefix.tabs.background", Color.WHITE, 17f, 1f, 0xFFF1F1F1.toInt())
            .apply { elevation = 2f }, 0f, 0f, WIDTH, HEIGHT - 2f)
        val count = (days + 1L).coerceAtMost(3).toInt()
        val tabWidth = 624f / count
        repeat(count) { index ->
            val fill = shape(Color.TRANSPARENT, 17f)
            val tab = button("$tagPrefix.day.$index", "", {
                if (index == 2 && days > 2) openMenu() else select(index)
            }, size = 28f, weight = 400, selectedWeight = 700, astaSans = true).apply {
                background = fill
                outlineProvider = null
                translationZ = 3f
            }
            buttons += tab
            fills += fill
            place(tab, 8f + tabWidth * index, 6f, tabWidth, 72f)
        }
        refresh()
    }

    private fun select(day: Int) {
        selectedDay = day
        refresh()
        onDay(day)
    }

    private fun refresh() {
        buttons.forEachIndexed { index, tab ->
            val number = if (index == 2) selectedDay.coerceAtLeast(2) else index
            tab.text = if (number == 0) "전체" else "${number}일차" + if (index == 2 && days > 2) " ▾" else ""
            tab.contentDescription = tab.text
            tab.isSelected = selectedDay.coerceAtMost(2) == index
            tab.setTextColor(if (tab.isSelected) Color.WHITE else 0xFF44464E.toInt())
            fills[index].setColor(if (tab.isSelected) 0xFF0064FF.toInt() else Color.TRANSPARENT)
        }
        requestLayout()
    }

    private fun openMenu() {
        dismissMenu()
        menu = PopupMenu(context, buttons.last()).apply {
            for (day in 2..days.coerceAtMost(365)) {
                val date = dateLabel(day)
                menu.add(if (date.isBlank()) "${day}일차" else "${day}일차 · $date")
                    .setOnMenuItemClickListener { select(day); true }
            }
            show()
        }
    }

    fun dismissMenu() { menu?.dismiss(); menu = null }

    override fun onDetachedFromWindow() {
        dismissMenu()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) = Unit

    companion object {
        const val WIDTH = 642f
        const val HEIGHT = 86f
    }
}
