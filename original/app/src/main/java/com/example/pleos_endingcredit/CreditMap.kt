package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.roundToInt

/** Renders the same map composition through the opening, route reveal, and distance caption. */
internal class CreditMap(context: Context, private val content: CreditContent) {
    private val sample = content.demo && content.trip.id in COAST_SAMPLE_TRIPS
    private val points = content.recording?.points?.filter(TripPoint::valid).orEmpty()
    private val samplePoints = if (sample) loadSampleRoute(context) else emptyList()
    val hasRoute = points.size >= 2 || samplePoints.size >= 2
    private val route = CreditRouteTerrain(
        context, points.ifEmpty { samplePoints }, content.recording?.visibleStops.orEmpty(),
        sample, showOverview = content.demo,
    )
    private val artboard = RectF(0f, 0f, 680f, 474f)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val attribution = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF395365.toInt()
        textSize = 7f
        setShadowLayer(2f, 0f, 0f, Color.WHITE)
    }
    private val blurred by lazy(LazyThreadSafetyMode.NONE) { blurredMap() }

    fun draw(canvas: Canvas, scene: Int, elapsed: Long) {
        val zoom = when (scene) {
            CreditPlayback.OPENING -> 1.13f
            CreditPlayback.ROUTE -> {
                val p = CreditPlayback.phase(elapsed, 0, 1_250)
                1.13f - .13f * p * p * (3f - 2f * p)
            }
            else -> 1f
        }
        val progress = when (scene) {
            CreditPlayback.OPENING -> 0f
            CreditPlayback.ROUTE -> CreditPlayback.phase(elapsed, 0L, CreditPlayback.ROUTE_DRAW_MILLIS)
            else -> 1f
        }
        val saved = canvas.save()
        canvas.scale(zoom, zoom, 340f, 237f)
        drawBase(canvas, progress, scene == CreditPlayback.ROUTE)
        canvas.restoreToCount(saved)
        if (scene == CreditPlayback.SUMMARY) {
            val fade = CreditPlayback.phase(elapsed, 0L, 530L)
            if (fade > 0f) {
                val layer = blurred
                bitmapPaint.alpha = (255 * fade).roundToInt()
                canvas.drawBitmap(layer, null, artboard, bitmapPaint)
                bitmapPaint.alpha = 255
                canvas.drawColor(Color.argb((44 * fade).roundToInt(), 240, 248, 250))
            }
        }
    }

    private fun drawBase(canvas: Canvas, progress: Float, showMarker: Boolean) {
        canvas.drawColor(PANEL)
        route.draw(canvas, progress, showMarker)
    }

    fun drawAttribution(canvas: Canvas) {
        if (route.hasMap) {
            canvas.drawText("© OpenStreetMap contributors · OpenFreeMap · OpenMapTiles", 16f, 459f, attribution)
        }
    }

    private fun blurredMap(): Bitmap {
        val w = 136
        val h = 95
        val raster = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(raster).apply {
            scale(w / 680f, h / 474f)
            drawBase(this, 1f, false)
        }
        val source = IntArray(w * h)
        val softened = IntArray(source.size)
        raster.getPixels(source, 0, w, 0, 0, w, h)
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (dy in -1..1) for (dx in -1..1) {
                val pixel = source[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
                r += Color.red(pixel)
                g += Color.green(pixel)
                b += Color.blue(pixel)
            }
            softened[y * w + x] = Color.rgb(r / 9, g / 9, b / 9)
        }
        raster.setPixels(softened, 0, w, 0, 0, w, h)
        return raster
    }

    private companion object {
        const val PANEL = 0xFFDFE9ED.toInt()
        val COAST_SAMPLE_TRIPS = setOf("demo-gangneung", "demo-sokcho", "demo-home-sokcho")
        private var cachedSample: List<TripPoint>? = null

        fun loadSampleRoute(context: Context): List<TripPoint> = cachedSample ?: context.assets
            .open("b3/flat-map/east-coast-route.csv").bufferedReader().useLines { rows ->
                rows.mapIndexed { index, row ->
                    val (lon, lat) = row.split(',')
                    TripPoint(lat.toDouble(), lon.toDouble(), index + 1L)
                }.toList()
            }.also { cachedSample = it }
    }
}
