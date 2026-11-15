package com.example.pleos_endingcredit

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A3's application window, in the supplied JSON's 1700 x 1184 Figma pixels.
 * The (840, 92) screen origin excludes the Pleos driving view and system bars.
 * Native inputs retain their width and scroll into view when the IME opens.
 * This surface owns its typography, including the reference's Noto action.
 */
internal class A3TripStartView @JvmOverloads constructor(
    context: Context,
    initialName: String = "",
    initialPeople: String = "",
    initialDate: String = "",
    nameChanged: (String) -> Unit = {},
    peopleChanged: (String) -> Unit = {},
    dateChanged: (String) -> Unit = {},
    begin: () -> Unit = {},
    presentation: Presentation = Presentation.PAGE,
    titleOnly: Boolean = false,
) : ScrollView(context) {
    enum class Presentation { PAGE, POPUP }
    init {
        tag = "a3.scroll"
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        val form = if (presentation == Presentation.POPUP) A3TripStartPopupForm(context,
            initialName, initialPeople, nameChanged, peopleChanged, begin, titleOnly)
        else Surface(context, initialName, initialPeople, initialDate,
            nameChanged, peopleChanged, dateChanged, begin)
        addView(form, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun onHostStopped() { (getChildAt(0) as? A3TripStartPopupForm)?.onHostStopped() }

    private class Surface(
        context: Context,
        initialName: String,
        initialPeople: String,
        initialDate: String,
        nameChanged: (String) -> Unit,
        peopleChanged: (String) -> Unit,
        dateChanged: (String) -> Unit,
        begin: () -> Unit,
    ) : ViewGroup(context) {
        private val designWidth = 1700f
        private val designHeight = 1184f
        private data class Placement(
            val view: View, val x: Float, val y: Float, val width: Float, val height: Float,
            val params: FrameLayout.LayoutParams = FrameLayout.LayoutParams(0, 0),
        )
        private data class Typography(val view: TextView, val size: Float, val lineHeight: Float,
            val baseline: Float = 0f)
        private data class Input(val view: TextView, val paddingLeft: Float, val paddingRight: Float)
        private data class Decoration(val drawable: GradientDrawable, val radius: Float, val stroke: Float, val color: Int)

        private val ink = Color.rgb(19, 20, 23)
        private val muted = Color.rgb(104, 106, 114)
        private val hintColor = Color.rgb(164, 168, 174)
        private val outline = Color.rgb(199, 201, 206)
        private val placements = mutableListOf<Placement>()
        private val typography = mutableListOf<Typography>()
        private val inputs = mutableListOf<Input>()
        private val decorations = mutableListOf<Decoration>()
        private val stepperButtons = mutableListOf<MaterialButton>()
        private val content = FrameLayout(context).apply { tag = "a3.content"; clipChildren = false }
        private val card = RectF()
        private val border = RectF()
        private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33131417
            style = Paint.Style.STROKE
        }
        private var scale = 1f
        private var dialog: DatePickerDialog? = null
        private val action: MaterialButton

        init {
            tag = "a3.surface"
            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            // Do not focus an empty input and open the IME on entering A3.
            isFocusableInTouchMode = true
            addView(content)

            val intro = FrameLayout(context).apply { tag = "a3.intro" }
            place(content, intro, 465.1543f, 169f, 759.69f, 136f)
            place(intro, label("새 여정 만들기", 48f, 64f, ink, 700, centered = true).apply {
                tag = "a3.title"
                isAccessibilityHeading = true
            }, 0f, 0f, 759.69f, 64f)
            place(intro, label("함께할 사람들과 이번 여행의 첫 페이지를 남겨보세요.",
                28f, 36f, muted, centered = true).apply { tag = "a3.subtitle" },
                0f, 100f, 759.69f, 36f)

            val name = input(initialName, "예: 우리의 강릉 여행", InputType.TYPE_CLASS_TEXT,
                50, "a3.name", nameChanged)
            field("여행 이름 (선택)", "a3.nameLabel", name, 341.5f, 401f, 1007f)

            val people = input(initialPeople, "4명", InputType.TYPE_CLASS_NUMBER,
                2, "a3.people", peopleChanged, radius = 24f, stroke = 1f,
                paddingLeft = 95.24976f, paddingRight = 180f, fill = Color.WHITE).apply {
                imeOptions = EditorInfo.IME_ACTION_DONE
            }
            place(content, label("인원", 24f, 28f, muted).apply {
                tag = "a3.peopleLabel"; labelFor = people.id
            }, 341.5f, 567f, 42f, 28f)
            val peopleFrame = FrameLayout(context).apply { tag = "a3.peopleGroup" }
            place(content, peopleFrame, 341.5f, 611f, 495f, 90f)
            place(peopleFrame, people, 0f, 0f, 495f, 90f)
            place(peopleFrame, icon(R.drawable.a3_people, "a3.peopleIcon"),
                35.24977f, 33.12793f, 36f, 23.74457f)
            val adjustPeople: (Int) -> Unit = { delta ->
                val current = people.text?.toString()?.toIntOrNull() ?: 4
                people.setText((current + delta).coerceIn(1, 99).toString())
                people.setSelection(people.text?.length ?: 0)
            }
            stepper(peopleFrame, "a3.minus", "-", "인원 줄이기", Color.rgb(160, 165, 170),
                337f, 18f, 12f) { adjustPeople(-1) }
            stepper(peopleFrame, "a3.plus", "+", "인원 늘리기", Color.rgb(227, 228, 230),
                408.58984f, 16f, 16f) { adjustPeople(1) }

            val date = AppCompatButton(context).apply {
                id = View.generateViewId()
                tag = "a3.date"
                isAllCaps = false
                // Keep the calendar/chevron above this flat field when focused or selected.
                elevation = 0f; stateListAnimator = null
                configureInput(this, 24f, 1f, 93.692245f, 80f, Color.WHITE)
                fun update(value: String) {
                    // The reference's example remains a hint, never a saved date.
                    text = runCatching { LocalDate.parse(value).format(DATE_FORMAT) }.getOrDefault("")
                    hint = "2026.11.09(일)"
                    contentDescription = "종료 예정일 (선택): ${text.ifBlank { "날짜 선택" }}"
                }
                var selectedDate = initialDate
                update(selectedDate)
                setOnClickListener {
                    val selected = runCatching { LocalDate.parse(selectedDate) }.getOrElse { LocalDate.now() }
                    val picker = DatePickerDialog(context, { _, year, month, day ->
                        selectedDate = LocalDate.of(year, month + 1, day).toString()
                        update(selectedDate)
                        dateChanged(selectedDate)
                    }, selected.year, selected.monthValue - 1, selected.dayOfMonth)
                    dialog = picker
                    picker.setOnShowListener { AppTypography.applyTo(picker.window!!.decorView) }
                    picker.setOnDismissListener { dialog = null }
                    picker.show()
                }
            }
            place(content, label("종료 예정일 (선택)", 24f, 28f, muted).apply {
                tag = "a3.dateLabel"; labelFor = date.id
            }, 863.5f, 567f, 174f, 28f)
            place(content, date, 863.5f, 611f, 495f, 90f)
            place(content, icon(R.drawable.a3_calendar, "a3.calendarIcon"),
                897.19226f, 638f, 36f, 36f)
            // Keep the source arrow's outlined stroke outside its nominal bounds.
            val arrow = FrameLayout(context).apply { tag = "a3.chevronIcon"; clipChildren = false }
            place(content, arrow, 1292.6696f, 650.5f, 22f, 11f)
            place(arrow, icon(R.drawable.a3_chevron), -3f, -3f, 28f, 17f)

            val note = FrameLayout(context).apply {
                tag = "a3.notePanel"
                background = decoration(Color.rgb(251, 252, 255), 14f, 1f)
            }
            place(content, note, 341.5f, 733f, 1007f, 86f)
            val information = FrameLayout(context).apply {
                tag = "a3.infoIcon"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = decoration(Color.TRANSPARENT, 18f, 3f, Color.rgb(140, 140, 140))
            }
            place(note, information, 33f, 25f, 36f, 36f)
            place(information, label("!", 24f, 28f, Color.rgb(140, 140, 140)).apply {
                tag = "a3.infoLabel"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, 14.5f, 4f, 7f, 28f)
            place(note, label("크레딧으로 볼지, 이동비를 나눌지는 여행이 끝날 때 골라요.",
                24f, 28f, hintColor).apply { tag = "a3.note" }, 93f, 29f, 548f, 28f)

            val actionFrame = FrameLayout(context).apply { tag = "a3.action" }
            place(content, actionFrame, 341.5f, 919f, 1007f, 96f)
            action = MaterialButton(context).apply {
                tag = "a3.begin"
                contentDescription = "여행 시작"
                backgroundTintList = ColorStateList.valueOf(Color.rgb(54, 125, 255))
                minimumWidth = 0; minWidth = 0; minimumHeight = 0; minHeight = 0
                insetTop = 0; insetBottom = 0
                setPadding(0, 0, 0, 0)
                elevation = 0f; stateListAnimator = null; strokeWidth = 0
                addPressMotion()
                setOnClickListener { begin() }
            }
            place(actionFrame, action, 0f, 0f, 1007f, 96f)
            place(actionFrame, label("여행 시작", 30f, 44f, Color.rgb(247, 248, 250), 700).apply {
                tag = "a3.beginLabel"
                typeface = requireNotNull(ResourcesCompat.getFont(context, R.font.noto_sans_kr_bold))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnClickListener { begin() }
                isFocusable = false
            }, 440.59f, 25.25f, 119.31f, 44f)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
            // Below half-size, use the surrounding whitespace before reducing
            // native input text below 14sp; a short/IME viewport scrolls instead.
            val minimumScale = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                14f, resources.displayMetrics) / 28f
            val availableScale = ((width - 32f * resources.displayMetrics.density) / 1017f).coerceAtLeast(.01f)
            scale = max(width / designWidth, min(minimumScale, availableScale))
            val desiredHeight = px(designHeight)
            val height = resolveSize(desiredHeight, heightMeasureSpec).coerceAtLeast(1)
            setMeasuredDimension(width, height)
            card.set(0f, 0f, width.toFloat(), height.toFloat())
            placements.forEach { item ->
                item.params.width = px(item.width)
                item.params.height = px(item.height)
                item.params.leftMargin = px(item.x)
                item.params.topMargin = px(item.y)
                if (item.view.layoutParams != item.params) item.view.layoutParams = item.params
            }
            typography.forEach { item ->
                if (item.view is ReferenceDesignText) {
                    item.view.setReferenceTypography(item.size, item.lineHeight, item.baseline, scale)
                } else {
                    // Editable text keeps Android's cursor, selection and IME layout.
                    item.view.setTextSize(TypedValue.COMPLEX_UNIT_PX, item.size * scale)
                    item.view.setLineHeight(px(item.lineHeight))
                }
            }
            inputs.forEach { input ->
                val verticalPadding = px(27f)
                input.view.setPadding(px(input.paddingLeft), verticalPadding, px(input.paddingRight), verticalPadding)
            }
            decorations.forEach { item ->
                item.drawable.cornerRadius = item.radius * scale
                item.drawable.setStroke(px(item.stroke).coerceAtLeast(1), item.color)
            }
            stepperButtons.forEach { it.cornerRadius = px(14f) }
            action.cornerRadius = px(16f)
            content.measure(exact(px(designWidth)), exact(px(designHeight)))
            borderPaint.strokeWidth = 2f * scale
            border.set(card)
            border.inset(scale, scale)
            paper.setShadowLayer(((12f * scale - .5f) / .57735f).coerceAtLeast(.01f),
                0f, 6f * scale, 0x14000000)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val x = ((width - designWidth * scale) / 2f).roundToInt()
            val y = ((height - designHeight * scale) / 2f).roundToInt()
            content.layout(x, y, x + content.measuredWidth, y + content.measuredHeight)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRoundRect(card, 24f * scale, 24f * scale, paper)
            canvas.drawRoundRect(border, 23f * scale, 23f * scale, borderPaint)
        }

        override fun onDetachedFromWindow() {
            dialog?.dismiss()
            dialog = null
            super.onDetachedFromWindow()
        }

        private fun field(copy: String, tag: String, input: View, x: Float, y: Float, width: Float) {
            place(content, label(copy, 24f, 28f, muted).apply {
                this.tag = tag
                labelFor = input.id
            }, x, y, width, 28f)
            place(content, input, x, y + 44f, width, 90f)
        }

        private fun input(initial: String, placeholder: String, type: Int, limit: Int,
            tag: String, changed: (String) -> Unit, radius: Float = 14f, stroke: Float = 3f,
            paddingLeft: Float = 35f, paddingRight: Float = 35f, fill: Int = Color.TRANSPARENT) = AppCompatEditText(context).apply {
            id = View.generateViewId()
            this.tag = tag
            inputType = type
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_NEXT
            filters = arrayOf(InputFilter.LengthFilter(limit))
            configureInput(this, radius, stroke, paddingLeft, paddingRight, fill)
            hint = placeholder
            setText(initial)
            doAfterTextChanged { changed(it?.toString().orEmpty()) }
        }

        private fun configureInput(view: TextView, radius: Float, stroke: Float,
            paddingLeft: Float, paddingRight: Float, fill: Int) {
            view.typeface = AppTypography.font(context, 400)
            view.includeFontPadding = false
            view.letterSpacing = 0f
            view.maxLines = 1
            view.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            view.setTextColor(ink)
            view.setHintTextColor(hintColor)
            view.minimumWidth = 0; view.minWidth = 0; view.minimumHeight = 0; view.minHeight = 0
            view.background = decoration(fill, radius, stroke)
            inputs += Input(view, paddingLeft, paddingRight)
            typography += Typography(view, 28f, 36f)
        }

        private fun decoration(fill: Int, radius: Float, stroke: Float, color: Int = outline) =
            GradientDrawable().apply {
                setColor(fill)
                decorations += Decoration(this, radius, stroke, color)
            }

        private fun icon(resource: Int, name: String? = null) = ImageView(context).apply {
            tag = name
            setImageResource(resource)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        private fun stepper(parent: FrameLayout, tag: String, copy: String, description: String,
            fill: Int, x: Float, textX: Float, textWidth: Float, change: () -> Unit) {
            val frame = FrameLayout(context).apply { this.tag = tag + "Frame" }
            place(parent, frame, x, 21f, 48f, 48f)
            val button = MaterialButton(context).apply {
                this.tag = tag
                contentDescription = description
                backgroundTintList = ColorStateList.valueOf(fill)
                minimumWidth = 0; minWidth = 0; minimumHeight = 0; minHeight = 0
                insetTop = 0; insetBottom = 0
                setPadding(0, 0, 0, 0)
                elevation = 0f; stateListAnimator = null; strokeWidth = 0
                setOnClickListener { change() }
            }
            stepperButtons += button
            place(frame, button, 0f, 0f, 48f, 48f)
            place(frame, label(copy, 28f, 36f, Color.BLACK).apply {
                this.tag = tag + "Label"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isFocusable = false
                setOnClickListener { change() }
            }, textX, 6f, textWidth, 36f)
        }

        private fun label(copy: String, size: Float, lineHeight: Float, color: Int,
            weight: Int = 400, centered: Boolean = false) = ReferenceDesignLabel(context).apply {
            text = copy
            typeface = AppTypography.font(context, weight)
            setTextColor(color)
            includeFontPadding = false
            letterSpacing = 0f
            maxLines = 1
            gravity = Gravity.TOP or if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
            setPadding(0, 0, 0, 0)
            val baseline = when (size) {
                48f -> 49.064f
                30f -> 34f
                28f -> 27.954f
                else -> 22.532f
            }
            typography += Typography(this, size, lineHeight, baseline)
        }

        private fun place(parent: FrameLayout, view: View, x: Float, y: Float, width: Float, height: Float) {
            val item = Placement(view, x, y, width, height)
            placements += item
            parent.addView(view, item.params)
        }

        override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        private fun px(value: Float) = (value * scale).roundToInt()
        private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)
    }

    companion object {
        internal const val POPUP_WIDTH = 1100f
        internal fun popupHeight(titleOnly: Boolean): Float = if (titleOnly) 480f else 720f
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd(E)", Locale.KOREAN)
    }
}
