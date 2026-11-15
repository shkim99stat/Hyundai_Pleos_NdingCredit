package com.example.pleos_endingcredit

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F4StatsTest {
    @Test fun recordedDistancesUseWholeKilometersAcrossThreeSummaryCards() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                fun detail(distanceMeters: Double): F4CatalogDetailView {
                    val trip = Trip(name = "F4 거리 확인", people = 4)
                    val departure = TripPoint(37.751, 128.876, 1_800_000_000_000)
                    val visit = TripPoint(37.773, 128.903, departure.at + 120_000)
                    val record = TripRecording(
                        tripId = trip.id,
                        startedAt = departure.at,
                        points = listOf(departure, visit),
                        latest = visit,
                        stops = listOf(RecordedStop("visit", visit.at, visit, userLabel = "경포호")),
                        distanceMeters = distanceMeters,
                        movingMillis = 9_600_000,
                    )
                    return F4CatalogDetailView(activity, TripResultContent(trip, record), 0,
                        onDay = {}, onClose = {}, onEdit = {}, onReplay = {}, onDelete = {}, onRename = {}, onShare = null,
                    ).apply {
                        measure(View.MeasureSpec.makeMeasureSpec(1696, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(1180, View.MeasureSpec.EXACTLY))
                        layout(0, 0, measuredWidth, measuredHeight)
                    }
                }
                val short = detail(4_800.0).findViewWithTag<TextView>("f4.stat.1.value")
                val long = detail(12_345_678.0)
                assertNull(long.findViewWithTag<View>("f4.place.distance"))
                assertTrue(long.findViewWithTag<TextView>("f4.place.time").text.isNotBlank())
                val distance = long.findViewWithTag<TextView>("f4.stat.1.value")
                assertEquals("4km", short.text.toString())
                assertEquals("12345km", distance.text.toString())
                val first = long.findViewWithTag<View>("f4.stats.0")
                val middle = long.findViewWithTag<View>("f4.stats.1")
                val last = long.findViewWithTag<View>("f4.stats.2")
                assertTrue(first.right < middle.left && middle.right < last.left)
                assertTrue(middle.left < distance.left && distance.right < middle.right)
                val timeline = long.findViewWithTag<View>("f4.places")
                val replay = long.findViewWithTag<View>("f4.replay")
                val panel = long.findViewWithTag<View>("f4.panel")
                assertEquals(20, replay.top - timeline.bottom)
                assertEquals(1011, replay.top)
                assertEquals(1104, panel.height)
                assertEquals(41, panel.bottom - replay.bottom)
                assertTrue(!timeline.canScrollVertically(1))
                assertTrue(long.findViewWithTag<View>("f4.day.0").isSelected)
                assertTrue(long.findViewWithTag<View>("f4.day.1").performClick())
                assertTrue(long.findViewWithTag<View>("f4.day.1").isSelected)
            }
        }
    }
}
