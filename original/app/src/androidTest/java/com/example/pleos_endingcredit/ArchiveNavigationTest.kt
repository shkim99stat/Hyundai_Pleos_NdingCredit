package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.GridView
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
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ArchiveNavigationTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context = instrument.targetContext

    @Test fun f1OriginalPhotosAndF4AreConnectedToTheSelectedTrip() {
        val prefs = context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        try {
            prefs.edit().clear().putBoolean("consent", true).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val first = DemoContent.homeTrips.first()
                val second = DemoContent.homeTrips[1]
                click(scenario, "home.archive")
                await(scenario, "f1.catalog")
                click(scenario, "f1.back")
                await(scenario, "home.a2")
                click(scenario, "home.archive")
                await(scenario, "f1.catalog")
                scenario.onActivity { activity ->
                    val root = activity.window.decorView.findViewWithTag<View>("f1.catalog")
                    val grid = root.findViewWithTag<GridView>("f1.grid")
                    assertEquals(4, grid.numColumns)
                    assertEquals(DemoContent.trips.size, grid.count)
                    val scale = min(root.width / 1700f, root.height / 1184f)
                    for (index in 0 until 8) {
                        val tile = grid.getChildAt(index)
                        val image = requireNotNull(tile.findViewWithTag<ImageView>("f1.poster.image"))
                        val favorite = tile.findViewWithTag<View>("f1.poster.favorite")
                        assertNull(image.drawable)
                        assertEquals(ImageView.ScaleType.CENTER_CROP, image.scaleType)
                        assertEquals(392f * scale, image.width.toFloat(), 2f)
                        assertEquals(420f * scale, image.height.toFloat(), 2f)
                        assertEquals((22f + index % 4 * 416f) * scale, tile.left.toFloat(), 3f)
                        val selected = favorite.isSelected
                        assertTrue(favorite.performClick())
                        assertEquals(!selected, favorite.isSelected)
                        assertEquals(if (selected) View.VISIBLE else View.GONE,
                            tile.findViewWithTag<View>("f1.heart.svg").visibility)
                        assertTrue(favorite.performClick())
                        assertEquals(selected, favorite.isSelected)
                    }
                }
                capture(scenario, "f1.catalog", "f1-catalog.png")

                scenario.onActivity {
                    it.window.decorView.findViewWithTag<EditText>("f1.search").setText(second.name)
                }
                instrument.waitForIdleSync()
                scenario.onActivity {
                    val grid = it.window.decorView.findViewWithTag<GridView>("f1.grid")
                    assertNull(grid.findViewWithTag<View>("f1.poster.${first.id}"))
                    val tile = requireNotNull(grid.findViewWithTag<View>("f1.poster.${second.id}"))
                    assertTrue(tile.findViewWithTag<View>("f1.poster.image").performClick())
                }
                await(scenario, "f4.detail")
                scenario.onActivity {
                    val root = it.window.decorView.findViewWithTag<View>("f4.detail")
                    assertTrue(root.findViewWithTag<TextView>("f4.title").text.startsWith(second.name))
                    assertTrue(root.findViewWithTag<View>("f4.day.1").performClick())
                    assertTrue(root.findViewWithTag<View>("f4.day.1").isSelected)
                }
                scenario.recreate()
                await(scenario, "f4.detail")
                scenario.onActivity {
                    assertTrue(it.window.decorView.findViewWithTag<View>("f4.day.1").isSelected)
                    it.window.decorView.findViewWithTag<View>("f4.day.0").performClick()
                }
                capture(scenario, "f4.detail", "f4-detail.png")
                click(scenario, "f4.costs")
                await(scenario, "d1.costs.dialog")
                scenario.onActivity {
                    val dialog = it.window.decorView.findViewWithTag<View>("d1.costs.dialog")
                    assertEquals("27,320원", dialog.findViewWithTag<TextView>("d1.costs.total.value").text.toString())
                    assertNotNull(dialog.findViewWithTag<ImageView>("d1.costs.qr").drawable)
                }
                capture(scenario, "d1.costs.dialog", "d1-costs.png")
                click(scenario, "d1.costs.close")

                click(scenario, "f4.replay")
                await(scenario, "b3.credit")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                await(scenario, "f4.detail")
                click(scenario, "f4.back")
                await(scenario, "f1.catalog")
                scenario.onActivity {
                    assertEquals(second.name, it.window.decorView.findViewWithTag<EditText>("f1.search").text.toString())
                    it.onBackPressedDispatcher.onBackPressed()
                }
                await(scenario, "home.a2")
                click(scenario, "home.archive")
                await(scenario, "f1.catalog")
                scenario.onActivity { activity ->
                    val poster = activity.window.decorView.findViewWithTag<View>("f1.poster.${first.id}")
                    assertTrue(poster.findViewWithTag<View>("f1.poster.image").performClick())
                }
                click(scenario, "f4.costs")
                click(scenario, "d1.costs.home")
                await(scenario, "home.a2")
            }
        } finally {
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
    }

    private fun click(scenario: ActivityScenario<MainActivity>, tag: String) {
        await(scenario, tag)
        scenario.onActivity { assertTrue(it.window.decorView.findViewWithTag<View>(tag).performClick()) }
    }

    private fun await(scenario: ActivityScenario<MainActivity>, tag: String) {
        repeat(55) {
            var shown = false
            scenario.onActivity {
                shown = it.window.decorView.findViewWithTag<View>(tag)?.let { v ->
                    v.isShown && v.isLaidOut
                } == true
            }
            if (shown) return
            SystemClock.sleep(100)
        }
        fail("Missing $tag")
    }

    private fun capture(scenario: ActivityScenario<MainActivity>, tag: String, filename: String) {
        SystemClock.sleep(450) // Wait for the page crossfade.
        val bounds = Rect()
        scenario.onActivity {
            val root = it.window.decorView.findViewWithTag<View>(tag)
            val width = if (tag == "d1.costs.dialog") 1100f else 1696f
            val height = if (tag == "d1.costs.dialog") D1_1CostsView.HEIGHT else 1180f
            val scale = min(root.width / width, root.height / height)
            val pos = IntArray(2)
            root.getLocationOnScreen(pos)
            val left = pos[0] + ((root.width - width * scale) / 2).roundToInt()
            val top = pos[1] + ((root.height - height * scale) / 2).roundToInt()
            bounds.set(left, top, left + (width * scale).roundToInt(), top + (height * scale).roundToInt())
        }
        val screenshot = requireNotNull(instrument.uiAutomation.takeScreenshot())
        assertTrue(bounds.intersect(0, 0, screenshot.width, screenshot.height))
        val app = Bitmap.createBitmap(screenshot, bounds.left, bounds.top, bounds.width(), bounds.height())
        File(context.filesDir, filename).outputStream().use { app.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (app !== screenshot) app.recycle()
        screenshot.recycle()
    }
}
