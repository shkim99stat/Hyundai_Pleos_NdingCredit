package com.example.pleos_endingcredit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import com.caverock.androidsvg.SVG
import kotlin.math.min
import kotlin.math.roundToInt

/** Figma 265:2, composed from native text and shapes plus the original perforation/notch SVGs. */
@SuppressLint("ViewConstructor")
internal class CreditTicketView(context: Context, private val content: CreditContent) : ViewGroup(context) {
    private data class Slot(val view: ReferenceDesignLabel, val x: Float, val y: Float,
        val w: Float, val h: Float, val size: Float, val baseline: Float)
    private val slots = mutableListOf<Slot>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Path().apply { addRoundRect(0f, 0f, 540f, 236f, 12f, 12f, Path.Direction.CW) }
    private val notch by lazy { SVG.getFromAsset(context.assets, "b3/10edc.svg").renderToPicture() }
    private val perforation by lazy { SVG.getFromAsset(context.assets, "b3/24438.svg").renderToPicture() }
    private val inter = Typeface.create(ResourcesCompat.getFont(context, R.font.inter_variable), 600, false)
    private val mono = Typeface.create(ResourcesCompat.getFont(context, R.font.roboto_mono_variable), 400, false)
    private val monoBold = Typeface.create(mono, 700, false)
    private val notoBold = ResourcesCompat.getFont(context, R.font.noto_sans_kr_bold)!!
    private val notoMedium = Typeface.create(ResourcesCompat.getFont(context, R.font.noto_sans_kr_regular), 500, false)
    private val batang = ResourcesCompat.getFont(context, R.font.gowun_batang_bold)!!
    private var scale = 1f
    private var elapsed = 0L
    private lateinit var distance: ReferenceDesignLabel
    private val stamp = TicketStamp(context, content.ticketEndDate, inter, notoMedium)

    init {
        tag = "b3.ticket"
        setWillNotDraw(false)
        clipChildren = false; clipToPadding = false
        label("cinema", "N-DING CREDIT CINEMA", 20f, 10f, 220f, 11f, 9f, inter, PAPER, 2.52f)
        label("number", "No. ${content.ticketNumber}", 312f, 10f, 64f, 12f, 9f, mono, 0xB3F2ECDF.toInt(), gravity = Gravity.END)
        label("showing", "NOW SHOWING", 20f, 46f, 180f, 10f, 8.5f, inter, RED, 2.55f)
        label("title", content.title, 20f, 60f, 356f, 38f, 26f, batang, INK, baseline = 30.94f)
        label("subtitle", content.ticketSubtitle, 20f, 102f, 356f, 10f, 8.5f, inter, DIM, 1.87f)
        label("dateLabel", "DATE", 20f, 136f, 160f, 10f, 8f, inter, DIM, 1.92f)
        label("date", content.ticketDate, 20f, 149f, 182f, 17f, 13f, monoBold, INK, baseline = 13.65f)
        label("theaterLabel", "THEATER", 214f, 136f, 162f, 10f, 8f, inter, DIM, 1.92f)
        label("theater", content.ticketTheater, 214f, 149f, 162f, 17f, 13f, notoBold, INK)
        label("runtimeLabel", "RUNTIME", 20f, 182f, 160f, 10f, 8f, inter, DIM, 1.92f)
        label("runtime", content.ticketRuntime, 20f, 195f, 170f, 17f, 13f, monoBold, INK, baseline = 13.65f)
        label("seatsLabel", "SEATS", 214f, 182f, 162f, 10f, 8f, inter, DIM, 1.92f)
        label("seats", content.ticketSeats, 214f, 195f, 162f, 17f, 13f, monoBold, INK, baseline = 13.65f)
        label("admit", "ADMIT", 418f, 22f, 101f, 11f, 9f, inter, DIM, 3.6f, Gravity.CENTER_HORIZONTAL)
        label("people", content.trip.people.toString(), 418f, 34f, 101f, 53f, 40f, monoBold, INK,
            gravity = Gravity.CENTER_HORIZONTAL, baseline = 42f)
        label("persons", "PERSONS", 418f, 84f, 101f, 9f, 7.5f, inter, DIM, 2.25f, Gravity.CENTER_HORIZONTAL)
        distance = label("distance", content.odometer(5, 0), 418f, 114f, 101f, 22f, 17f, monoBold, INK,
            1.02f, Gravity.CENTER_HORIZONTAL, 17.85f)
        label("km", "KM DRIVEN", 418f, 138f, 100f, 9f, 7.5f, inter, DIM, 2.25f, Gravity.CENTER_HORIZONTAL)
        label("barcode", content.barcodeText, 418f, 204f, 100f, 9f, 7f, mono, DIM, .7f,
            Gravity.CENTER_HORIZONTAL, 7.35f)
        stamp.tag = "b3.ticket.stamp"
        stamp.rotation = -8f
        addView(stamp)
        setProgress(0)
    }

    fun setProgress(elapsedMillis: Long) {
        elapsed = elapsedMillis
        val enter = CreditPlayback.phase(elapsed, 0, 650)
        translationY = (1f - enter) * (1f - enter) * 384f * scale
        val printed = CreditPlayback.phase(elapsed, 800, 1250)
        distance.alpha = printed
        distance.scaleX = 1.12f - .12f * printed; distance.scaleY = distance.scaleX
        distance.translationY = (1f - printed) * 8f * scale
        val ended = CreditPlayback.phase(elapsed, 1500, 1800)
        stamp.alpha = .9f * ended
        stamp.scaleX = 1.2f - .2f * ended; stamp.scaleY = stamp.scaleX
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(width, height)
        scale = min(width / 540f, height / 236f)
        slots.forEach { item ->
            val textPaint = TextPaint(item.view.paint).apply { textSize = item.size }
            val size = item.size * min(1f, item.w / textPaint.measureText(item.view.text.toString()).coerceAtLeast(1f))
            item.view.setReferenceTypography(size, item.h, item.baseline * size / item.size, scale)
            item.view.measure(exact(px(item.w)), exact(px(item.h)))
        }
        stamp.measure(exact(px(142f)), exact(px(40f)))
        setProgress(elapsed)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        slots.forEach { item ->
            val x = px(item.x); val y = px(item.y)
            item.view.layout(x, y, x + item.view.measuredWidth, y + item.view.measuredHeight)
        }
        // The unrotated 142 x 40 body gives the Figma 146.185 x 59.373 rotated bounds.
        val x = px(286.0925f); val y = px(165.6865f)
        stamp.layout(x, y, x + stamp.measuredWidth, y + stamp.measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val saved = canvas.save(); canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL; paint.color = PAPER
        paint.setShadowLayer(24f, 0f, 10f, 0x59000000)
        canvas.drawRoundRect(0f, 0f, 540f, 236f, 12f, 12f, paint)
        paint.clearShadowLayer()
        val header = canvas.save(); canvas.clipPath(outline)
        paint.color = INK; canvas.drawRect(0f, 0f, 396f, 32f, paint)
        canvas.restoreToCount(header)
        paint.color = 0x2E2A2622
        canvas.drawRect(20f, 124f, 376f, 125f, paint)
        canvas.drawRect(418f, 104f, 518f, 105f, paint)
        val cut = canvas.save(); canvas.translate(396f, 18f); canvas.rotate(90f); canvas.translate(0f, -.6f)
        canvas.drawPicture(perforation); canvas.restoreToCount(cut)
        val topNotch = canvas.save(); canvas.translate(385f, -11f); canvas.drawPicture(notch); canvas.restoreToCount(topNotch)
        val bottomNotch = canvas.save(); canvas.translate(385f, 225f); canvas.drawPicture(notch); canvas.restoreToCount(bottomNotch)
        paint.color = INK
        BARCODE_X.forEachIndexed { i, x -> canvas.drawRect(x.toFloat(), 166f, (x + BARCODE_W[i]).toFloat(), 200f, paint) }
        canvas.restoreToCount(saved)
    }

    private fun label(key: String, copy: String, x: Float, y: Float, w: Float, h: Float,
        size: Float, font: Typeface, color: Int, tracking: Float = 0f,
        gravity: Int = Gravity.START, baseline: Float = size * .97f): ReferenceDesignLabel {
        val view = ReferenceDesignLabel(context).apply {
            tag = "b3.ticket.$key"; text = copy; typeface = font; setTextColor(color)
            fontVariationSettings = when (font) {
                monoBold -> "'wght' 700"
                mono -> "'wght' 400"
                inter -> "'wght' 600, 'opsz' 14"
                else -> null
            }
            includeFontPadding = false; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 0); this.gravity = gravity; letterSpacing = tracking / size
            setReferenceTypography(size, h, baseline, 1f)
        }
        slots += Slot(view, x, y, w, h, size, baseline); addView(view)
        return view
    }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(1), MeasureSpec.EXACTLY)
    private fun px(value: Float) = (value * scale).roundToInt()

    private companion object {
        const val PAPER = 0xFFF2ECDF.toInt()
        const val INK = 0xFF2A2622.toInt()
        const val DIM = 0xFF8A8278.toInt()
        const val RED = 0xFFB5372B.toInt()
        val BARCODE_X = intArrayOf(420, 425, 431, 436, 442, 447, 452, 458, 463, 469, 474, 479, 485, 490, 495, 501)
        val BARCODE_W = intArrayOf(2, 3, 1, 1, 2, 1, 3, 2, 1, 1, 2, 3, 1, 1, 3, 1)
    }
}

@SuppressLint("ViewConstructor")
private class TicketStamp(context: Context, endDate: String, inter: Typeface, noto: Typeface) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = ReferenceDesignLabel(context).apply {
        text = "THE END"; typeface = inter; setTextColor(0xFFB5372B.toInt()); gravity = Gravity.CENTER_HORIZONTAL
        fontVariationSettings = "'wght' 600, 'opsz' 14"
        includeFontPadding = false; letterSpacing = 2.52f / 14f; maxLines = 1
    }
    private val date = ReferenceDesignLabel(context).apply {
        text = "상영 종료 · $endDate"; typeface = noto; setTextColor(0xFFB5372B.toInt()); gravity = Gravity.CENTER_HORIZONTAL
        includeFontPadding = false; letterSpacing = 2.25f / 7.5f; maxLines = 1
    }
    private var scale = 1f
    init { setWillNotDraw(false); addView(title); addView(date); contentDescription = "THE END, 상영 종료 $endDate" }
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w); val height = MeasureSpec.getSize(h); setMeasuredDimension(width, height)
        scale = min(width / 142f, height / 40f)
        title.setReferenceTypography(14f, 17f, 13.58f, scale)
        date.setReferenceTypography(7.5f, 10f, 7.275f, scale)
        title.measure(exact(118f), exact(17f)); date.measure(exact(118f), exact(10f))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        title.layout(px(12f), px(6f), px(130f), px(23f))
        date.layout(px(12f), px(23f), px(130f), px(33f))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val saved = canvas.save(); canvas.scale(scale, scale)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = 0xD9B5372B.toInt()
        canvas.drawRoundRect(1f, 1f, 141f, 39f, 6f, 6f, paint)
        canvas.restoreToCount(saved)
    }
    private fun px(value: Float) = (value * scale).roundToInt()
    private fun exact(value: Float) = MeasureSpec.makeMeasureSpec(px(value).coerceAtLeast(1), MeasureSpec.EXACTLY)
}
