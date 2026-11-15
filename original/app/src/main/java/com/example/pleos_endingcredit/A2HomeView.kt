package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout

/** A2 entry screen for both fresh installs and returning travelers. */
internal class A2HomeView(context: Context, onStart: () -> Unit, onArchive: () -> Unit) :
    ResultDesignSurface(context, 1680f, 1184f) {

    init {
        tag = "home.a2"
        // Figma 935:81387: the sky blue fades to white at 78.846% of the surface.
        place(object : View(context) {
            private val paint = Paint()

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h * .78846f, 0xFFE1F3FF.toInt(), Color.WHITE, Shader.TileMode.CLAMP,
                )
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }, 0f, 0f, 1680f, 1184f)
        addTravelCollage()
        place(label("home.title", "N딩 크레딧", 48f, 800, typeface = AppTypography.font(context, 800))
            .apply { isAccessibilityHeading = true }, 56f, 55f, 450f, 64f)
        place(label("home.empty.title", "첫 여행을 기록해 볼까요?", 52f, 700, centered = true),
            320f, 713f, 1040f, 68f)
        place(label("home.empty.description", "여행이 끝나면 다닌 길이 짧은 영화처럼 나오고, 이동비도 바로 나눌 수 있어요",
            26f, color = muted, centered = true), 250f, 805f, 1180f, 36f)
        place(button("home.start", "여행 준비하기", onStart, solid = true, size = 32f)
            .apply { background = shape(0xFF367DFF.toInt(), 32f) }, 336f, 883f, 1048f, 96f)
        place(button("home.archive", "이전 여행 다시보기", onArchive, solid = true, size = 32f)
            .apply { background = shape(0xFF404040.toInt(), 32f) }, 336f, 996f, 1048f, 96f)
    }

    private fun addTravelCollage() {
        // Figma 935:81386 uses a 1700px window. Align its artwork to this 1680px surface's center.
        place(travelSvg(context, "a2-935-route.svg"), 456f, 330.764f, 776.383f, 220.79f)
        photoCard("left", 344.325f, 317.357f, -11.23f, 594.07f, 468.12f,
            shadowBlur = 12f, shadowX = -4f, shadowY = 9f, shadowColor = 0x1A000000)
        photoCard("middle", 417.547f, 384.844f, -2.14f, 852.806f, 394.116f,
            shadowBlur = 14.6f, shadowX = 0f, shadowY = 13f, shadowColor = 0x40000000)
        photoCard("right", 384.382f, 354.277f, 23.66f, 1092.667f, 459.398f,
            shadowBlur = 18f, shadowX = -4f, shadowY = 4f, shadowColor = 0x40000000)
        place(travelSvg(context, "a2-935-pin.svg").apply { rotation = 29.02f },
            1359.62f, 307.06f, 51f, 71f)
    }

    private fun photoCard(name: String, frameSize: Float, photoSize: Float, angle: Float,
        centerX: Float, centerY: Float, shadowBlur: Float, shadowX: Float, shadowY: Float,
        shadowColor: Int) {
        val padding = 30f
        val outerSize = frameSize + padding * 2f
        val card = object : FrameLayout(context) {
            private val image = travelImage(context, "a2-935-$name.webp", 820)
            private val matte = RectF()
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            private val photoOutline = GradientDrawable().apply { setColor(Color.WHITE) }
            private var radius = 0f

            init {
                rotation = angle
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                setWillNotDraw(false)
                image.background = photoOutline
                image.clipToOutline = true
                addView(image)
            }

            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, h)
                val imageSize = (photoSize * measuredWidth / outerSize).toInt().coerceAtLeast(1)
                val spec = MeasureSpec.makeMeasureSpec(imageSize, MeasureSpec.EXACTLY)
                image.measure(spec, spec)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val inset = (width - image.measuredWidth) / 2
                image.layout(inset, inset, inset + image.measuredWidth, inset + image.measuredHeight)
            }

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                val scale = w / outerSize
                val edge = padding * scale
                radius = 30f * scale
                photoOutline.cornerRadius = radius
                matte.set(edge, edge, w - edge, h - edge)
                val blur = ((shadowBlur * scale / 2f - .5f) / .57735f).coerceAtLeast(.01f)
                paint.setShadowLayer(blur, shadowX * scale, shadowY * scale, shadowColor)
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawRoundRect(matte, radius, radius, paint)
            }
        }
        place(card, centerX - outerSize / 2f, centerY - outerSize / 2f, outerSize, outerSize)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saved = canvas.save()
        val clip = Path().apply { addRoundRect(surface, 24f * drawingScale, 24f * drawingScale, Path.Direction.CW) }
        canvas.clipPath(clip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
    }
}
