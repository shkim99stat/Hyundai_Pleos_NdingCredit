package com.example.pleos_endingcredit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TripStartPopupTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun figmaShellFitsFullHalfAndKeyboardHeightWithTheExistingForm() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for ((width, height) in listOf(1700 to 1184, 850 to 592, 1700 to 500)) {
                    var name = ""; var people = ""; var began = false; var dismissed = false
                    val form = A3TripStartView(activity, "", "", "",
                        { name = it }, { people = it }, {}, { began = true },
                        A3TripStartView.Presentation.POPUP)
                    val overlay = A3TripStartPopupOverlay(activity, form) { dismissed = true }
                    AppTypography.applyTo(overlay)
                    overlay.measure(exact(width), exact(height))
                    overlay.layout(0, 0, width, height)
                    val card = bounds(overlay, "trip.start.card")
                    val scale = width / 1700f
                    assertEquals(1100f * scale, card.width().toFloat(), 1f)
                    if (height >= 592) {
                        assertEquals(720f * scale, card.height().toFloat(), 1f)
                        assertEquals(300f * scale, card.left.toFloat(), 1f)
                        assertEquals(233f * scale, card.top.toFloat(), 1f)
                    } else {
                        assertTrue(card.height() <= height - 48)
                        form.scrollTo(0, form.getChildAt(0).height - form.height)
                    }
                    val action = bounds(overlay, "a3.begin")
                    assertTrue("Start action is clipped", card.contains(action))
                    val names = form.findViewWithTag<EditText>("a3.name")
                    val count = form.findViewWithTag<EditText>("a3.people")
                    names.setText("팝업 여행"); count.setText("3")
                    form.findViewWithTag<View>("a3.plus").performClick()
                    assertEquals("4", people)
                    form.findViewWithTag<View>("a3.minus").performClick()
                    assertEquals("3", people)
                    assertEquals("팝업 여행", name)
                    // Flush EditText's layout after changing text in this offscreen fixture.
                    overlay.measure(exact(width), exact(height))
                    overlay.layout(0, 0, width, height)
                    if (height < 592) form.scrollTo(0, form.getChildAt(0).height - form.height)
                    assertNull(form.findViewWithTag<View>("a3.date"))
                    assertNull(form.findViewWithTag<View>("a3.dateLabel"))
                    assertEquals("이번 여행, 어떻게 부를까요?", form.findViewWithTag<TextView>("a3.title").text)
                    assertEquals("기록 시작하기", form.findViewWithTag<TextView>("a3.beginLabel").text)
                    for (asset in listOf("crew-935-mic.svg", "crew-935-divider.svg", "crew-935-a3-close.svg")) {
                        assertTrue(activity.assets.open("travel_refresh/$asset").use { it.read() >= 0 })
                    }
                    assertTrue(form.findViewWithTag<View>("a3.voice").isClickable)
                    if (height >= 592) {
                        for ((tag, slot) in mapOf(
                            "a3.name" to floatArrayOf(40f, 218f, 948f, 48f),
                            "a3.people" to floatArrayOf(916f, 340f, 72f, 48f),
                            "a3.nameDivider" to floatArrayOf(40f, 279f, 1020f, 1f),
                            "a3.peopleDivider" to floatArrayOf(40f, 439f, 1020f, 1f),
                            "a3.microphoneIcon" to floatArrayOf(1012f, 220f, 48f, 48f),
                            "a3.close" to floatArrayOf(1012f, 36f, 48f, 48f),
                            "a3.begin" to floatArrayOf(40f, 588f, 1020f, 92f),
                        )) {
                            val actual = bounds(overlay, tag)
                            assertEquals(card.left + slot[0] * scale, actual.left.toFloat(), 1.1f)
                            assertEquals(card.top + slot[1] * scale, actual.top.toFloat(), 1.1f)
                            assertEquals(slot[2] * scale, actual.width().toFloat(), 1.1f)
                            assertEquals(slot[3] * scale, actual.height().toFloat(), 1.1f)
                        }
                    }
                    assertTrue(form.findViewWithTag<View>("a3.begin").performClick())
                    assertTrue(began)
                    assertFalse(dismissed)
                    overlay.findViewWithTag<View>("trip.start.card").performClick()
                    assertFalse("Tapping inside dismissed the form", dismissed)
                    capture(overlay, activity.filesDir, "popup-${width}x$height.png")
                    val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(image).apply { drawColor(Color.WHITE); overlay.draw(this) }
                    assertEquals("Dim differs from Figma", 0xFF8E8E90.toInt(), image.getPixel(10, 10))
                    val microphone = bounds(overlay, "a3.microphoneIcon")
                    if (card.contains(microphone)) {
                        var painted = false
                        for (y in microphone.top until microphone.bottom) for (x in microphone.left until microphone.right) {
                            if (Color.red(image.getPixel(x, y)) < 100) painted = true
                        }
                        assertTrue("Original microphone SVG is empty or covered", painted)
                    }
                    image.recycle()
                    assertTrue(form.findViewWithTag<View>("a3.close").performClick())
                    assertTrue("Close icon did not dismiss", dismissed)
                    dismissed = false
                    overlay.performClick()
                    assertTrue("Backdrop tap did not dismiss", dismissed)
                }
            }
        }
    }

    @Test fun a2OpensTheModalAndRestoresItsDraftThenDismissesBackToTheSameHome() {
        val prefs = context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        val crews = context.getSharedPreferences("travel_crews", Context.MODE_PRIVATE)
        val originalCrews = crews.getString("crews", null)
        try {
            crews.edit().remove("crews").commit()
            prefs.edit().clear().putInt(DemoContent.VERSION_KEY, DemoContent.VERSION)
                .putBoolean("consent", true).commit()
            val store = TripStore(context)
            store.complete(Trip(id = "popup-history", name = "이전 여행", people = 2))
            store.resume = null
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            await { it.findViewWithTag<View>("home.a2")?.isShown == true }
            lateinit var home: View
            live { activity ->
                val root = activity.window.decorView
                assertNull(root.findViewWithTag<View>("trip.start.overlay"))
                home = root.findViewWithTag("home.a2")
                capture(root, activity.filesDir, "a2-before-popup.png")
            }
            tap("home.start")
            await { it.findViewWithTag<View>("a2.crew")?.isShown == true }
            tap("crew.next")
            await { it.findViewWithTag<View>("trip.start.overlay")?.isShown == true }
            live { activity ->
                val root = activity.window.decorView
                assertSame(home, root.findViewWithTag<View>("home.a2"))
                assertTrue(home.isShown)
                root.findViewWithTag<EditText>("a3.name").setText("복원할 여행")
                root.findViewWithTag<EditText>("a3.people").setText("0")
            }
            live { assertNull(it.window.decorView.findViewWithTag<View>("a3.date")) }
            tap("a3.begin") // Invalid count must keep the modal open and never start a recording.
            live { activity ->
                assertNotNull(activity.window.decorView.findViewWithTag<View>("trip.start.overlay"))
                assertNull(TripStore(context).activeTrip)
                activity.window.decorView.findViewWithTag<EditText>("a3.people").setText("3")
            }
            var previous: MainActivity? = null
            live { previous = it; it.recreate() }
            await { it.findViewWithTag<View>("trip.start.overlay")?.isShown == true && liveInstance !== previous }
            live { activity ->
                val root = activity.window.decorView
                assertEquals("복원할 여행", root.findViewWithTag<EditText>("a3.name").text.toString())
                assertEquals("3", root.findViewWithTag<EditText>("a3.people").text.toString())
                assertNull(root.findViewWithTag<View>("a3.date"))
                assertTrue(root.findViewWithTag<View>("home.a2").isShown)
                home = root.findViewWithTag("home.a2")
                capture(root, activity.filesDir, "popup-live.png")
            }
            requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { image ->
                val target = File(context.filesDir, "design-verification/popup-live-screen.png")
                target.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
            live { it.onBackPressedDispatcher.onBackPressed() }
            await { it.findViewWithTag<View>("trip.start.overlay") == null && it.findViewWithTag<View>("home.a2")?.isShown == true }
            live { assertSame(home, it.window.decorView.findViewWithTag<View>("home.a2")) }
            tap("home.start")
            await { it.findViewWithTag<View>("a2.crew")?.isShown == true }
            tap("crew.next")
            await { it.findViewWithTag<View>("trip.start.overlay")?.isShown == true }
            live { activity ->
                val root = activity.window.decorView
                assertTrue(root.findViewWithTag<EditText>("a3.name").text.isNullOrEmpty())
                outsideTap(root.findViewWithTag("trip.start.overlay"))
            }
            await { it.findViewWithTag<View>("trip.start.overlay") == null }
            assertNull(store.activeTrip)
            assertEquals(1, store.trips.size)
            context.startActivity(Intent(Intent.ACTION_MAIN, null, context, MainActivity::class.java)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await { it.findViewWithTag<View>("home.a2")?.isShown == true }
            live { assertNull(it.window.decorView.findViewWithTag<View>("trip.start.overlay")) }
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().forEach { it.finishAndRemoveTask() }
            }
            crews.edit().putString("crews", originalCrews).commit()
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

    private var liveInstance: MainActivity? = null
    private fun live(action: (MainActivity) -> Unit) = instrumentation.runOnMainSync {
        val current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().last()
        liveInstance = current
        action(current)
    }
    private fun await(condition: (View) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync {
                val current = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().lastOrNull()
                liveInstance = current
                if (current != null) ready = condition(current.window.decorView)
            }
            if (ready) { SystemClock.sleep(300); return }
            SystemClock.sleep(100)
        }
        fail("Requested popup/home state did not appear")
    }
    private fun tap(tag: String) {
        await { it.findViewWithTag<View>(tag)?.isShown == true }
        live { assertTrue(it.window.decorView.findViewWithTag<View>(tag).performClick()) }
    }
    private fun bounds(root: ViewGroup, tag: String) = Rect().also {
        val node = requireNotNull(root.findViewWithTag<View>(tag))
        node.getDrawingRect(it); root.offsetDescendantRectToMyCoords(node, it)
    }
    private fun outsideTap(view: View) {
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now + action, action, 10f, 10f, 0)
            view.dispatchTouchEvent(event); event.recycle()
        }
    }
    private fun capture(view: View, filesDir: File, name: String) {
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val folder = File(filesDir, "design-verification").apply { mkdirs() }
        File(folder, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun exact(value: Int) = View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY)
}
