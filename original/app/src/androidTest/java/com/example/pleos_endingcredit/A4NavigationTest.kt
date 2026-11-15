package com.example.pleos_endingcredit

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class C1NavigationTest {
    @Test fun resultDialogsPersistEditsAndHomeReturnsDirectlyToA2() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val prefs = context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        try {
            val demoTrips = org.json.JSONArray().apply {
                DemoContent.trips.forEach { put(it.toJson()) }
            }
            prefs.edit().putString("trips", demoTrips.toString()).commit()
            val trip = DemoContent.trips.first().copy(splitPeople = null, resultPlaces = null)
            val store = TripStore(context); store.activeTrip = null; store.update(trip)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                settle()
                scenario.onActivity { activity ->
                    MainActivity::class.java.getDeclaredField("tripId").apply { isAccessible=true }.set(activity,trip.id)
                    MainActivity::class.java.getDeclaredField("page").apply { isAccessible=true }.set(activity,Page.C1_RESULT)
                    MainActivity::class.java.getDeclaredMethod("render").apply { isAccessible=true }.invoke(activity)
                }; settle()
                capture(instrument, "c1-live")
                fun click(tag: String) { scenario.onActivity {
                    val node = nodes(it.window.decorView).last { v -> v.tag == tag && v.isShown }
                    assertTrue(node.isEnabled); assertTrue(node.performClick())
                }; settle() }
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withTagValue(
                    org.hamcrest.Matchers.equalTo<Any>("c1.swipe.row.demo-rest")))
                    .perform(androidx.test.espresso.action.ViewActions.swipeLeft())
                settle()
                scenario.onActivity {
                    val row = it.window.decorView.findViewWithTag<View>("c1.swipe.row.demo-rest")
                    val delete = row.findViewWithTag<View>("c1.swipe.delete.demo-rest")
                    assertEquals(View.VISIBLE, delete.visibility)
                    assertEquals(row.width, delete.right)
                }
                assertNull(store.trips.first { it.id == trip.id }.resultPlaces)
                capture(instrument, "c1-swipe-live")
                click("c1.swipe.delete.demo-rest")
                assertEquals(8, store.trips.first { it.id == trip.id }.resultPlaces!!.size)
                assertTrue(store.trips.first { it.id == trip.id }.resultPlaces!!.none { it.id == "demo-rest" })
                click("c1.places.review")
                capture(instrument, "c1-2-live")
                click("c1.places.remove.demo-restaurant"); click("c1.places.cancel")
                assertEquals(8, store.trips.first { it.id == trip.id }.resultPlaces!!.size)
                click("c1.places.review"); click("c1.places.remove.demo-restaurant"); click("c1.places.done")
                assertEquals(7, store.trips.first { it.id == trip.id }.resultPlaces!!.size)
                click("c1.places.review"); click("c1.places.name.demo-unknown")
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withTagValue(
                    org.hamcrest.Matchers.equalTo<Any>("c1.places.name.input")))
                    .perform(androidx.test.espresso.action.ViewActions.replaceText("직접 확인한 장소"))
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("저장"))
                    .perform(androidx.test.espresso.action.ViewActions.click())
                settle(); click("c1.places.done")
                assertEquals("직접 확인한 장소", store.trips.first { it.id == trip.id }.resultPlaces!!.first { it.id == "demo-unknown" }.name)
                scenario.onActivity { activity ->
                    assertEquals("확인이 필요한 곳 1곳", activity.window.decorView
                        .findViewWithTag<View>("c1.places.review").contentDescription.toString())
                }
                scenario.recreate(); settle()
                scenario.onActivity { assertNotNull(it.window.decorView.findViewWithTag<View>("c1.result")) }
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withTagValue(
                    org.hamcrest.Matchers.equalTo<Any>("c1.home")))
                    .perform(androidx.test.espresso.action.ViewActions.click())
                settle()
                scenario.onActivity {
                    assertTrue(it.window.decorView.findViewWithTag<View>("home.a2")?.isShown == true)
                    assertNull(it.window.decorView.findViewWithTag<View>("a03.setup"))
                }
                capture(instrument, "c1-home-a2-live")
            }
        } finally {
            prefs.edit().clear().apply { original.forEach { (key,value) -> when(value) {
                is String -> putString(key,value); is Boolean -> putBoolean(key,value); is Int -> putInt(key,value)
                is Long -> putLong(key,value); is Float -> putFloat(key,value)
            } } }.commit()
        }
    }
    private fun nodes(root: View): List<View> = listOf(root) + if(root is ViewGroup)
        (0 until root.childCount).flatMap { nodes(root.getChildAt(it)) } else emptyList()
    private fun settle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); Thread.sleep(600) }

    private fun capture(instrument: android.app.Instrumentation, name: String) {
        val screenshot = instrument.uiAutomation.takeScreenshot() ?: return
        val directory = java.io.File(instrument.targetContext.filesDir, "c1-verification").apply { mkdirs() }
        try {
            java.io.File(directory, "$name.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            screenshot.recycle()
        }
    }
}
