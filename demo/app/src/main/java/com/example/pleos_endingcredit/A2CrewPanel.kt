package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt

internal class A2CrewPanel(
    context: Context,
    private val crews: List<Pair<String, Int>>,
    onClose: () -> Unit,
    private val onNext: (String?, Int?) -> Unit,
) : ResultDesignSurface(context, 1100f, 824f) {
    private val rows = mutableListOf<CrewRow>()
    private var selectedIndex = 0
    private var nameDialog: AlertDialog? = null

    init {
        tag = "a2.crew"
        val title = if (crews.isEmpty()) "새 크루를 등록해주세요." else "어떤 크루와 여행하시나요?"
        place(label("crew.title", title, 32f, 700, color = INK, typeface = AppTypography.font(context, 700))
            .apply { isAccessibilityHeading = true }, 40f, 40f, 850f, 40f)
        place(label("crew.step", "1 / 2", 24f, color = MUTED, typeface = AppTypography.font(context))
            .apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }, 902f, 46f, 86f, 28f)
        place(travelSvg(context, "crew-935-a2-close.svg").apply {
            tag = "crew.close"
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            contentDescription = "크루 선택 닫기"
            addIconRipple(0x22131417)
            setOnClickListener { onClose() }
        }, 1012f, 36f, 48f, 48f)

        val visibleRows = minOf(crews.size, 3)
        if (visibleRows > 0) {
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            crews.forEachIndexed { index, (name, people) ->
                val row = CrewRow(context, index, name, people) {
                    if (selectedIndex != index) {
                        rows[selectedIndex].setChecked(false)
                        selectedIndex = index
                        rows[selectedIndex].setChecked(true)
                    }
                }
                rows += row
                row.setChecked(index == selectedIndex)
                column.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1))
            }
            val list = object : ScrollView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    rows.forEachIndexed { index, row ->
                        val top = (112f * index * drawingScale).roundToInt()
                        val bottom = ((112f * index + 100f) * drawingScale).roundToInt()
                        val nextTop = (112f * (index + 1) * drawingScale).roundToInt()
                        val params = row.layoutParams as LinearLayout.LayoutParams
                        params.height = (bottom - top).coerceAtLeast(1)
                        params.bottomMargin = if (index < rows.lastIndex) nextTop - bottom else 0
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }.apply {
                tag = "crew.saved.list"
                isFillViewport = false
                isVerticalScrollBarEnabled = crews.size > visibleRows
                overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(column, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
            place(list, 40f, 128f, ROW_WIDTH, 112f * visibleRows - 12f)
        }

        val addY = if (visibleRows == 0) 137f else 128f + 112f * visibleRows
        place(button("crew.custom", "", ::addCrew).apply {
            background = shape(Color.WHITE, 16f, 1f, BORDER)
            contentDescription = "크루 추가하기"
        }, 40f, addY, ROW_WIDTH, 100f)
        place(travelSvg(context, "crew-935-add.svg"), 487f, addY + 38f, 24f, 24f)
        place(label("crew.custom.copy", "추가하기", 26f, 700, color = INK,
            typeface = AppTypography.font(context, 700)), 523f, addY + 33f, 110f, 34f)
        place(button("crew.next", "여행 시작하기", {
            val crew = crews.getOrNull(selectedIndex)
            onNext(crew?.first, crew?.second)
        }, size = 28f, astaSans = true).apply {
            setTextColor(INK)
            background = shape(Color.WHITE, 16f, 1f, INK)
        }, 40f, 692f, ROW_WIDTH, 92f)
    }

    private fun addCrew() {
        val field = EditText(context).apply {
            setSingleLine(true)
            hint = "크루 이름"
            filters = arrayOf(android.text.InputFilter.LengthFilter(30))
        }
        nameDialog = AlertDialog.Builder(context)
            .setTitle("새 크루")
            .setView(field)
            .setNegativeButton("취소", null)
            .setNeutralButton("크루 없이 계속") { _, _ -> onNext(null, null) }
            .setPositiveButton("다음", null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { nameDialog = null }
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = field.text.toString().trim()
                    if (name.isEmpty()) {
                        field.error = "크루 이름을 입력해 주세요."
                    } else {
                        // A matching name uses its saved headcount instead of overwriting it.
                        val count = crews.firstOrNull { it.first == name }?.second
                        dialog.dismiss()
                        onNext(name, count)
                    }
                }
            }
    }

    override fun onDetachedFromWindow() {
        nameDialog?.dismiss()
        super.onDetachedFromWindow()
    }

    private class CrewRow(context: Context, index: Int, name: String, people: Int, select: () -> Unit) :
        ResultDesignSurface(context, ROW_WIDTH, 100f, surfaceRadius = 16f) {
        private val fill = shape(Color.WHITE, 16f)
        private val activeIcon = travelSvg(context, "crew-935-radio-on.svg")
        private val inactiveIcon = travelSvg(context, "crew-935-radio-off.svg")
        private val choice = button("crew.option.${index + 1}", "", select).apply {
            contentDescription = "$name, 탑승 인원 ${people}명"
            background = fill
        }

        init {
            place(choice, 0f, 0f, ROW_WIDTH, 100f)
            place(activeIcon, 23f, 37f, 24f, 24f)
            place(inactiveIcon, 23f, 37f, 24f, 24f)
            place(label("crew.name.${index + 1}", name, 26f, 700, color = INK,
                typeface = AppTypography.font(context, 700)), 71f, 15f, 924f, 34f)
            place(label("crew.people.${index + 1}", "탑승 인원 ${people}명", 24f, color = MUTED,
                typeface = AppTypography.font(context)), 71f, 53f, 924f, 28f)
        }

        fun setChecked(checked: Boolean) {
            choice.isSelected = checked
            activeIcon.visibility = if (checked) VISIBLE else INVISIBLE
            inactiveIcon.visibility = if (checked) INVISIBLE else VISIBLE
            updateFill()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            updateFill()
        }

        private fun updateFill() {
            fill.setColor(if (choice.isSelected) 0x1A0064FF else Color.WHITE)
            fill.setStroke(drawingScale.roundToInt().coerceAtLeast(1), if (choice.isSelected) BLUE else BORDER)
        }
    }

    companion object {
        private const val ROW_WIDTH = 1020f
        private const val BLUE = 0xFF0064FF.toInt()
        private const val INK = 0xFF131417.toInt()
        private const val MUTED = 0xFF686A72.toInt()
        private const val BORDER = 0xFFE0E1E6.toInt()
    }
}
