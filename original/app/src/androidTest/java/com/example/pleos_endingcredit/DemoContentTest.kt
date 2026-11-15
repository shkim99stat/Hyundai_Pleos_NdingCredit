package com.example.pleos_endingcredit

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoContentTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context = instrument.targetContext
    private val prefs = context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE)
    private lateinit var original: Map<String, *>

    @Before fun clear() {
        original = prefs.all.toMap()
        prefs.edit().clear().commit()
    }

    @After fun restore() {
        prefs.edit().clear().apply {
            original.forEach { (key, value) -> when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
            } }
        }.commit()
    }

    @Test fun freshStoreSeedsNineTripsAndTheBusanCheckpointOnlyOnce() {
        val store = TripStore(context)
        assertEquals(DemoContent.trips, store.trips)
        assertEquals(CreditResume("demo-home-busan", 3), store.resume)
        assertEquals(6, CREDIT_FRAME_COUNT)
        val edited = store.trips.first().copy(name = "이름을 바꾼 여행", people = 5)
        store.update(edited)
        val recorded = Trip(id = "real-recording", name = "새로 기록한 여행", people = 3)
        store.complete(recorded)
        store.resume = CreditResume(recorded.id, 5)
        val reopened = TripStore(context)
        assertEquals(10, reopened.trips.size)
        assertEquals(recorded, reopened.trips.first())
        assertEquals(edited, reopened.trips.single { it.id == edited.id })
        assertEquals(CreditResume(recorded.id, 5), reopened.resume)
        assertEquals(R.drawable.archive_gyeongju, DemoContent.coverResource(reopened.trips.single { it.id == "demo-gyeongju" }))
    }

    @Test fun migrationPreservesExistingHistoryConsentActiveTripAndCheckpoint() {
        val existing = Trip(id = "saved", name = "기존 여행", people = 2)
        val active = Trip(id = "active", name = "기록 중 여행", people = 6)
        prefs.edit().putBoolean("consent", true)
            .putString("trips", "[${existing.toJson()}]")
            .putString("active_trip", active.toJson().toString())
            .putString("resume_trip", existing.id).putInt("resume_frame", 4).commit()
        repeat(2) {
            val store = TripStore(context)
            assertEquals(10, store.trips.size)
            assertEquals(existing, store.trips.last())
            assertEquals(active, store.activeTrip)
            assertTrue(store.hasConsent)
            assertEquals(CreditResume(existing.id, 4), store.resume)
        }
    }

    @Test fun versionOneStoreAddsTheFiveHomeSamplesWithoutReplacingEditedHistory() {
        val edited = DemoContent.trips.first().copy(name = "이름을 바꾼 여행")
        val legacy = listOf(edited) + DemoContent.trips.drop(1).take(3)
        val checkpoint = CreditResume(edited.id, 4, 500)
        prefs.edit().putInt(DemoContent.VERSION_KEY, 1)
            .putInt("credit_sequence_version", 1)
            .putString("trips", org.json.JSONArray(legacy.map(Trip::toJson)).toString())
            .putString("resume_trip", checkpoint.tripId)
            .putInt("resume_frame", checkpoint.frame)
            .putLong("resume_elapsed", checkpoint.elapsedMillis)
            .commit()
        repeat(2) {
            val store = TripStore(context)
            assertEquals(legacy + DemoContent.homeTrips, store.trips)
            assertEquals(checkpoint, store.resume)
        }
    }

    @Test fun qrDecodesToTheActualTripSummaryWithKoreanAndEditedPeople() {
        val trip = DemoContent.trips.first().copy(name = "함께한 강릉 여행", people = 5)
        val qr = TripShare.qr(trip)
        val pixels = IntArray(qr.width * qr.height)
        qr.getPixels(pixels, 0, qr.width, 0, 0, qr.width, qr.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(qr.width, qr.height, pixels))))
        assertEquals(TripShare.summary(trip), decoded.text)
        assertTrue(decoded.text.contains("1인 8,312원"))
        qr.recycle()
    }

    @Test fun freshLaunchShowsThePopulatedCatalogAndResumesCredit() {
        // No fixture insertion: Activity must install the real bundled demo itself.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                instrument.uiAutomation.executeShellCommand("am task focus ${a.taskId}").close()
            }
            fun settle() { instrument.waitForIdleSync(); Thread.sleep(400) }
            fun clickTag(tag: String) {
                scenario.onActivity { a -> assertTrue(nodes(a.window.decorView).last { it.tag == tag && it.isShown }.performClick()) }
                settle()
            }
            fun clickText(text: String) {
                scenario.onActivity { a -> if (creditTestAction(a, text)) return@onActivity
                    assertTrue(nodes(a.window.decorView).filterIsInstance<TextView>()
                    .last { it.text.toString() == text && it.isShown }.performClick()) }
                settle()
            }
            fun shown(text: String) = scenario.onActivity { a ->
                assertTrue("Missing $text", creditChapterShown(a.window.decorView, text) || nodes(a.window.decorView).filterIsInstance<TextView>()
                    .any { it.text.toString() == text && it.isShown })
            }
            settle()
            scenario.onActivity { it.window.decorView.checkRequiredConsents() }
            clickTag("a0.accept")
            shown("이동비를 받을 곳을")
            clickTag("a03.later")
            scenario.onActivity { a ->
                assertTrue(a.window.decorView.findViewWithTag<View>("home.a2").isShown)
                assertNull(a.window.decorView.findViewWithTag<View>("trip.start.overlay"))
            }
            shown("속초 1박 2일")
            scenario.onActivity { a ->
                assertEquals("전체 상영관·9편",
                    a.window.decorView.findViewWithTag<View>("home.archive").contentDescription.toString())
            }
            clickTag("home.archive")
            shown("전체 상영관")
            scenario.onActivity { a ->
                assertEquals(9, a.window.decorView.findViewWithTag<android.widget.GridView>("f1.grid").count)
            }
            clickTag("f1.year")
            selectCatalogResume()
            settle()
            scenario.onActivity { a ->
                val credit = nodes(a.window.decorView).filterIsInstance<B3CreditView>().last { it.isShown }
                assertEquals(CreditPlayback.FILM, CreditPlayback.index(credit.frame))
                assertTrue(credit.contentDescription.toString().endsWith(CreditPlayback.titles.last()))
            }
            clickText("결과 보기")
            scenario.onActivity { a ->
                assertTrue(a.window.decorView.findViewWithTag<View>("c1.result").isShown)
            }
        }
    }

    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
}
