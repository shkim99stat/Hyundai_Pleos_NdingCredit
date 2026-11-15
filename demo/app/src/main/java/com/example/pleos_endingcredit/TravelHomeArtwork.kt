package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import android.widget.ImageView
import com.caverock.androidsvg.SVG
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val TRAVEL_ASSETS = "travel_refresh"

internal fun tripDisplayZone(trip: Trip): ZoneId =
    if (DemoScenario.isDemo(trip.id)) DemoScenario.zone else ZoneId.systemDefault()

internal fun travelLocalDate(at: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

internal fun parseTripDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

internal fun travelSvg(context: Context, filename: String): View = object : View(context) {
    private val picture = context.assets.open("$TRAVEL_ASSETS/$filename").use {
        SVG.getFromInputStream(it).renderToPicture()
    }
    init { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        canvas.drawPicture(picture, RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }
}

internal fun travelImage(context: Context, name: String, maximumEdge: Int) = ImageView(context).apply {
        val path = "$TRAVEL_ASSETS/$name"
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
        val edge = maxOf(options.outWidth, options.outHeight)
        var sample = 1
        while (edge / (sample * 2) >= maximumEdge) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        setImageBitmap(context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) })
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}
