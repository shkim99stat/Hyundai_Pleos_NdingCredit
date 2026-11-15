package com.example.pleos_endingcredit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** A1 settlement setup, Figma node 341:14976. Coordinates use the 1700 × 1184 app window. */
@SuppressLint("ViewConstructor")
internal class A1PayoutSetupView(context: Context, onContinue: () -> Unit) :
    ConsentDesignSurface(context, Color.WHITE, sourceWidth = 1700f, assetDirectory = "a03") {
    private val paleBlue = 0xFFF1F6FF.toInt()
    private val inactiveFill = 0xFFF7F8FA.toInt()
    private val outline = 0x33131417
    private val secondary = 0xFFA4A8AE.toInt()
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD4D4D4.toInt()
        style = Paint.Style.STROKE
    }
    private val windowBorder = RectF()

    init {
        tag = "a03.setup"
        layoutDirection = LAYOUT_DIRECTION_LTR

        box(88f, 80f, 171f, 56f, paleBlue, 24f)
        icon("f241a.svg", 112f, 96f, 24f, 24f)
        label("a03.badge", "이동비 정산", 144f, 96f, 96f, 24f, 20f, 700, CONSENT_BLUE)

        label("a03.title", "이동비를 받을 곳을", 88f, 160f, 680f, 64f, 48f, 800)
            .isAccessibilityHeading = true
        label("a03.title.second", "미리 정해 둘까요?", 88f, 224f, 680f, 64f, 48f, 800)
        label("a03.description", "등록해 두면 여행이 끝난 뒤", 88f, 304f, 680f, 36f, 28f,
            color = 0xFF44464E.toInt())
        label("a03.description.second", "함께 탄 사람에게 바로 정산을 보낼 수 있어요.",
            88f, 340f, 680f, 36f, 28f, color = 0xFF44464E.toInt())

        icon("4fe00.svg", 31.994f, 571.5f, 160.012f, 1f).rotation = -89.28f
        step(1, 457f, "휴대폰 카메라로 QR 스캔", 310f)
        step(2, 541f, "받을 곳 고르기", 169f)
        label("a03.step.2.note", "휴대폰에서", 345f, 553f, 110f, 24f, 24f, 700, secondary)
        step(3, 625f, "등록완료", 103f)
        label("a03.step.3.note", "차에 바로 반영되요", 279f, 637f, 185f, 24f, 24f, 700, secondary)
        box(471f, 453f, 122f, 56f, paleBlue, 24f)
        label("a03.easy", "간편 등록", 471f, 469f, 122f, 24f, 20f, 700, CONSENT_BLUE, centered = true)

        label("a03.timer.label", "QR 코드 유효시간", 1196f, 157f, 382f, 34f, 26f,
            color = 0xFF565656.toInt(), centered = true)
        // The linked design supplies an example QR and timer state.
        label("a03.timer", "02:48", 1277f, 195f, 218f, 80f, 80f, 800, CONSENT_BLUE, centered = true)
        box(1194f, 331f, 394f, 394f, Color.WHITE, 32f, outline)
        image("fe6f7.png", "a03.qr", 1211f, 348f, 360f, 24f).apply {
            contentDescription = "정산 등록 예시 QR 코드"
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        icon("77206.svg", 1217f, 362f, 347f, 331f).tag = "a03.qr.corners"
        label("a03.status", "휴대폰 연결을 기다리는 중...", 1194f, 757f, 394f, 28f,
            24f, 700, CONSENT_BLUE, centered = true)

        provider("toss", "토스", 1170f, 108f, "c6dff.png")
        provider("kakao", "카카오페이", 1286f, 169f, "7aa89.png")
        provider("account", "계좌번호", 1463f, 149f)

        icon("c940c.svg", 88f, 970f, 24f, 24f)
        label("a03.privacy", "입력한 내용은 차량 디스플레이에 보이지 않아요",
            120f, 968f, 470f, 28f, 24f, color = CONSENT_MUTED)
        button("a03.later", "나중에 할게요", 88f, 1020f, 1524f, 84f, 28f, CONSENT_BLUE,
            lineHeight = 36f, onClick = onContinue)
    }

    private fun step(number: Int, y: Float, title: String, titleWidth: Float) {
        val current = number == 1
        if (current) {
            box(88f, y, 48f, 48f, 0xFFD2E4FF.toInt(), 24f)
            box(92f, y + 4f, 40f, 40f, CONSENT_BLUE, 20f)
        } else {
            box(88f, y, 48f, 48f, inactiveFill, 24f, outline)
        }
        label("a03.step.$number", number.toString(), 88f, y + 5f, 48f, 38f, 26f, 500,
            if (current) Color.WHITE else CONSENT_MUTED, centered = true, noto = true)
        label("a03.step.$number.title", title, 152f, y + 5f, titleWidth, 38f, 30f, 700,
            if (current) 0xFF222222.toInt() else CONSENT_MUTED)
    }

    private fun provider(key: String, title: String, x: Float, width: Float, file: String? = null) {
        box(x, 817f, width, 46f, Color.WHITE, 12f, outline)
        if (file != null) {
            image(file, "a03.provider.$key.icon", x + 17f, 828f, 24f, 4f)
        } else {
            box(x + 17f, 828f, 24f, 24f, CONSENT_MUTED, 4f)
            icon("3f7e8.svg", x + 20f, 831f, 18f, 18f).tag = "a03.provider.$key.icon"
        }
        label("a03.provider.$key", title, x + 49f, 826f, width - 60f, 28f, 24f, color = CONSENT_MUTED)
    }

    private fun image(file: String, id: String, x: Float, y: Float, size: Float, radius: Float): View {
        val bitmap = requireNotNull(context.assets.open("a03/$file").use(BitmapFactory::decodeStream))
        val view = object : View(context) {
            private val bounds = RectF()
            private val clip = Path()
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                bounds.set(0f, 0f, w.toFloat(), h.toFloat())
                clip.rewind()
                val rounded = radius * w / size
                clip.addRoundRect(bounds, rounded, rounded, Path.Direction.CW)
            }

            override fun onDraw(canvas: Canvas) {
                val saved = canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(bitmap, null, bounds, paint)
                canvas.restoreToCount(saved)
            }
        }.apply {
            tag = id
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        place(view, x, y, size, size)
        return view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val saved = canvas.save()
        canvas.translate(surface.left, surface.top)
        canvas.scale(scale, scale)
        windowBorder.set(1f, 1f, 1699f, 1183f)
        borderPaint.strokeWidth = 2f
        canvas.drawRoundRect(windowBorder, 23f, 23f, borderPaint)
        cardPaint.setShadowLayer(12f, 0f, 6f, 0x14000000)
        canvas.drawRoundRect(1194f, 331f, 1588f, 725f, 32f, 32f, cardPaint)
        canvas.restoreToCount(saved)
    }
}
