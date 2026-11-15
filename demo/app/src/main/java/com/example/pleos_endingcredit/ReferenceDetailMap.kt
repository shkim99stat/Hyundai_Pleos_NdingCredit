package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color

/** Artwork for the built-in sample trips. Recorded trips use the live route and visit data. */
internal class ReferenceDetailMap(context: Context) : ResultDesignSurface(context, 1696f, 1180f) {
    private var zoom = 1f
    private var expanded = false
    private val routeViews = mutableListOf<android.view.View>()
    private val numberViews = mutableListOf<android.view.View>()

    init {
        place(travelImage(context, "f4-basemap.png", 1500), -17f, -68f, 1713f, 1270.63f)
        routeViews += place(asset("route.svg", "f4.sample.route", "f4_834"), 249f, 323f, 430f, 724.738f)
        val pins = listOf(315f to 786f, 297f to 587f, 346f to 463f, 552f to 375f, 504f to 605f)
        pins.forEachIndexed { index, (x, y) ->
            numberViews += place(asset("pin-ring.svg", "f4.sample.ring.$index", "f4_834"), x - 18f, y - 18f, 36f, 36f)
            numberViews += place(asset("pin.svg", "f4.sample.marker.$index", "f4_834"), x - 30f, y - 64f, 60f, 72f)
            numberViews += place(label("f4.sample.pin.$index", (index + 1).toString(),
                28f, 700, Color.WHITE, centered = true, typeface = AppTypography.font(context, 700)),
                x - 18f, y - 60f, 36f, 36f)
        }
        routeViews += place(android.widget.FrameLayout(context).apply {
            clipChildren = true
            val car = travelImage(context, "f4-car.png", 800).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_XY
            }
            addView(car, android.widget.FrameLayout.LayoutParams(-1, -1))
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                car.scaleX = 1.5599f
                car.scaleY = 1.3426f
                car.translationX = (0.27995f - .2607f) * width
                car.translationY = (0.1713f - .152f) * height
            }
        }, 561.64f, 790.03f, 91.33f, 59.08f)
    }

    fun selectDay(day: Int) {
        routeViews.forEach { it.alpha = 1f }
        numberViews.forEachIndexed { index, view ->
            val pin = index / 3
            view.visibility = if (day == 0 || (day == 1 && pin < 4) || (day == 2 && pin == 4))
                VISIBLE else INVISIBLE
            if (view is android.widget.TextView) view.text = if (day == 2) "1" else (pin + 1).toString()
        }
    }

    fun zoomBy(step: Int) {
        zoom = (if (step > 0) zoom * 1.25f else zoom / 1.25f).coerceIn(.8f, 3f)
        invalidate()
    }

    fun fit() { zoom = 1f; invalidate() }
    fun setExpanded(value: Boolean) { expanded = value; fit() }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        val center = surface.left + (if (expanded) 848f else 470f) * drawingScale
        canvas.scale(zoom, zoom, center, surface.centerY())
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}
