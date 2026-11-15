package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CreditDesignTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private fun exact(value: Int) = View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY)
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()

    @Test fun allDesignScenesRenderWithLocalAssetsAtThreeWindowSizes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = CreditContent(DemoContent.trips.first(), null)
                val scenes = listOf(Triple("01-opening", 0, 0L), Triple("02-route", 1, 2_500L),
                    Triple("03-summary", 2, 1_000L), Triple("04-film", 5, 1_000L),
                    Triple("05-split", 5, 3_500L), Triple("06-roll", 5, 14_000L))
                val output = File(context.filesDir, "b3-redesign-verification").apply { mkdirs() }
                for ((name, frame, elapsed) in scenes) {
                    for ((width, height) in listOf(680 to 474, 1360 to 948, 420 to 840)) {
                        val view = B3CreditView(activity, content, frame, elapsed, muted = false)
                        AppTypography.applyTo(view)
                        view.measure(exact(width), exact(height))
                        view.layout(0, 0, width, height)
                        assertEquals(4, nodes(view).count { it.tag?.toString()?.startsWith("b3.chapter.") == true })
                        assertNotNull(view.findViewWithTag<View>("b3.mute"))
                        assertFalse(view.isClickable)
                        assertEquals(content.odometer(frame, elapsed), view.findViewWithTag<TextView>("b3.odometer").text.toString())
                        for (label in nodes(view).filterIsInstance<ReferenceDesignLabel>()) {
                            assertTrue("$name ${label.tag} has no layout", label.width > 0 && label.height > 0)
                        }
                        when (frame) {
                            0 -> {
                                assertEquals("차량에 고스란히 담긴", view.findViewWithTag<TextView>("b3.eyebrow").text.toString())
                                val title = view.findViewWithTag<TextView>("b3.title")
                                assertEquals("우리의 강릉 여행", title.text.toString())
                                assertEquals(700, title.typeface.weight)
                            }
                            2 -> assertEquals("312km를 달려왔어요", view.findViewWithTag<TextView>("b3.routeCaption").text.toString())
                            5 -> {
                                val film = view.findViewWithTag<View>("b3.film")
                                val bridge = view.findViewWithTag<TextureView>("b3.video.bridge")
                                val cat = view.findViewWithTag<TextureView>("b3.video.cat")
                                assertEquals(film.width / 2, cat.width)
                                when (elapsed) {
                                    1_000L -> assertEquals(film.width, bridge.clipBounds!!.width())
                                    3_500L -> assertTrue(bridge.clipBounds!!.width() in (film.width / 2 + 1) until film.width)
                                    else -> {
                                        assertEquals(film.width / 2, bridge.clipBounds!!.width())
                                        assertEquals(1f, cat.alpha, .001f)
                                    }
                                }
                                assertTrue(view.findViewWithTag<View>("b3.roll").contentDescription.toString().endsWith("THE END"))
                            }
                        }
                        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        view.draw(Canvas(image))
                        File(output, "$name-${width}x${height}.png").outputStream().use {
                            image.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        image.recycle()
                    }
                }
                assertEquals(setOf("map.png", "bridge.mp4", "cat.mp4"), context.assets.list("b3/v2")!!.toSet())
                for (name in listOf("bridge.mp4", "cat.mp4")) {
                    context.assets.openFd("b3/v2/$name").use { assertTrue(it.length > 0) }
                }
            }
        }
    }

    @Test fun nextSkipsOneSceneAndResultCompletesOnlyOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = CreditContent(DemoContent.trips.first(), null)
                var chapter = -1
                var finished = 0
                val summary = B3CreditView(activity, content, 2, onChapter = { chapter = it })
                assertFalse(summary.performClick())
                summary.findViewWithTag<View>("b3.next").performClick()
                assertEquals(5, chapter)
                val ending = B3CreditView(activity, content, 5, onComplete = { finished++ })
                assertFalse(ending.performClick())
                assertEquals("결과 보기", ending.findViewWithTag<TextView>("b3.next.label").text.toString())
                ending.findViewWithTag<View>("b3.next").performClick()
                ending.findViewWithTag<View>("b3.next").performClick()
                assertEquals(1, finished)
            }
        }
    }


    @Test fun playbackPausesPersistsRestoresAndFinishesIntoTheExistingResult() {
        val prefs = context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        try {
            val store = TripStore(context)
            store.activeTrip = null
            store.complete(DemoContent.trips.first().copy(mode = ResultMode.CREDIT))
            store.resume = CreditResume(DemoContent.trips.first().id, 0, 0)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    instrument.uiAutomation.executeShellCommand("am task focus ${activity.taskId}").close()
                    val method = MainActivity::class.java.getDeclaredMethod("resumeCredit",
                        Int::class.javaObjectType, Boolean::class.javaPrimitiveType)
                    method.isAccessible = true
                    method.invoke(activity, null, false)
                }
                await(scenario) { it.window.decorView.findViewWithTag<B3CreditView>("b3.credit")?.elapsedMillis?.let { time -> time > 150 } == true }
                var pausedTime = 0L
                scenario.onActivity { a ->
                    val view = a.window.decorView.findViewWithTag<B3CreditView>("b3.credit")
                    view.setPaused(true); pausedTime = view.elapsedMillis
                    assertNotNull(view.findViewWithTag<View>("b3.roll"))
                }
                SystemClock.sleep(350)
                scenario.onActivity { assertEquals(pausedTime, it.window.decorView.findViewWithTag<B3CreditView>("b3.credit").elapsedMillis) }
                scenario.moveToState(Lifecycle.State.CREATED)
                val checkpoint = TripStore(context).resume!!
                assertEquals(CreditPlayback.SCREENING_FRAME, checkpoint.frame); assertEquals(pausedTime, checkpoint.elapsedMillis)
                SystemClock.sleep(350)
                assertEquals(checkpoint, TripStore(context).resume)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.recreate()
                await(scenario) { it.window.decorView.findViewWithTag<B3CreditView>("b3.credit")?.isShown == true }
                scenario.onActivity { a ->
                    val view = a.window.decorView.findViewWithTag<B3CreditView>("b3.credit")
                    assertTrue(abs(view.elapsedMillis - checkpoint.elapsedMillis) < 1500)
                    assertEquals(CreditPlayback.SCREENING_FRAME, view.frame)
                    assertNull(view.findViewWithTag<View>("b3.video.bridge"))
                    view.setPaused(false)
                }
                scenario.onActivity { it.window.decorView.findViewWithTag<B3CreditView>("b3.credit").seekTo(15_850) }
                await(scenario) { it.window.decorView.findViewWithTag<B3CreditView>("b3.credit") == null }
                assertNull(TripStore(context).resume)
            }
        } finally {
            prefs.edit().clear().apply {
                original.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value); is Int -> putInt(key, value)
                    is Long -> putLong(key, value); is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value); is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                } }
            }.commit()
        }
    }

    private fun videoHasFrame(video: TextureView): Boolean {
        if (!video.isAvailable) return false
        val image = video.getBitmap(48, 32) ?: return false
        return try {
            val colors = IntArray(48 * 32)
            image.getPixels(colors, 0, 48, 0, 0, 48, 32)
            colors.toSet().size > 8
        } finally {
            image.recycle()
        }
    }

    private fun saveScreen(name: String) {
        val screenshot = instrument.uiAutomation.takeScreenshot() ?: return
        val directory = File(context.filesDir, "b3-redesign-verification").apply { mkdirs() }
        try {
            File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }
    }

    private fun await(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        do {
            var satisfied = false
            scenario.onActivity { satisfied = condition(it) }
            if (satisfied) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        var details = ""
        scenario.onActivity { a ->
            val view = a.window.decorView.findViewWithTag<B3CreditView>("b3.credit")
            details = "focus=${a.hasWindowFocus()}, lifecycle=${a.lifecycle.currentState}, chapter=${view?.frame}, elapsed=${view?.elapsedMillis}, shown=${view?.isShown}, viewFocus=${view?.hasWindowFocus()}, text=" +
                nodes(a.window.decorView).filterIsInstance<TextView>().filter { it.isShown }.joinToString { it.text.toString() }
        }
        fail("B3 UI did not reach the expected state: $details")
    }
}
