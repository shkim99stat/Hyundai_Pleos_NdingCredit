package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.tan

/** The B3 route and the bundled flat maps share a Web Mercator projection. */
internal class CreditRouteTerrain(
    context: Context,
    points: List<TripPoint>,
    stops: List<RecordedStop>,
    private val sample: Boolean,
    showOverview: Boolean,
) {
    private data class Position(val x: Double, val y: Double)
    private data class Pin(val x: Float, val y: Float, val number: String, val progress: Float)
    private data class Atlas(val bitmap: Bitmap, val bounds: List<Double>) {
        fun contains(x0: Double, y0: Double, x1: Double, y1: Double) =
            x0 >= bounds[0] && y0 >= bounds[1] && x1 <= bounds[2] && y1 <= bounds[3]
    }

    private val route = Path()
    private val segment = Path()
    private val measure = PathMeasure()
    private val atlasPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val atlasRect = RectF()
    private val markerPosition = FloatArray(2)
    private val endpoint = FloatArray(4)
    private val labels = mutableListOf<Triple<String, Float, Float>>()
    private var length = 0f
    private var atlas: Atlas? = null
    private var pins = emptyList<Pin>()
    val hasMap get() = atlas != null

    init {
        val projected = points.map { project(it.latitude, it.longitude) }
        if (projected.isNotEmpty()) {
            val minX = projected.minOf { it.x }
            val maxX = projected.maxOf { it.x }
            val minY = projected.minOf { it.y }
            val maxY = projected.maxOf { it.y }
            val coast = loadAtlas(context, "east-coast")?.takeIf { it.contains(minX, minY, maxX, maxY) }
            atlas = coast ?: loadAtlas(context, "korea")?.takeIf { it.contains(minX, minY, maxX, maxY) }
            val local = coast?.takeIf { sample || maxY - minY > .00085 }
            val localBounds = local?.bounds
            val scale = if (localBounds != null) {
                maxOf(680 / (localBounds[2] - localBounds[0]), 474 / (localBounds[3] - localBounds[1]))
            } else {
                min(492 / (maxX - minX).coerceAtLeast(.00075), 324 / (maxY - minY).coerceAtLeast(.00045))
            }
            val centerX = if (localBounds != null) (localBounds[0] + localBounds[2]) / 2 else (minX + maxX) / 2
            val centerY = if (localBounds != null) (localBounds[1] + localBounds[3]) / 2 else (minY + maxY) / 2
            fun x(value: Double) = (340 + (value - centerX) * scale).toFloat()
            fun y(value: Double) = (237 + (value - centerY) * scale).toFloat()

            atlas?.bounds?.let { atlasRect.set(x(it[0]), y(it[1]), x(it[2]), y(it[3])) }
            val traveled = FloatArray(projected.size)
            for ((i, p) in projected.withIndex()) {
                if (i == 0 || points[i].gapBefore) route.moveTo(x(p.x), y(p.y))
                else route.lineTo(x(p.x), y(p.y))
                if (i == 0) continue
                val before = projected[i - 1]
                val step = if (points[i].gapBefore) 0f else hypot(x(p.x) - x(before.x), y(p.y) - y(before.y))
                traveled[i] = traveled[i - 1] + step
            }
            length = traveled.last()
            if (sample) {
                endpoint[0] = x(projected.first().x)
                endpoint[1] = y(projected.first().y)
                endpoint[2] = x(projected.last().x)
                endpoint[3] = y(projected.last().y)
                labels += Triple("속초", endpoint[0] + 11f, endpoint[1] + 3f)
                labels += Triple("강릉", endpoint[2] + 11f, endpoint[3] + 3f)
                val sea = project(38.046, 129.04)
                labels += Triple("동해", x(sea.x), y(sea.y))
            }
            if (length > 0f) {
                pins = stops.mapIndexedNotNull { i, stop ->
                    val point = stop.point?.takeIf(TripPoint::valid) ?: return@mapIndexedNotNull null
                    val p = project(point.latitude, point.longitude)
                    val at = points.indexOfFirst { it.at >= stop.at }
                    Pin(x(p.x), y(p.y), (i + 1).toString(), (traveled.getOrNull(at) ?: length) / length)
                }
            }
        } else if (showOverview) {
            atlas = loadAtlas(context, "korea")
            atlas?.let {
                val width = 680f
                val height = width * it.bitmap.height / it.bitmap.width
                atlasRect.set(0f, (474f - height) / 2, width, (474f + height) / 2)
            }
        }
    }

    fun draw(canvas: Canvas, progress: Float, showMarker: Boolean) {
        atlas?.let { canvas.drawBitmap(it.bitmap, null, atlasRect, atlasPaint) }
        if (sample) labels.forEach { (name, x, y) -> drawLabel(canvas, name, x, y) }
        if (length <= 0f || progress <= 0f) return
        var remaining = length * progress
        var markerVisible = false
        measure.setPath(route, false)
        do {
            segment.rewind()
            val drawn = min(remaining, measure.length)
            if (drawn > 0f) {
                measure.getSegment(0f, drawn, segment, true)
                stroke.color = Color.WHITE
                stroke.strokeWidth = 6.5f
                canvas.drawPath(segment, stroke)
                stroke.color = ROUTE_BLUE
                stroke.strokeWidth = 4.5f
                canvas.drawPath(segment, stroke)
                if (showMarker && remaining <= measure.length) {
                    markerVisible = measure.getPosTan(drawn, markerPosition, null)
                }
            }
            remaining -= measure.length
        } while (remaining > 0f && measure.nextContour())

        if (sample) {
            drawMarker(canvas, endpoint[0], endpoint[1])
            if (progress >= 1f) drawMarker(canvas, endpoint[2], endpoint[3])
        }
        if (markerVisible) drawMarker(canvas, markerPosition[0], markerPosition[1])
        for (pin in pins) {
            if (progress < pin.progress) continue
            drawStopPin(canvas, pin)
        }
    }

    private fun drawStopPin(canvas: Canvas, pin: Pin) {
        circle.color = STOP_OUTLINE
        canvas.drawCircle(pin.x, pin.y, 7.6f, circle)
        circle.color = Color.WHITE
        canvas.drawCircle(pin.x, pin.y, 6.1f, circle)

        text.color = STOP_INK
        text.textSize = 9.5f
        text.textAlign = Paint.Align.CENTER
        val numberWidth = text.measureText(pin.number)
        if (numberWidth > 10.5f) text.textSize *= 10.5f / numberWidth
        canvas.drawText(pin.number, pin.x, pin.y - (text.ascent() + text.descent()) / 2, text)
    }

    private fun drawLabel(canvas: Canvas, label: String, x: Float, y: Float) {
        text.textAlign = Paint.Align.LEFT
        text.textSize = 12f
        text.style = Paint.Style.STROKE
        text.strokeWidth = 2.5f
        text.strokeJoin = Paint.Join.ROUND
        text.color = Color.WHITE
        canvas.drawText(label, x, y, text)
        text.style = Paint.Style.FILL
        text.color = 0xFF3F6377.toInt()
        canvas.drawText(label, x, y, text)
    }

    private fun drawMarker(canvas: Canvas, x: Float, y: Float) {
        circle.color = Color.WHITE
        canvas.drawCircle(x, y, 4.8f, circle)
        circle.color = ROUTE_BLUE
        canvas.drawCircle(x, y, 3.3f, circle)
    }

    private companion object {
        const val ROUTE_BLUE = 0xFF1477B5.toInt()
        const val STOP_OUTLINE = 0xFF1682FA.toInt()
        const val STOP_INK = 0xFF212121.toInt()
        val atlases = mutableMapOf<String, Atlas>()

        fun project(latitude: Double, longitude: Double) = Position(
            (longitude + 180) / 360,
            (1 - asinh(tan(Math.toRadians(latitude.coerceIn(-85.0511, 85.0511)))) / PI) / 2,
        )

        fun loadAtlas(context: Context, name: String): Atlas? = atlases[name] ?: runCatching {
            val bounds = context.assets.open("b3/flat-map/$name.bounds").bufferedReader().use { reader ->
                reader.readText().trim().split(',').map(String::toDouble)
            }
            val bitmap = context.assets.open("b3/flat-map/$name.png").use { BitmapFactory.decodeStream(it) }
            Atlas(requireNotNull(bitmap), bounds).also { atlases[name] = it }
        }.getOrNull()
    }
}

