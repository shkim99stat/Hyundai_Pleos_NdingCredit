package com.example.pleos_endingcredit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator

/** An explicitly schematic preview, displayed until the basemap is ready. */
internal class DemoRouteFallbackView(
    context: Context,
    private val recordingStyle: Boolean = false,
    private val detailStyle: Boolean = false,
) : View(context) {
    init { tag="demo.map.fallback" }
    private var expanded = false
    fun expandRecordingMap(value: Boolean) {
        expanded = value
        invalidate()
    }

    private var record: TripRecording? = null
    private var position: TripPoint? = null
    private var motion: ValueAnimator? = null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = 0xFF263142.toInt()
    private val detailPin by lazy { detailAsset("pin.svg") }
    private val detailRing by lazy { detailAsset("pin-ring.svg") }
    private val pinBounds = RectF()

    private fun detailAsset(name: String) = context.assets.open("f4_834/$name").use {
        com.caverock.androidsvg.SVG.getFromInputStream(it).renderToPicture()
    }

    private fun drawDetailStop(canvas: Canvas, point: PointF, number: Int) {
        val scale = width / 1696f
        pinBounds.set(point.x - 18f * scale, point.y - 18f * scale, point.x + 18f * scale, point.y + 18f * scale)
        canvas.drawPicture(detailRing, pinBounds)
        pinBounds.set(point.x - 30f * scale, point.y - 64f * scale, point.x + 30f * scale, point.y + 8f * scale)
        canvas.drawPicture(detailPin, pinBounds)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 28f * scale
        canvas.drawText(number.toString(), point.x, point.y - 42f * scale - (paint.ascent() + paint.descent()) / 2, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    fun update(value: TripRecording, animationMillis: Long) {
        val from = position
        val to = value.latest
        record = value
        motion?.cancel()
        motion = null
        if (from == null || to == null || animationMillis <= 0 || from.at >= to.at) {
            position = to
            invalidate()
            return
        }
        val track = listOf(from) + value.points.filter { it.at > from.at && it.at < to.at } + to
        var next = 1
        motion = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationMillis
            interpolator = LinearInterpolator()
            addUpdateListener {
                val at = from.at + ((to.at - from.at) * it.animatedFraction.toDouble()).toLong()
                while (next < track.lastIndex && track[next].at < at) next++
                val before = track[next - 1]
                val after = track[next]
                position = if (at >= to.at) to else
                    before.toward(after, (at - before.at).toDouble() / (after.at - before.at)).copy(at = at)
                invalidate()
            }
            start()
        }
    }

    fun settlePosition() {
        motion?.cancel()
        motion = null
        position = record?.latest
        invalidate()
    }

    override fun onDetachedFromWindow() {
        settlePosition()
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(if (recordingStyle) 0xFFF0F1F5.toInt() else 0xFFF2F2F2.toInt())
        val w=width * if ((recordingStyle || detailStyle) && !expanded) .53f else 1f
        val h=height.toFloat()
        fun xy(lat:Double,lng:Double)=PointF(w*(.12f+((lng-126.9)/2.2*.73).toFloat()),h*(.20f+((38.3-lat)/1.1*.63).toFloat()))
        paint.color = if (recordingStyle) 0xFFB4DAF7.toInt() else 0xFFC4C7C9.toInt()
        canvas.drawRect(w * .85f, 0f, width.toFloat(), h, paint)
        paint.textSize=(w/34).coerceAtLeast(11f)
        listOf(Triple(37.5665,126.978,"서울"),Triple(38.2045,128.5905,"속초"),Triple(37.8055,128.907,"강릉")).forEach { (lat,lng,name)->
            val p=xy(lat,lng);paint.color=ink;canvas.drawCircle(p.x,p.y,5f,paint);canvas.drawText(name,p.x+8,p.y-12,paint)
        }
        val points=record?.points.orEmpty()
        if(points.isNotEmpty()) {
            val current = position ?: points.last()
            val path = Path()
            for ((index, point) in points.withIndex()) {
                if (recordingStyle && point.at >= current.at) break
                val p = xy(point.latitude, point.longitude)
                if (index == 0 || point.gapBefore) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            val start = xy(points.first().latitude, points.first().longitude)
            val end = xy(points.last().latitude, points.last().longitude)
            val markerPoint = xy(current.latitude, current.longitude)
            if (recordingStyle && points.first().at < current.at && !current.gapBefore) path.lineTo(markerPoint.x, markerPoint.y)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = if (recordingStyle || detailStyle) 0xFF0066FF.toInt() else 0xFF0CC8B7.toInt()
            if (!recordingStyle && !detailStyle && start != end) {
                paint.shader = LinearGradient(start.x, start.y, end.x, end.y,
                    intArrayOf(0xFF0CEBD7.toInt(), 0xFF20BFF0.toInt(), 0xFF1074FA.toInt()),
                    null, Shader.TileMode.CLAMP)
            }
            canvas.drawPath(path, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
            val stops = record?.visibleStops.orEmpty()
            val markers = RouteStopMarkers(stops)
            stops.forEachIndexed { index, stop ->
                stop.point?.let { point ->
                    val p = xy(point.latitude, point.longitude)
                    if (detailStyle) {
                        drawDetailStop(canvas, p, index + 1)
                        return@let
                    }
                    val pending = stop.selectedPlaceId == null && stop.userLabel == null
                    val last = markers.isLast(stop) && !pending
                    paint.color = markers.color(stop)
                    canvas.drawCircle(p.x, p.y, 10f, paint)
                    if (!last) {
                        paint.color = Color.WHITE
                        canvas.drawCircle(p.x, p.y, 8f, paint)
                    }
                    paint.color = if (last) Color.WHITE else ink
                    paint.textSize = 12f
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(if (pending) "?" else (index + 1).toString(),
                        p.x, p.y - (paint.ascent() + paint.descent()) / 2f, paint)
                    paint.textAlign = Paint.Align.LEFT
                }
            }
            paint.color = Color.WHITE
            canvas.drawCircle(markerPoint.x, markerPoint.y, 9f, paint)
            paint.color = 0xFF1074FA.toInt()
            canvas.drawCircle(markerPoint.x, markerPoint.y, 6f, paint)
        }
        paint.color=ink;paint.textSize=(w/42).coerceAtLeast(10f)
        canvas.drawText("도로 데이터 © OpenStreetMap contributors",w*.08f,h*.92f,paint)
    }
}
