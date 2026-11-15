package com.example.pleos_endingcredit

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.view.View
import android.widget.CompoundButton
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DemoCatalogNavigationTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()

    @Test fun a2ShowsTheFiveFigmaPhotosAndOpensTheSelectedTripDetail() {
        val trip = DemoContent.homeTrips.first()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            if (!instrument.targetContext.getSharedPreferences("ending_credit_mock", 0)
                    .getBoolean("consent", false)) {
                click(activity, "a0.locationConsent")
                click(activity, "a0.drivingConsent")
                click(activity, "a0.accept")
            }
            capture("a2")
            click(activity, "home.archive")
            await(activity, "f1.catalog")
            for (attempt in 0 until 60) {
                var loaded = false
                activity.onActivity {
                    val grid = it.window.decorView.findViewWithTag<GridView>("f1.grid")
                    loaded = (0 until 8).all { index ->
                        grid.getChildAt(index)?.findViewWithTag<ImageView>("f1.poster.image")?.drawable != null
                    }
                }
                if (loaded) break
                SystemClock.sleep(100)
            }
            activity.onActivity {
                val grid = it.window.decorView.findViewWithTag<GridView>("f1.grid")
                assertEquals(4, grid.numColumns)
                val sampleIds = listOf(
                    "demo-home-sokcho", "demo-gyeongju", "demo-home-jeju", "demo-home-solo",
                    "demo-gangneung", "demo-home-busan", "demo-home-gapyeong", "demo-sokcho",
                )
                sampleIds.forEachIndexed { index, id ->
                    val poster = grid.getChildAt(index)
                    assertEquals("f1.poster.$id", poster.tag)
                    assertNotNull(poster.findViewWithTag<ImageView>("f1.poster.image").drawable)
                }
            }
            capture("f1")
            activity.onActivity {
                val poster = it.window.decorView.findViewWithTag<View>("f1.poster.${trip.id}")
                assertTrue(poster.findViewWithTag<View>("f1.poster.image").performClick())
            }
            await(activity, "f4.detail")
            activity.onActivity {
                val root = it.window.decorView
                assertTrue(root.findViewWithTag<TextView>("f4.title").text.startsWith(trip.name))
                assertTrue(root.findViewWithTag<View>("f4.day.1").performClick())
                assertTrue(root.findViewWithTag<View>("f4.day.1").isSelected)
            }
            capture("f4")
            click(activity, "f4.costs")
            await(activity, "d1.costs.dialog")
            activity.onActivity {
                val costs = it.window.decorView.findViewWithTag<View>("d1.costs.dialog")
                assertEquals("27,320원", costs.findViewWithTag<TextView>("d1.costs.total.value").text.toString())
                val qr = costs.findViewWithTag<ImageView>("d1.costs.qr")
                val home = costs.findViewWithTag<View>("d1.costs.home")
                assertNotNull(qr.drawable)
                assertTrue(qr.bottom < home.top)
                assertEquals(qr.left, home.left)
            }
            capture("d1")
            click(activity, "d1.costs.close")
            click(activity, "f4.replay")
            await(activity, "b3.credit")
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await(activity, "f4.detail")
            click(activity, "f4.back")
            await(activity, "f1.catalog")
            val stored = instrument.targetContext.getSharedPreferences("travel_archive_favorites", 0)
            val favoriteKey = "figma-f1:${trip.id}"
            val initial = stored.all[favoriteKey]
            try {
                var selected = false
                activity.onActivity {
                    val heart = it.window.decorView.findViewWithTag<View>("f1.poster.${trip.id}")
                        .findViewWithTag<View>("f1.poster.favorite")
                    selected = heart.isSelected
                    assertTrue(heart.performClick())
                    assertEquals(!selected, heart.isSelected)
                }
                click(activity, "f1.back")
                click(activity, "home.archive")
                await(activity, "f1.catalog")
                activity.onActivity {
                    val poster = it.window.decorView.findViewWithTag<View>("f1.poster.${trip.id}")
                    val heart = poster.findViewWithTag<View>("f1.poster.favorite")
                    assertEquals(!selected, heart.isSelected)
                    assertEquals(if (selected) View.GONE else View.VISIBLE,
                        poster.findViewWithTag<View>("f1.heart-selected.svg").visibility)
                    assertTrue(heart.performClick())
                    assertEquals(selected, heart.isSelected)
                }
            } finally {
                val editor = stored.edit()
                if (initial is Boolean) editor.putBoolean(favoriteKey, initial) else editor.remove(favoriteKey)
                editor.commit()
            }
            click(activity, "f1.back")
            await(activity, "home.a2")

            click(activity, "home.archive")
            await(activity, "f1.catalog")
            activity.onActivity {
                val poster = it.window.decorView.findViewWithTag<View>("f1.poster.${trip.id}")
                assertTrue(poster.findViewWithTag<View>("f1.poster.image").performClick())
            }
            click(activity, "f4.costs")
            click(activity, "d1.costs.home")
            await(activity, "home.a2")
        }
    }

    @Test fun completedShowcaseCalculatesCostsFromItsDistanceAndUpdatesTheQr() {
        val trip = Trip(id = DemoScenario.CURRENT_PREFIX + "costs-test", name = DemoScenario.TITLE,
            people = 4, createdOn = DemoScenario.DATE)
        val snapshot = DemoScenario.snapshot(trip.id, DemoScenario.legs.lastIndex, 1.0)
        val record = snapshot.copy(endedAt = snapshot.latest?.at)

        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { context ->
                var split = trip.people
                val dialog = D1_1CostsView(context, TripResultContent(trip, record), dismiss = {}, home = {}) {
                    split = it
                }
                fun text(tag: String) = dialog.findViewWithTag<TextView>("d1.costs.$tag").text.toString()
                val fields = mapOf(
                    "title" to "이동비 계산 · 시연용 추정",
                    "distance.value" to "504km",
                    "efficiency.value" to "5.0km/kWh",
                    "usage.value" to "100.88kWh",
                    "energy.value" to "30,264원",
                    "toll.value" to "8,600원",
                    "parking.value" to "0원",
                    "total.value" to "38,864원",
                )
                fields.forEach { (tag, value) -> assertEquals(tag, value, text(tag)) }
                val qr = dialog.findViewWithTag<ImageView>("d1.costs.qr")
                assertTrue(readQr(qr).contains("1인 이동비: 9,716원"))
                assertTrue(dialog.findViewWithTag<View>("d1.costs.minus").performClick())
                assertEquals(3, split)
                val updatedQr = readQr(qr)
                assertTrue(updatedQr.contains("시연용 추정"))
                assertTrue(updatedQr.contains("합계: 38,864원"))
                assertTrue(updatedQr.contains("1인 이동비: 12,955원"))

                val regularTrip = trip.copy(id = "non-showcase-costs-test")
                val regularDialog = D1_1CostsView(context,
                    TripResultContent(regularTrip, record.copy(tripId = regularTrip.id)), dismiss = {}, home = {}) {}
                assertEquals("기록 없음", regularDialog.findViewWithTag<TextView>("d1.costs.total.value").text.toString())
            }
        }
    }

    private fun readQr(image: ImageView): String {
        val bitmap = (image.drawable as BitmapDrawable).bitmap
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    private fun click(activity: ActivityScenario<MainActivity>, tag: String) {
        await(activity, tag)
        activity.onActivity {
            val control = it.window.decorView.findViewWithTag<View>(tag)
            val wasChecked = (control as? CompoundButton)?.isChecked
            val handled = control.performClick()
            if (wasChecked == null) assertTrue("$tag handled click", handled)
            else assertEquals("$tag toggled", !wasChecked, (control as CompoundButton).isChecked)
        }
    }

    private fun await(activity: ActivityScenario<MainActivity>, tag: String) {
        repeat(60) {
            var ready = false
            activity.onActivity {
                ready = it.window.decorView.findViewWithTag<View>(tag)?.let { view ->
                    view.isShown && view.isLaidOut && view.isEnabled
                } == true
            }
            if (ready) return
            SystemClock.sleep(100)
        }
        fail("Missing $tag")
    }

    private fun capture(name: String) {
        SystemClock.sleep(450) // Wait for the page crossfade.
        val image = requireNotNull(instrument.uiAutomation.takeScreenshot())
        val folder = File(instrument.targetContext.filesDir, "demo-qa").apply { mkdirs() }
        try {
            File(folder, "catalog-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            image.recycle()
        }
    }
}
