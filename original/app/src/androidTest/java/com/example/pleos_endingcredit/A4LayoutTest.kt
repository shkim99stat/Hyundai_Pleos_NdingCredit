package com.example.pleos_endingcredit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.min

/** Current C1, C1-1, C1-2 replace the former A4 result mock. */
@RunWith(AndroidJUnit4::class)
class C1ResultTest {
    @Test fun sourceAssetsGeometryActionsAndModalsRenderAtThreeSizes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val trip = DemoContent.trips.first()
            val content = TripResultContent(trip, null)
            for ((width, height) in listOf(1696 to 1180, 848 to 590, 1682 to 1210)) {
                var homes = 0; var costs = 0; var edits = 0; var replays = 0
                val root = C1TripResultView(activity, content, { homes++ }, { costs++ }, { edits++ }, { replays++ })
                measure(root, width, height)
                val scale = min(width / 1696f, height / 1180f)
                val viewport = root.findViewWithTag<View>("c1.map.viewport")
                assertEquals(1696 * scale, viewport.width.toFloat(), 1.1f)
                assertEquals(1180 * scale, viewport.height.toFloat(), 1.1f)
                assertEquals("23km", root.findViewWithTag<TextView>("c1.stat.1").text.toString())
                assertEquals("횡성휴게소(강릉방향)", root.findViewWithTag<TextView>("c1.place.name.0").text.toString())
                assertEquals("강원특별자치도 강릉시 금성로 21",
                    root.findViewWithTag<TextView>("c1.place.address.1").text.toString())
                val replay = root.findViewWithTag<View>("c1.replay")
                val cost = root.findViewWithTag<View>("c1.costs")
                assertEquals(315f * scale, cost.width.toFloat(), 1.1f)
                assertTrue(replay.right < cost.left)
                assertEquals("확인이 필요한 곳 2곳",
                    root.findViewWithTag<View>("c1.places.review").contentDescription.toString())
                assertEquals(android.view.View.INVISIBLE,
                    root.findViewWithTag<View>("c1.swipe.delete.demo-rest").visibility)
                val home = root.findViewWithTag<View>("c1.home")
                val tabs = root.findViewWithTag<View>("c1.tabs")
                val timeline = root.findViewWithTag<View>("c1.places.timeline")
                assertTrue(home.bottom < tabs.top)
                assertTrue(home.bottom < timeline.top)
                assertTrue(replay.performClick())
                assertTrue(cost.performClick())
                assertEquals(1, replays)
                assertEquals(1, costs)
                root.findViewWithTag<View>("c1.map.expand").performClick()
                measure(root, width, height)
                assertEquals(1696 * scale, viewport.width.toFloat(), 1.1f)
                assertFalse(root.findViewWithTag<View>("c1.places.timeline").isShown)
                root.findViewWithTag<View>("c1.map.expand").performClick()
                measure(root, width, height)
                assertEquals(1696 * scale, viewport.width.toFloat(), 1.1f)
                render(activity.filesDir, "c1-${width}x$height", root)

                val places = ResultPlacesView(activity, C1Places.entries(content), {}, {})
                val composition = android.widget.FrameLayout(activity).apply {
                    addView(C1TripResultView(activity, content, {}, {}, {}, {}), android.widget.FrameLayout.LayoutParams(-1,-1))
                    addView(C1PlacesOverlay(activity, places, null, {}), android.widget.FrameLayout.LayoutParams(-1,-1))
                }
                measure(composition, width, height)
                assertEquals(785 * scale, places.height.toFloat(), 1.1f)
                assertEquals(230 * scale + (height - 1180 * scale) / 2, places.top.toFloat(), 1.1f)
                assertEquals("이대로 저장하기", places.findViewWithTag<TextView>("c1.places.done").text.toString())
                render(activity.filesDir, "c1-2-${width}x$height", composition)
            }
            val expected = setOf("35f68.png", "27029.svg", "ed584.svg", "cbf30.svg", "cf854.svg", "68d9f.svg")
            assertEquals(expected, activity.assets.list("c1/v3")!!.toSet())
            expected.forEach { activity.assets.open("c1/v3/$it").use { stream -> assertTrue(stream.available() > 0) } }
            listOf("a03/fe6f7.png", "a4/45823.svg", "a4/5910b.svg").forEach {
                activity.assets.open(it).use { stream -> assertTrue(stream.available() > 0) }
            }
        } }
    }
    @Test fun splitBoundsAndCancelledEditsPreserveTravelPartyAndPlaces() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val trip = DemoContent.trips.first()
            var people = 4
            val costs = ResultCostsView(activity, TripResultContent(trip, null), {}, { people = it })
            measure(costs, 1100, 702)
            val minus = costs.findViewWithTag<View>("c1.costs.minus")
            val plus = costs.findViewWithTag<View>("c1.costs.plus")
            assertFalse(plus.isEnabled)
            repeat(5) { minus.performClick() }; assertEquals(2, people); assertFalse(minus.isEnabled)
            repeat(5) { plus.performClick() }; assertEquals(4, people); assertFalse(plus.isEnabled)
            assertEquals(4, trip.people)
            val content = TripResultContent(trip, null)
            var saved: List<ResultPlace>? = null; var cancelled = 0
            val editor = ResultPlacesView(activity, content.places, { cancelled++ }, { saved = it })
            measure(editor, 1100, 785)
            editor.findViewWithTag<View>("c1.places.remove.${content.places.first().id}").performClick()
            editor.findViewWithTag<View>("c1.places.cancel").performClick()
            assertEquals(1, cancelled); assertNull(saved); assertEquals(9, content.places.size)
            editor.findViewWithTag<View>("c1.places.done").performClick()
            assertEquals(8, saved!!.size)
            val restored = Trip.fromJson(trip.copy(splitPeople = 2, resultPlaces = saved).toJson())
            assertEquals(4, restored.people); assertEquals(2, restored.splitPeople); assertEquals(saved, restored.resultPlaces)
        } }
    }
    @Test fun informationBandsBlurOnlyMapPixelsAndKeepTheMiddleSharp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val pattern = object : View(activity) {
                override fun onDraw(canvas: Canvas) {
                    val paint = android.graphics.Paint()
                    for (x in 0 until width step 32) {
                        paint.color = if (x / 32 % 2 == 0) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                        canvas.drawRect(x.toFloat(), 0f, (x + 32).toFloat(), height.toFloat(), paint)
                    }
                }
            }
            val backdrop = ResultMapBackdrop(activity, pattern)
            measure(backdrop, 874, 1180)
            val image = Bitmap.createBitmap(874, 1180, Bitmap.Config.ARGB_8888)
            backdrop.draw(Canvas(image))
            fun red(x: Int, y: Int) = android.graphics.Color.red(image.getPixel(x, y))
            // A translucent rectangle alone would leave both black pixels at the same value.
            assertTrue(red(256, 90) > red(280, 90))
            assertTrue(red(256, 1000) > red(280, 1000))
            assertEquals(0, red(256, 500)); assertEquals(0, red(280, 500))
            assertEquals(0, red(256, ResultMapBackdrop.TOP_END.toInt() + 1))
            assertEquals(0, red(256, ResultMapBackdrop.BOTTOM_START.toInt() - 1))
            image.recycle()
        } }
    }
    @Test fun realTripsDoNotInheritDemoMeasurementsAndSingleTravellersStayValid() {
        val trip = Trip(name = "실제 기록", people = 1)
        val missing = TripResultContent(trip, null)
        assertEquals("—", missing.distance); assertNull(missing.total); assertTrue(missing.places.isEmpty())
        assertEquals("기록 없음", missing.rain); assertEquals("기록 없음", missing.night)
        assertEquals(1, missing.minPeople); assertEquals(1, missing.splitPeople)
        val actual = TripResultContent(trip, TripRecording(trip.id, 1L, endedAt = 2L, distanceMeters = 12500.0, movingMillis = 3600000))
        assertEquals("12.5", actual.distance); assertEquals("1시간 0분", actual.duration)
        assertNull(actual.energy); assertNull(actual.toll)
        val saved = DemoContent.trips.first().copy(splitPeople = 3, resultPlaces = listOf(ResultPlace("edited", "수정한 장소", "10:20")))
        val summary = TripShare.summary(saved)
        assertTrue(summary.contains("나누는 인원 3명")); assertTrue(summary.contains("수정한 장소"))
        assertTrue(summary.contains("13,854원"))
        val large = saved.copy(resultPlaces = (0..99).map { ResultPlace("$it", "가".repeat(80), "10:20") })
        val qr = TripShare.qr(large); assertEquals(640, qr.width); qr.recycle()
    }
    private fun measure(view: View, width: Int, height: Int) {
        AppTypography.applyTo(view)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0,0,width,height)
    }
    private fun render(folder: File, name: String, view: View) {
        val output = File(folder,"c1-verification").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap)); File(output,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
}
