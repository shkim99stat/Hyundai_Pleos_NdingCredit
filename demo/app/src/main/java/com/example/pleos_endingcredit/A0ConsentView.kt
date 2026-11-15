package com.example.pleos_endingcredit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.appcompat.widget.AppCompatCheckBox

/** A0 consent screen from Figma node 326:9843. */
internal class A0ConsentView @JvmOverloads constructor(
    context: Context, onAccept: () -> Unit = {},
) : FrameLayout(context) {
    private val screen = ConsentDesignSurface(context, Color.WHITE)
    private val termsMotion = PopupMotion(this)
    private var terms: View? = null
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = dismissTerms()
    }

    init {
        addView(screen, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        screen.label("a0.title", "여행 기록을 위해 약관에 동의해 주세요.",
            0f, 135f, 1678f, 52f, 40f, 700, color = 0xFF1D1E21.toInt(), centered = true)
            .isAccessibilityHeading = true
        screen.label("a0.subtitle", "차에 담긴 이야기로 여행 크레딧을 만들고,",
            0f, 206.25f, 1685f, 34f, 26f, color = CONSENT_MUTED, centered = true)
        screen.label("a0.subtitle.second", "이동비도 나눠 드려요.",
            0f, 240.25f, 1685f, 34f, 26f, color = CONSENT_MUTED, centered = true)

        val cardX = 276.47f
        val cardY = 310f
        screen.box(cardX, cardY, 1143f, 565f, Color.WHITE, 36f, 0x33131417)
        screen.label("a0.required", "필수 약관", cardX + 35, cardY + 30, 101f, 31f, 26f, 700)
            .isAccessibilityHeading = true
        screen.label("a0.required.count", "2", cardX + 145, cardY + 27, 28f, 38f,
            26f, 700, color = CONSENT_BLUE)
        screen.label("a0.required.hint", "시작하려면 아래 약관에 동의하세요",
            cardX + 760, cardY + 32, 350f, 29f, 24f, 500, color = 0xFF888888.toInt())
        screen.icon("divider.svg", cardX + 35, cardY + 76.5f, 1073.01f, 1f).rotation = -.21f

        val location = addAgreement("location", "위치·경로 기반서비스 이용약관",
            cardX, cardY + 130, 83.5f)
        val driving = addAgreement("driving", "주행 정보·수집필수동의",
            cardX, cardY + 343, 92f)
        addTermsLink("location.first", ConsentTerms.LOCATION, cardX, cardY + 195.32f, "위치 서비스 이용 약관 동의")
        addTermsLink("location.second", ConsentTerms.LOCATION, cardX, cardY + 248.81f, "경로 서비스 이용 약관 동의")
        addTermsLink("driving.first", ConsentTerms.DRIVING, cardX, cardY + 419.63f, "위치 서비스 이용 약관 동의")
        addTermsLink("driving.second", ConsentTerms.DRIVING, cardX, cardY + 473.12f, "경로 서비스 이용 약관 동의")

        val start = screen.button("a0.accept", "동의 후 시작", cardX, 899f, 1143f, 96f,
            size = 30f, fill = CONSENT_BLUE, noto = true) {
            if (location.isChecked && driving.isChecked) onAccept()
        }.apply {
            val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
            backgroundTintList = ColorStateList(states, intArrayOf(0xFFE1E6EE.toInt(), CONSENT_BLUE))
            setTextColor(ColorStateList(states, intArrayOf(CONSENT_MUTED, Color.WHITE)))
            isEnabled = false
        }
        val update = { start.isEnabled = location.isChecked && driving.isChecked }
        location.setOnCheckedChangeListener { _, checked ->
            screen.findViewWithTag<View>("a0.location.checked").visibility = if (checked) VISIBLE else INVISIBLE
            update()
        }
        driving.setOnCheckedChangeListener { _, checked ->
            screen.findViewWithTag<View>("a0.driving.checked").visibility = if (checked) VISIBLE else INVISIBLE
            update()
        }
        screen.label("a0.note", "기록은 이 차 안에 저장해요. 자세한 위치는 여행 종료 30일 뒤 앱을 열면 지워요.",
            0f, 1019f, 1646f, 30f, 24f, color = 0xFF666666.toInt(), centered = true)
    }

    private fun addAgreement(key: String, title: String, cardX: Float, y: Float, labelX: Float): AppCompatCheckBox {
        val checkbox = AppCompatCheckBox(context).apply {
            tag = "a0.${key}Consent"
            contentDescription = "$title (필수)"
            buttonDrawable = null
            background = null
            isFocusable = true
        }
        screen.place(checkbox, cardX + 25f, y - 9f, 56f, 54f)
        screen.box(cardX + 35f, y, 36f, 36f, Color.WHITE, 8f, 0xFF888888.toInt())
        screen.icon("checked.svg", cardX + 35f, y, 36f, 36f).apply {
            tag = "a0.$key.checked"
            visibility = INVISIBLE
        }
        screen.label("a0.$key.label", title, cardX + labelX, y - 2f, 540f, 38f,
            26f, 700, color = CONSENT_INK).apply {
            setOnClickListener { checkbox.performClick() }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        return checkbox
    }

    private fun addTermsLink(key: String, terms: ConsentTerms, cardX: Float, y: Float, text: String) {
        val copy = screen.label("a0.$key.copy", text, cardX + 83.5f, y, 365f, 29f,
            24f, 500, color = 0xFF888888.toInt())
        val label = screen.label("a0.$key.view.label", "보기", cardX + 1053f, y + 2.68f,
            40f, 24f, 20f, 500, color = 0xFF888888.toInt())
        label.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        screen.icon("chevron.svg", cardX + 1098.5f, y + 4.68f, 11f, 19f).rotation = 180f
        screen.action("a0.$key.view", "${terms.title} $text 보기", cardX + 1005f, y - 9f,
            115f, 48f) { openTerms(terms) }
        copy.setOnClickListener { openTerms(terms) }
    }

    private fun openTerms(kind: ConsentTerms) {
        if (terms != null) return
        val overlay = ConsentTermsOverlay(context, kind, ::dismissTerms)
        terms = overlay
        screen.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        screen.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        termsMotion.show(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.requestFocus()
        back.isEnabled = true
    }

    private fun dismissTerms() {
        if (terms == null) return
        termsMotion.dismiss()
        terms = null
        screen.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO
        screen.descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        back.isEnabled = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (context as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher?.addCallback(back)
    }

    override fun onDetachedFromWindow() {
        back.remove()
        termsMotion.dismiss(animate = false)
        terms = null
        back.isEnabled = false
        screen.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO
        screen.descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        super.onDetachedFromWindow()
    }
}
