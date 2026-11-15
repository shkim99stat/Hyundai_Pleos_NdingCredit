package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class TravelHomeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun a2MatchesTheReferenceAtFullHalfAndLiveWindowSizes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for ((width, height) in listOf(1700 to 1184, 850 to 592, 1682 to 1210)) {
                    var starts = 0
                    var archives = 0
                    var replayed: Trip? = null
                    val root = A2LegacyTravelHomeView(activity, DemoContent.trips,
                        { starts++ }, { archives++ }, { replayed = it })
                    AppTypography.applyTo(root)
                    root.measure(exact(width), exact(height))
                    root.layout(0, 0, width, height)
                    fun node(tag: String) = requireNotNull(root.findViewWithTag<View>(tag))
                    fun copy(tag: String) = (node(tag) as TextView).text.toString()
                    fun bounds(tag: String) = Rect().also { rect ->
                        val view = node(tag)
                        view.getDrawingRect(rect)
                        root.offsetDescendantRectToMyCoords(view, rect)
                    }

                    val scale = min(width / 1700f, height / 1184f)
                    for ((tag, size) in mapOf(
                        "home.featured.photo" to (593f to 937f),
                        "home.distance.map" to (660f to 454.706f),
                        "home.distance.icon" to (31.219f to 31.509f),
                        "home.destination.icon" to (21.9835f to 35.2902f),
                        "home.destination.line" to (128.732f to 85.7389f),
                        "home.destination.dot" to (18f to 18f),
                        "home.duration.icon" to (35f to 35f),
                        "home.duration.art" to (110f to 110f),
                    )) {
                        val image = node(tag) as ImageView
                        assertEquals(tag, (size.first * scale).toDouble(), image.width.toDouble(), 1.0)
                        assertEquals(tag, (size.second * scale).toDouble(), image.height.toDouble(), 1.0)
                        assertNotNull(tag, image.drawable)
                    }
                    assertEquals("N딩 크레딧", copy("home.title"))
                    assertNull(root.findViewWithTag<View>("home.update"))
                    assertEquals((28f * scale).toDouble(), node("home.replay.icon").width.toDouble(), 1.0)
                    assertEquals("전체 상영관·9편", node("home.archive").contentDescription.toString())
                    assertEquals("9편", copy("home.archive.label.2"))
                    assertEquals("여행 기록 시작하기", copy("home.start"))
                    assertEquals("최신 상영작", copy("home.featured.badge"))
                    assertEquals("속초 1박 2일", copy("home.featured.title"))
                    assertEquals("2026.10.25 (일) · 1박 2일", copy("home.featured.date"))
                    assertEquals("여행 상세 보기", copy("home.replay.label"))
                    assertEquals("2,140", copy("home.distance.value"))
                    assertEquals("km", copy("home.distance.unit"))
                    assertEquals("강릉", copy("home.destination.value"))
                    assertEquals("4번 다녀왔어요", copy("home.destination.detail"))
                    assertEquals("36", copy("home.duration.hours"))
                    assertEquals("30", copy("home.duration.minutes"))
                    assertTrue(bounds("home.featured").contains(bounds("home.replay")))
                    assertTrue(bounds("home.featured").right < bounds("home.distance").left)
                    assertTrue(bounds("home.distance").bottom < bounds("home.destination").top)
                    assertTrue(bounds("home.destination").right < bounds("home.duration").left)
                    assertTrue(bounds("home.duration").bottom < bounds("home.start").top)
                    assertTrue(node("home.start").performClick())
                    assertTrue(node("home.archive").performClick())
                    assertTrue(node("home.replay").performClick())
                    assertEquals(1, starts)
                    assertEquals(1, archives)
                    assertEquals(DemoContent.homeTrips.first(), replayed)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    val destination = bounds("home.destination")
                    assertEquals(0xFFECF8F4.toInt(), bitmap.getPixel(destination.centerX(), destination.centerY()))
                    val duration = bounds("home.duration")
                    assertEquals(0xFFF4EFFF.toInt(), bitmap.getPixel(duration.centerX(), duration.centerY()))
                    val action = bounds("home.start")
                    assertEquals(0xFF0066FF.toInt(), bitmap.getPixel(action.left + action.width() / 4, action.centerY()))
                    val folder = File(activity.filesDir, "design-verification").apply { mkdirs() }
                    File(folder, "a2-406-${width}x$height.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun emptyHomeOffersPreparationAndArchive() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var started = false
                var openedArchive = false
                val view = A2HomeView(activity, { started = true }, { openedArchive = true })
                view.measure(exact(1680), exact(1184))
                view.layout(0, 0, 1680, 1184)
                assertEquals("첫 여행을 기록해 볼까요?", view.findViewWithTag<TextView>("home.empty.title").text.toString())
                assertTrue(view.findViewWithTag<View>("home.start").performClick())
                assertTrue(view.findViewWithTag<View>("home.archive").performClick())
                assertTrue(started && openedArchive)
                val bitmap = Bitmap.createBitmap(1680, 1184, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val folder = File(activity.filesDir, "design-verification").apply { mkdirs() }
                File(folder, "a2-empty.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }

    @Test fun realTripsWithoutMeasurementsDoNotInheritReferenceMetrics() {
        val trip = Trip(id = "a2-real-trip", name = "강릉 여행", people = 2)
        val missing = TravelHomeSummary.from(listOf(trip)) { null }
        assertEquals("기록 없음", missing.distance)
        assertEquals("지역 기록 없음", missing.region)
        assertNull(missing.drivingMinutes)
        val recorded = TravelHomeSummary.from(listOf(trip)) {
            TripRecording(it.id, 1L, endedAt = 2L, distanceMeters = 12_500.0, movingMillis = 95 * 60_000L)
        }
        assertEquals("13km", recorded.distance)
        assertEquals("지역 기록 없음", recorded.region)
        assertEquals(95L, recorded.drivingMinutes)
        val demo = TravelHomeSummary.from(DemoContent.trips) { null }
        assertEquals("2,140km", demo.distance)
        assertEquals(4, demo.regionVisits)
        assertEquals(2_190L, demo.drivingMinutes)
        assertTrue(demo.isReference)
    }

    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()

    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
