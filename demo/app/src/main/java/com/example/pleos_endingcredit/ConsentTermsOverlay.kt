package com.example.pleos_endingcredit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.withClip
import androidx.core.view.ViewCompat

internal enum class ConsentTerms(val screenId: String, val title: String) {
    LOCATION("A0-1", "위치·경로"),
    DRIVING("A0-2", "주행 기록"),
}

/** App-window overlay for the A0 terms panels (278:3911 and 278:2860). */
@SuppressLint("ViewConstructor")
internal class ConsentTermsOverlay(context: Context, kind: ConsentTerms, dismiss: () -> Unit) :
    ConsentDesignSurface(context, 0xB3000000.toInt()) {
    private val panelX = if (kind == ConsentTerms.LOCATION) 298.47f else 298f
    private val panelY = 242f
    private val panelHeight = if (kind == ConsentTerms.LOCATION) 734f else 700f
    private val panel = RectF()
    private val shadow = RectF()
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x05000000 }
    private val secondary = 0xFF737373.toInt()

    init {
        tag = "a0.terms"
        setTag(R.id.ending_credit_screen_id, kind.screenId)
        ViewCompat.setAccessibilityPaneTitle(this, "${kind.title} 약관")
        isFocusableInTouchMode = true
        setOnClickListener { dismiss() }
        place(View(context).apply {
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, panelX, panelY, 1100f, panelHeight)
        when (kind) {
            ConsentTerms.LOCATION -> location(dismiss)
            ConsentTerms.DRIVING -> driving(dismiss)
        }
    }

    private fun location(dismiss: () -> Unit) {
        label("a0.terms.title", "위치·경로", panelX + 47.53f, panelY + 44f, 155f, 41f, 34f, 700)
            .isAccessibilityHeading = true
        box(panelX + 207.53f, panelY + 48.5f, 59f, 32f, 0x4D0066FF, 8f)
        label("a0.terms.required", "필수", panelX + 207.53f, panelY + 48.5f, 59f, 32f,
            20f, 600, color = 0xFF23327D.toInt(), centered = true)
        label("a0.terms.subtitle", "지도에 다닌 길을 그려요", panelX + 47.5f, panelY + 103.97f,
            900f, 29f, 24f, color = secondary)
        icon("close.svg", panelX + 1004, panelY + 48, 48f, 48f)
        action("a0.terms.close", "닫기", panelX + 992, panelY + 36, 72f, 72f, dismiss)

        box(panelX + 53, panelY + 169, 999f, 303f, 0xFFEBF1F6.toInt(), 19f)
        val rows = listOf(
            "모으는 정보" to "차의 위치(GPS)와 다닌 경로, 정차 지점을 기록해요.",
            "이용 목적" to "지도에 다닌 길을 그리고, 정차 지점 주변 장소를 찾고, 크레딧을 만들어요.",
            "보관 기간" to "여행 종료 30일 뒤 앱을 열면 위치 원본을 지워요. 여행 기록은 삭제할 때까지 남아요.",
            "외부 전송" to "지도 표시 영역을 OpenFreeMap에, 주변 장소 검색에 필요한 정차 위치를 카카오맵에 보내요.",
        )
        val rowY = listOf(199.47f, 260.72f, 324.97f, 386.22f)
        rows.forEachIndexed { index, (name, text) ->
            val y = panelY + rowY[index]
            label("a0.terms.row.$index", name, panelX + 79.25f, y, 123f, 29f,
                24f, 600, color = Color.BLACK)
            body(text, panelX + 205.53f, y, 823f, 60f, 22f, 28f)
        }
        button("a0.terms.confirm", "확인했어요", panelX + 47.53f, panelY + 496.54f,
            1004f, 96f, 28f, CONSENT_BLUE, noto = true, onClick = dismiss)
        button("a0.terms.full", "전체 약관 보기", panelX + 47.53f, panelY + 611.79f,
            1004f, 96f, 28f, 0xFFF2F2F2.toInt(), weight = 600, textColor = Color.BLACK,
            stroke = 0xFFC0C0C0.toInt())
    }

    private fun driving(dismiss: () -> Unit) {
        label("a0.terms.title", "주행 기록", panelX + 48, panelY + 44, 142f, 41f, 34f, 700, noto = true)
            .isAccessibilityHeading = true
        box(panelX + 195, panelY + 48.5f, 61f, 32f, 0xFFF0F0F0.toInt(), 8f)
        label("a0.terms.required", "필수", panelX + 195, panelY + 48.5f, 61f, 32f,
            20f, 700, centered = true, noto = true)
        label("a0.terms.subtitle", "크레딧 장면과 이동비를 만들어요", panelX + 48, panelY + 98,
            900f, 29f, 24f, color = secondary, noto = true)

        val rows = listOf(
            "모으는 정보" to "달린 거리 · 주행 시간 · 속도 · 정차 및 문 열림 신호",
            "쓰는 곳" to "주행 기록과 크레딧 만들기 · 정차 지점 찾기",
            "보관 기간" to "여행 종료 30일 뒤 앱을 열면 속도 원본을 지워요.",
            "저장하지 않는 것" to "좌석별 탑승 여부는 저장하지 않아요. 감지 인원은 여행 인원 입력에 써요.",
        )
        val separators = listOf(162f, 262f, 362f, 433f)
        rows.forEachIndexed { index, (name, text) ->
            val y = panelY + separators[index]
            box(panelX + 48, y, 1004f, 1f, 0xFFE6E6E6.toInt())
            label("a0.terms.row.$index", name, panelX + 48, y + 22, 248f, 30f,
                22f, 500, color = secondary, noto = true)
            body(text, panelX + 300, y + 20, 752f, 51f, 24f, 30f)
        }
        button("a0.terms.confirm", "확인했어요", panelX + 48, panelY + 520, 1004f, 96f,
            28f, 0xFF333333.toInt(), noto = true, onClick = dismiss)
        label("a0.terms.full", "전체 약관 보기 ›", panelX + 48, panelY + 638, 1004f, 26f,
            22f, 500, color = secondary, centered = true, noto = true).isEnabled = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        panel.set(surface.left + panelX * scale, surface.top + panelY * scale,
            surface.left + (panelX + 1100f) * scale, surface.top + (panelY + panelHeight) * scale)
        shadow.set(panel)
        shadow.inset(-10f * scale, -10f * scale)
        shade.setShadowLayer((10f * scale).coerceAtLeast(1f), 0f, 0f, 0x19000000)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.withClip(surface) {
            drawRoundRect(shadow, 38f * scale, 38f * scale, shade)
            drawRoundRect(panel, 28f * scale, 28f * scale, white)
        }
    }
}
