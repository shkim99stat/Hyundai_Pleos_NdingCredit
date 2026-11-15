package com.example.pleos_endingcredit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/** Editable trip setup form from Figma 935:81499 and 935:81743. */
internal class A3TripStartPopupForm(
    context: Context, initialName: String, initialPeople: String,
    nameChanged: (String) -> Unit, peopleChanged: (String) -> Unit, begin: () -> Unit,
    private val titleOnly: Boolean = false,
) : ViewGroup(context) {
    private data class Slot(val view: View, val x: Float, val y: Float, val w: Float, val h: Float)
    private data class Type(val view: ReferenceDesignLabel, val size: Float, val line: Float)
    private val slots = mutableListOf<Slot>()
    private val types = mutableListOf<Type>()
    private val bold = AppTypography.font(context, 700)
    private val stepperButtons = mutableListOf<MaterialButton>()
    private var scale = 1f
    private var speech: RecordingSpeechInput? = null
    private val name = editor("a3.name", initialName, "예: 강릉 1박 2일 여행", false).apply {
        contentDescription = "여행 이름 (선택)"
        filters = arrayOf(InputFilter.LengthFilter(50))
        imeOptions = EditorInfo.IME_ACTION_NEXT
        doAfterTextChanged { nameChanged(it?.toString().orEmpty()) }
    }
    private val people = editor("a3.people", initialPeople.ifBlank { "4" }, "4", true).apply {
        contentDescription = "탑승 인원수"
        filters = arrayOf(InputFilter.LengthFilter(2))
        imeOptions = EditorInfo.IME_ACTION_DONE
        doAfterTextChanged { peopleChanged(it?.toString().orEmpty()) }
    }
    private val peopleHelp = label("a3.peopleHelp", MANUAL_PEOPLE_HELP, 24f, 28f, ACCENT, weight = 500)
    private val action: MaterialButton

    init {
        tag = "a3.popupForm"
        clipChildren = false
        isFocusableInTouchMode = true
        slot(label("a3.title", "이번 여행, 어떻게 부를까요?", 32f, 40f, INK, weight = 700)
            .apply { isAccessibilityHeading = true }, 40f, 40f, 850f, 40f)
        slot(travelSvg(context, "crew-935-a3-close.svg").apply {
            tag = "a3.close"
            contentDescription = "팝업 닫기"
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            addIconRipple(0x22131417)
        }, 1012f, 36f, 48f, 48f)
        slot(label("a3.nameHelp", "비워 두면 '${DemoScenario.TITLE}'로 시작할게요.", 20f, 24f,
            MUTED), 40f, 96f, 950f, 24f)
        slot(label("a3.nameLabel", "제목", 26f, 34f, INK, weight = 700)
            .apply { labelFor = name.id }, 40f, 168f, 948f, 34f)
        slot(name, 40f, 218f, 948f, 48f)

        val microphone = FrameLayout(context).apply {
            tag = "a3.voice"
            isClickable = true
            isFocusable = true
            contentDescription = "여행 이름 음성 입력"
            setOnClickListener {
                speech?.close()
                speech = RecordingSpeechInput(context, { value ->
                    if (isAttachedToWindow && isShown) {
                        name.setText(value.take(50))
                        name.setSelection(name.text?.length ?: 0)
                    }
                }, "여행 이름을 말해 주세요")
                speech?.start()
            }
            addView(travelSvg(context, "crew-935-mic.svg").apply {
                tag = "a3.microphoneIcon"
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(0, 0))
        }
        slot(microphone, 1012f, 220f, 48f, 48f)
        divider("a3.nameDivider", 279f)
        if (!titleOnly) {
            slot(label("a3.peopleLabel", "인원수", 26f, 34f, INK, weight = 700)
                .apply { labelFor = people.id }, 40f, 328f, 740f, 34f)
            slot(label("a3.peopleCaption", "탑승 인원수", 24f, 28f, MUTED),
                40f, 374f, 740f, 28f)
            slot(people, 916f, 340f, 72f, 48f)
            stepper("a3.minus", "−", "인원 줄이기", 820f) { changePeople(-1) }
            stepper("a3.plus", "+", "인원 늘리기", 988f) { changePeople(1) }
            divider("a3.peopleDivider", 439f)
            slot(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ACCENT)
                }
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, 40f, 478f, 24f, 24f)
            slot(label("a3.peopleInfoIcon", "!", 16f, 24f, Color.WHITE, weight = 700).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, 40f, 478f, 24f, 24f)
            slot(peopleHelp, 76f, 476f, 984f, 28f)
        }
        val actionY = if (titleOnly) 348f else 588f
        action = button("a3.begin", "기록 시작하기", ACCENT, begin)
        slot(action, 40f, actionY, 1020f, 92f)
        slot(label("a3.beginLabel", "기록 시작하기", 28f, 36f, Color.WHITE, weight = 700).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener { begin() }
        }, 400f, actionY + 28f, 300f, 36f)
    }

    fun onHostStopped() { speech?.close() }

    override fun onDetachedFromWindow() {
        onHostStopped()
        speech = null
        super.onDetachedFromWindow()
    }

    private fun changePeople(delta: Int) {
        people.setText(((people.text?.toString()?.toIntOrNull() ?: 4) + delta).coerceIn(1, 99).toString())
        people.setSelection(people.text?.length ?: 0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        scale = width / A3TripStartView.POPUP_WIDTH
        val height = resolveSize(px(A3TripStartView.popupHeight(titleOnly)), heightMeasureSpec)
        setMeasuredDimension(width, height)
        types.forEach {
            val paint = Paint().apply { typeface = it.view.typeface; textSize = it.size }
            val fm = paint.fontMetrics
            val baseline = (it.line - fm.descent + fm.ascent) / 2f - fm.ascent
            it.view.setReferenceTypography(it.size, it.line, baseline, scale)
        }
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28f * scale)
        people.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28f * scale)
        stepperButtons.forEach {
            it.cornerRadius = px(12f)
            it.strokeWidth = px(1f).coerceAtLeast(1)
        }
        action.cornerRadius = px(16f)
        slots.forEach { it.view.measure(exact(px(it.w)), exact(px(it.h).coerceAtLeast(1))) }
        val microphone = findViewWithTag<FrameLayout>("a3.voice")
        microphone.getChildAt(0).measure(exact(px(48f)), exact(px(48f)))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        slots.forEach {
            val x = px(it.x)
            val y = px(it.y)
            it.view.layout(x, y, x + it.view.measuredWidth, y + it.view.measuredHeight)
        }
        findViewWithTag<FrameLayout>("a3.voice").getChildAt(0)
            .layout(0, 0, px(48f), px(48f))
    }

    private fun editor(tag: String, value: String, placeholder: String, numeric: Boolean) = AppCompatEditText(context).apply {
        id = View.generateViewId()
        this.tag = tag
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        setSingleLine()
        // A two-digit centred value needs a fixed text width, including before attachment.
        if (numeric) setHorizontallyScrolling(false)
        typeface = if (numeric) bold else AppTypography.font(context)
        gravity = Gravity.CENTER_VERTICAL or if (numeric) Gravity.CENTER_HORIZONTAL else Gravity.START
        includeFontPadding = false
        letterSpacing = 0f
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setTextColor(INK)
        setHintTextColor(MUTED)
        background = null
        setPadding(0, 0, 0, 0)
        hint = placeholder
        setText(value)
    }

    private fun stepper(tag: String, copy: String, description: String, x: Float, change: () -> Unit) {
        val step = button(tag, description, Color.WHITE, change).apply {
            strokeColor = ColorStateList.valueOf(0xFFE0E1E6.toInt())
        }
        stepperButtons += step
        slot(step, x, 328f, 72f, 72f)
        slot(label(tag + "Label", copy, 28f, 36f, MUTED, weight = 700).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener { change() }
        }, x, 346f, 72f, 36f)
    }

    private fun button(tag: String, description: String, fill: Int, click: () -> Unit) = MaterialButton(context).apply {
        this.tag = tag
        contentDescription = description
        backgroundTintList = ColorStateList.valueOf(fill)
        minimumWidth = 0; minWidth = 0; minimumHeight = 0; minHeight = 0
        insetTop = 0; insetBottom = 0
        setPadding(0, 0, 0, 0)
        elevation = 0f; stateListAnimator = null; strokeWidth = 0
        addPressMotion()
        setOnClickListener { click() }
    }

    private fun label(tag: String, copy: String, size: Float, line: Float, color: Int, weight: Int = 400) =
        ReferenceDesignLabel(context).apply {
            this.tag = tag; text = copy; typeface = AppTypography.font(context, weight)
            setTextColor(color); includeFontPadding = false; letterSpacing = 0f
            maxLines = 1; gravity = Gravity.START or Gravity.TOP; setPadding(0, 0, 0, 0)
            types += Type(this, size, line)
        }

    private fun slot(view: View, x: Float, y: Float, w: Float, h: Float) {
        slots += Slot(view, x, y, w, h)
        addView(view)
    }

    private fun divider(tag: String, y: Float) {
        slot(travelSvg(context, "crew-935-divider.svg").apply {
            this.tag = tag
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 40f, y, 1020f, 1f)
    }

    private fun px(value: Float) = (value * scale).roundToInt()
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    companion object {
        private const val ACCENT = 0xFF0064FF.toInt()
        private const val INK = 0xFF131417.toInt()
        private const val MUTED = 0xFF686A72.toInt()
        private const val MANUAL_PEOPLE_HELP = "함께 탄 인원을 확인해 주세요. 다르면 바꿔 주세요."
    }
}
