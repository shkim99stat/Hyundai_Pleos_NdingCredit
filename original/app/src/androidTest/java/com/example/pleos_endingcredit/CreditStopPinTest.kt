package com.example.pleos_endingcredit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreditStopPinTest {
    @Test fun recordedStopUsesWhiteFillBlackNumberAndBlueRingAfterArrival() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val start = TripPoint(38.0, 128.65, 1_000L)
        val first = TripPoint(38.0, 128.70, 2_000L)
        val excluded = TripPoint(38.0, 128.725, 3_000L)
        val end = TripPoint(38.0, 128.75, 4_000L)
        val trip = Trip(id = "pin-qa", name = "정차 핀 검증", people = 1, createdOn = "2026-11-08")
        val record = TripRecording(
            trip.id, start.at, end.at, points = listOf(start, first, excluded, end),
            stops = listOf(
                RecordedStop("visible", first.at, first),
                RecordedStop("excluded", excluded.at, excluded, excluded = true),
            ),
        )
        val map = CreditMap(context, CreditContent(trip, record))
        val baseline = CreditMap(context, CreditContent(trip, record.copy(stops = emptyList())))
        fun render(map: CreditMap, elapsed: Long) = Bitmap.createBitmap(1360, 948, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.scale(2f, 2f)
            map.draw(canvas, CreditPlayback.ROUTE, elapsed)
        }
        fun pixels(bitmap: Bitmap, x: Int, y: Int, size: Int) = IntArray(size * size).apply {
            bitmap.getPixels(this, 0, size, x, y, size, size)
        }
        val before = render(map, 1_600L)
        val beforeBaseline = render(baseline, 1_600L)
        val finished = render(map, CreditPlayback.ROUTE_DRAW_MILLIS)
        val finishedBaseline = render(baseline, CreditPlayback.ROUTE_DRAW_MILLIS)
        try {
            assertArrayEquals(pixels(beforeBaseline, 662, 456, 36), pixels(before, 662, 456, 36))
            assertEquals(Color.WHITE, finished.getPixel(672, 468))
            val marker = pixels(finished, 664, 458, 32)
            assertTrue(marker.any { Color.red(it) < 45 && Color.green(it) < 45 && Color.blue(it) < 45 })
            assertTrue(marker.any { Color.red(it) in 18..30 && Color.green(it) in 120..140 && Color.blue(it) > 240 })
            assertArrayEquals(pixels(finishedBaseline, 908, 456, 36), pixels(finished, 908, 456, 36))
            val folder = File(context.filesDir, "b3-pin-verification").apply { mkdirs() }
            File(folder, "recorded-stop.png").outputStream().use {
                finished.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            listOf(before, beforeBaseline, finished, finishedBaseline).forEach(Bitmap::recycle)
        }
    }
}

