package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.roundToInt

internal class B1CreditVideoPromptView(
    context: Context,
    trip: Trip,
    qr: Bitmap?,
    receipt: CreditVideoReceipt?,
    qrStatus: String,
    sampleQr: Boolean,
    onQrAction: () -> Unit,
    onSkip: () -> Unit,
    onMake: (() -> Unit)?,
    controls: View? = null,
) : FrameLayout(context) {
    val screenId = if (receipt == null) "B1" else "B1-1"

    init {
        tag = "b1.credit.video"
        val backdrop = ConsentDesignSurface(context, Color.WHITE, assetDirectory = "a4").apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            alpha = .35f
            box(56f, 139.8f, 1584f, 992.2f, 0xFFE6E6E6.toInt(), 16f, 0xFFB5B5B5.toInt())
            label("b1.trip", trip.name, 56f, 47.1f, 314f, 64f, 44f, 700, noto = true)
                .ellipsize = android.text.TextUtils.TruncateAt.END
            box(380f, 55.6f, 238f, 47f, 0xFFFFEBEB.toInt(), 24f)
            icon("b6899.svg", 398f, 72.1f, 14f, 14f)
            label("b1.recording", "기록하고 있어요", 422f, 63.6f, 176f, 31f, 26f, 700,
                color = 0xFFBF2121.toInt(), noto = true)
            if (Build.VERSION.SDK_INT >= 31) {
                setRenderEffect(RenderEffect.createBlurEffect(10f, 10f, Shader.TileMode.CLAMP))
            }
        }
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(CreditVideoCard(context, qr, receipt, qrStatus, sampleQr, onQrAction, onSkip, onMake),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        controls?.let {
            val density = resources.displayMetrics.density
            addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.LEFT).apply {
                leftMargin = (12 * density).roundToInt()
                bottomMargin = (48 * density).roundToInt()
            })
        }
    }
}

private class CreditVideoCard(
    context: Context,
    qr: Bitmap?,
    receipt: CreditVideoReceipt?,
    qrStatus: String,
    sampleQr: Boolean,
    onQrAction: () -> Unit,
    onSkip: () -> Unit,
    onMake: (() -> Unit)?,
) : ConsentDesignSurface(context, 0x8C14141A.toInt(), assetDirectory = "b1") {
    private val waitingBorder = GradientDrawable().apply { setColor(0xFFF4F5F7.toInt()) }
    private val thumbnailShape = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFF5AA9E6.toInt(), 0xFF1D3557.toInt(), 0xFF1D3557.toInt()),
    )
    private val skipButton: ReferenceDesignButton
    private val makeButton: ReferenceDesignButton

    init {
        isClickable = true
        box(X, Y, 1100f, 700f, Color.WHITE, 32f)
        copy("title", "크레딧에 넣을 영상이 있나요?", 64f, 60f, 972f, 53f, 44f, 700, 0xFF111111.toInt())
        copy("description", "휴대폰으로 QR을 찍어 영상 한 개를 올리면 크레딧 가운데에 들어가요.",
            64f, 130f, 972f, 39f, 26f, color = MUTED)
        copy("duration.hint", "5초~30초 · 소리는 빼고 넣어요", 64f, 169f, 972f, 39f, 26f, color = MUTED)

        if (sampleQr) {
            val size = if (receipt == null) 210f else 260f
            val left = if (receipt == null) 89f else 64f
            val top = if (receipt == null) 276.5f else 252f
            val asset = if (receipt == null) "qr-waiting.svg" else "qr-received.svg"
            icon(asset, X + left, Y + top, size, if (receipt == null) 211f else size).apply {
                contentDescription = "시연용 영상 올리기 QR"
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            }
        } else if (qr != null) {
            // The QR's white quiet zone sits outside the source artwork's black module bounds.
            val size = if (receipt == null) 256f else 304f
            place(ImageView(context).apply {
                tag = "b1.qr"
                setImageBitmap(qr)
                contentDescription = "휴대폰에서 영상 올리기 QR 코드"
            }, X + 194f - size / 2f, Y + 382f - size / 2f, size, size)
        } else {
            button("b1.qr.retry", qrStatus, X + 64f, Y + 333f, 260f, 96f, 23f,
                0xFFEEF0F3.toInt(), noto = true, textColor = INK, onClick = onQrAction)
        }

        if (receipt == null) {
            place(View(context).apply { background = waitingBorder }, X + 394f, Y + 252f, 642f, 260f)
            icon("waiting.svg", X + 681f, Y + 302f, 64f, 64f)
            copy("status", "아직 받은 영상이 없어요", 394f, 380f, 642f, 36f, 28f, 700, centered = true)
            val hint = "영상 없이 크레딧을 만들 수 있어요"
            copy("waiting.hint", hint, 394f, 426f, 642f, 28f, 22f,
                color = QUIET, centered = true)
        } else {
            box(X + 394f, Y + 252f, 642f, 260f, 0xFFEEF3FF.toInt(), 20f)
            place(object : AppCompatImageView(context) {
                private val mask = Path()

                override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                    super.onSizeChanged(w, h, oldw, oldh)
                    mask.reset()
                    mask.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 14f * scale, 14f * scale, Path.Direction.CW)
                }

                override fun onDraw(canvas: Canvas) {
                    val saved = canvas.save()
                    canvas.clipPath(mask)
                    super.onDraw(canvas)
                    canvas.restoreToCount(saved)
                }
            }.apply {
                tag = "b1.thumbnail"
                background = thumbnailShape
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(receipt.thumbnail)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, X + 424f, Y + 282f, 300f, 200f)
            icon("play.svg", X + 546f, Y + 354f, 56f, 56f)
            copy("thumbnail.duration", receipt.durationText, 428f, 442f, 280f, 30f, 20f, 700, Color.WHITE)
                .apply { gravity = Gravity.END; setShadowLayer(2f, 0f, 1f, 0x99000000.toInt()) }
            copy("status", "영상을 받았어요", 754f, 314f, 260f, 41f, 30f, 700, 0xFF1D3FA8.toInt())
            copy("received.duration", "${receipt.durationText} · 앞 ${receipt.includedSeconds}초가 들어가요",
                754f, 364f, 267f, 29f, 22f, color = MUTED)
            copy("replace.1", "다시 올리면 마지막에 올린", 754f, 408f, 260f, 31f, 22f, color = QUIET)
            copy("replace.2", "영상으로 바뀌어요", 754f, 439f, 260f, 31f, 22f, color = QUIET)
        }

        skipButton = button("b1.skip", "영상 없이 만들기", X + 64f, Y + 560f, 470f, 96f, 30f,
            0xFFEEF0F3.toInt(), noto = true, textColor = INK, onClick = onSkip)
        val makeFill = if (onMake == null) 0xFFB9CDFD.toInt() else 0xFF2F6BFF.toInt()
        makeButton = button("b1.make", "크레딧 만들기", X + 566f, Y + 560f, 470f, 96f, 30f,
            makeFill, noto = true, onClick = onMake)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val radius = 20f * scale
        waitingBorder.cornerRadius = radius
        waitingBorder.setStroke((2f * scale).roundToInt().coerceAtLeast(1), 0xFFC9CED6.toInt(),
            10f * scale, 7f * scale)
        thumbnailShape.cornerRadius = 14f * scale
        skipButton.cornerRadius = radius.roundToInt()
        makeButton.cornerRadius = radius.roundToInt()
    }

    private fun copy(tag: String, text: String, x: Float, y: Float, w: Float, h: Float, size: Float,
        weight: Int = 400, color: Int = INK, centered: Boolean = false) =
        label("b1.$tag", text, X + x, Y + y, w, h, size, weight, color, centered, noto = true)

    private companion object {
        const val X = 298f
        const val Y = 240f
        const val INK = 0xFF2B2F36.toInt()
        const val MUTED = 0xFF5B6170.toInt()
        const val QUIET = 0xFF8A8F98.toInt()
    }
}
