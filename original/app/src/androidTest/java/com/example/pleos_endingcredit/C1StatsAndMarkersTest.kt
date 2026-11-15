package com.example.pleos_endingcredit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class C1StatsAndMarkersTest {
    @Test fun c1UsesWholeKilometersAndSizesColumnsFromTheirText() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val trip = Trip(name = "C1 거리 확인", people = 4)
                fun result(meters: Double): C1TripResultView {
                    val record = TripRecording(trip.id, 1_800_000_000_000L, distanceMeters = meters,
                        movingMillis = 36_000_000L)
                    return C1TripResultView(activity, TripResultContent(trip, record), {}, {}, {}, {})
                }
                for ((width, height) in listOf(1696 to 1180, 848 to 590)) {
                    val short = result(4_800.0)
                    val long = result(12_345_678.0)
                    measure(short, width, height)
                    measure(long, width, height)
                    val shortDistance = short.findViewWithTag<TextView>("c1.stat.1")
                    val distance = long.findViewWithTag<TextView>("c1.stat.1")
                    assertEquals("4km", shortDistance.text.toString())
                    assertEquals("12345km", distance.text.toString())

                    assertEquals("4명", long.findViewWithTag<TextView>("c1.stat.2").text.toString())
                    assertEquals(shortDistance.width, distance.width)
                    val stats = (0..2).map { long.findViewWithTag<View>("c1.stat.$it") }
                    assertTrue(stats.zipWithNext().all { (left, right) -> left.right < right.left })
                }
            }
        }
    }

    @Test fun c1NumberedStopsStayAboveTheReviewButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = TripResultContent(DemoContent.trips.first(), null)
                val root = C1TripResultView(activity, content, {}, {}, {}, {})
                measure(root, 1696, 1180)
                val timeline = root.findViewWithTag<View>("c1.places.timeline")
                val review = root.findViewWithTag<View>("c1.places.review")
                assertEquals(timeline.bottom, (review.parent as View).top)
                val replay = root.findViewWithTag<View>("c1.replay")
                val costs = root.findViewWithTag<View>("c1.costs")
                assertEquals(315, costs.width)
                assertTrue(replay.right < costs.left)
                assertEquals(20, replay.top - (review.parent as View).bottom)
                assertEquals(1009, replay.top)
                val panel = root.findViewWithTag<View>("c1.places.panel")
                assertEquals(1102, panel.height)
                for (index in 0..2) {
                    val marker = root.findViewWithTag<TextView>("c1.place.marker.$index")
                    assertEquals((index + 1).toString(), marker.text.toString())
                    assertEquals(android.graphics.Color.WHITE, marker.currentTextColor)
                    assertEquals(marker.width, marker.height)
                    assertTrue((marker.parent.parent as View).top + marker.bottom < timeline.height)
                }
                val visitName = root.findViewWithTag<View>("c1.place.name.0")
                val visitAddress = root.findViewWithTag<View>("c1.place.address.0")
                val visitTime = root.findViewWithTag<TextView>("c1.place.time.0")
                assertTrue(visitName.bottom < visitAddress.top && visitAddress.bottom < visitTime.top)
                assertEquals("오전 10:20", visitTime.text.toString())
                val mapMarker = root.findViewWithTag<TextView>("f4.sample.pin.0")
                assertEquals("1", mapMarker.text.toString())
                assertEquals(android.graphics.Color.WHITE, mapMarker.currentTextColor)
                val list = timeline as C1Timeline
                val dayTwo = root.findViewWithTag<View>("c1.day.2")
                assertTrue(dayTwo.performClick())
                assertTrue(dayTwo.isSelected)
                assertTrue(list.findViewWithTag<View>("c1.place.marker.0") == null)
                measure(root, 1696, 1180)
                assertEquals(1009, replay.top)
                assertEquals(1102, panel.height)
                assertEquals(20, replay.top - (review.parent as View).bottom)
                assertTrue(!timeline.canScrollVertically(1))
                assertEquals(View.INVISIBLE, mapMarker.visibility)
                assertEquals("1", root.findViewWithTag<TextView>("f4.sample.pin.4").text.toString())
                assertTrue(root.findViewWithTag<View>("c1.day.0").performClick())
                measure(root, 1696, 1180)
                assertTrue(list.findViewWithTag<View>("c1.place.marker.0") != null)
                assertEquals(View.VISIBLE, mapMarker.visibility)
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                try {
                    root.draw(Canvas(bitmap))
                    File(activity.filesDir, "c1-numbered.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun c1ShowsAddressesBelowTheStopName() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val trip = Trip(name = "주소 확인", people = 2)
                val stop = RecordedStop("c1-address-test", 1_800_000_000_000L, null, selectedPlaceId = "987654321")
                val record = TripRecording(trip.id, stop.at, endedAt = stop.at + 1, stops = listOf(stop))
                val result = C1TripResultView(activity, TripResultContent(trip, record), {}, {}, {}, {})
                val timeline = result.findViewWithTag<C1Timeline>("c1.places.timeline")
                val address = result.findViewWithTag<TextView>("c1.place.address.0")
                timeline.showAddress(stop, "서울특별시 중구 세종대로 110")
                assertEquals("서울특별시 중구 세종대로 110", address.text.toString())
                PlaceDisplayNames.remember(listOf(PlaceCandidate("987654321", "서울시청", "공공기관", 0.0,
                    roadAddress = "서울특별시 중구 세종대로 110 본관")))
                timeline.refreshPlaceDetails(record, updateNames = false)
                assertEquals("서울특별시 중구 세종대로 110 본관", address.text.toString())
            }
        }
    }

    private fun measure(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
}
