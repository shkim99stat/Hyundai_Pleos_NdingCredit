package com.example.pleos_endingcredit

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.GridView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveScrollRenderingTest {
    @Test fun catalogScrollsVerticallyWhileItsSearchAndActionsStayPut() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val prefs = context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        try {
            prefs.edit().clear().putInt(DemoContent.VERSION_KEY, DemoContent.VERSION).commit()
            val trips = (1..16).map { i -> Trip(id = "catalog-scroll-$i", name = "여행 $i", people = 4) }
            val store = TripStore(context)
            trips.reversed().forEach(store::complete)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrument.waitForIdleSync()
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    root.checkRequiredConsents()
                    assertTrue(root.findViewWithTag<View>("a0.accept").performClick())
                }
                instrument.waitForIdleSync()
                scenario.onActivity { assertTrue(it.window.decorView.findViewWithTag<View>("a03.later").performClick()) }
                instrument.waitForIdleSync()
                scenario.onActivity { assertTrue(it.window.decorView.findViewWithTag<View>("home.archive").performClick()) }
                instrument.waitForIdleSync()
                var searchY = 0
                scenario.onActivity {
                    val root = it.window.decorView.findViewWithTag<View>("f1.catalog")
                    val grid = root.findViewWithTag<GridView>("f1.grid")
                    assertTrue(grid.canScrollVertically(1))
                    searchY = root.findViewWithTag<View>("f1.search").top
                    grid.smoothScrollToPosition(trips.lastIndex)
                }
                for (attempt in 0 until 25) {
                    var arrived = false
                    scenario.onActivity {
                        arrived = it.window.decorView.findViewWithTag<GridView>("f1.grid").lastVisiblePosition == trips.lastIndex
                    }
                    if (arrived) break
                    SystemClock.sleep(100)
                }
                scenario.onActivity {
                    val root = it.window.decorView.findViewWithTag<View>("f1.catalog")
                    val grid = root.findViewWithTag<GridView>("f1.grid")
                    assertTrue(grid.firstVisiblePosition >= 4)
                    assertNotNull(grid.findViewWithTag<View>("f1.poster.${trips.last().id}"))
                    assertEquals(searchY, root.findViewWithTag<View>("f1.search").top)
                    assertTrue(root.findViewWithTag<View>("f1.add").isShown)
                }
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
}
