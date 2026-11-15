package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.appcompat.widget.AppCompatImageView
import java.time.LocalDate
import kotlin.math.roundToInt

/** F1-only presentation of existing bundled trips. The trip store and demo playback keep their own data. */
internal class F1SamplePosters(private val context: Context) {
    data class Poster(
        val tripId: String,
        val title: String,
        val file: String,
        val crop: RectF,
        val showPlay: Boolean = false,
        val favorite: Boolean = false,
        val duration: String = "1박 2일",
    )

    operator fun get(tripId: String): Poster? = byId[tripId]

    fun order(trip: Trip): Int = orderById[trip.id] ?: Int.MAX_VALUE

    fun title(trip: Trip): String {
        val sample = byId[trip.id] ?: return trip.name
        return if (originalNames[trip.id] == trip.name) sample.title else trip.name
    }

    fun bitmap(file: String, enlarged: Boolean = false): Bitmap {
        if (!enlarged) cache.get(file)?.let { return it }
        val maximumEdge = if (enlarged) 1800f else 1000f
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.assets, "f1_catalog/$file")) { decoder, info, _ ->
            val scale = (maximumEdge / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
        }.also { if (!enlarged) cache.put(file, it) }
    }

    companion object {
        val startDate: LocalDate = LocalDate.of(2026, 10, 24)
        const val LAKE = "lake.jpg"
        val lakeCrop = RectF(-.4312f, -26.082f, 391.5688f, 423.57f)
        private val squareCrop = RectF(-51f, -37f, 443f, 457f)
        private val posters = listOf(
            Poster("demo-home-sokcho", "1주년 여행", LAKE, lakeCrop, showPlay = true),
            Poster("demo-gyeongju", "경주 여행", "gyeongju.png", squareCrop,
                showPlay = true, favorite = true, duration = "2박 3일"),
            Poster("demo-home-jeju", "⭐️우정여행❤️", "friends.jpg", RectF(-14f, -37f, 406f, 457f),
                showPlay = true, favorite = true),
            Poster("demo-home-solo", "꽃 이쁘당", "flowers.png", RectF(1.5665f, -37.0005f, 390.4335f, 546.3005f),
                showPlay = true),
            Poster("demo-gangneung", "강릉 여행", "gangneung.png", RectF(0f, 0f, 392f, 696.889f), favorite = true),
            Poster("demo-home-busan", "전주 여행", "jeonju.png", squareCrop),
            Poster("demo-home-gapyeong", "가평 여행", "gapyeong.png", squareCrop),
            Poster("demo-sokcho", "여수 여행", "yeosu.png", RectF(-127.856f, -49.1255f, 519.856f, 469.1255f)),
        )
        private val byId = posters.associateBy { it.tripId }
        private val orderById = posters.mapIndexed { index, poster -> poster.tripId to index }.toMap()
        private val originalNames = DemoContent.trips.associate { it.id to it.name }
        private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
    }
}

/** Render the Figma image layer inside the clipped card, with its original crop. */
internal class F1PosterPhoto(context: Context) : AppCompatImageView(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private var sample: F1SamplePosters.Poster? = null
    private var sampleImage: Bitmap? = null
    private var backgroundImage: Bitmap? = null

    fun show(bitmap: Bitmap?, poster: F1SamplePosters.Poster? = null, background: Bitmap? = null) {
        sample = poster
        sampleImage = bitmap
        backgroundImage = background
        setBackgroundColor(if (poster == null) 0xFF686A72.toInt() else Color.WHITE)
        if (bitmap == null) setImageDrawable(null) else setImageBitmap(bitmap)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val poster = sample
        val cover = sampleImage
        if (poster == null || cover == null) {
            super.onDraw(canvas)
            return
        }
        backgroundImage?.let { drawLayer(canvas, it, F1SamplePosters.lakeCrop, fit = true) }
        drawLayer(canvas, cover, poster.crop, fit = poster.file == F1SamplePosters.LAKE)
    }

    private fun drawLayer(canvas: Canvas, bitmap: Bitmap, crop: RectF, fit: Boolean) {
        destination.set(crop)
        if (!fit) {
            val zoom = maxOf(crop.width() / bitmap.width, crop.height() / bitmap.height)
            destination.inset((crop.width() - bitmap.width * zoom) / 2, (crop.height() - bitmap.height * zoom) / 2)
        }
        val saved = canvas.save()
        canvas.scale(width / 392f, height / 420f)
        canvas.clipRect(crop)
        canvas.drawBitmap(bitmap, null, destination, paint)
        canvas.restoreToCount(saved)
    }
}
