package com.example.pleos_endingcredit

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class A2FinalFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun populatedHistoryStillOpensTheBlueHomeAndBothBranches() {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("travel_crews", 0)
        val saved = preferences.getString("crews", null)
        val seeded = preferences.contains("demo_crews_seeded")
        preferences.edit().remove("crews").remove("demo_crews_seeded").commit()
        try {
            val examples = listOf("가족 크루" to 4, "욜로 크루" to 3, "성지 뱀장어" to 2)
            assertEquals(examples, TravelCrewStore(context).list())
            preferences.edit().remove("crews").commit()
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                if (!exists(activity, "home.start")) {
                    setChecked(activity, "a0.locationConsent")
                    setChecked(activity, "a0.drivingConsent")
                    click(activity, "a0.accept")
                }
                await(activity, "home.start")
                assertTrue(TripStore(context).trips.isNotEmpty())
                lateinit var home: View
                activity.onActivity {
                    val root = it.window.decorView
                    home = checkNotNull(root.findViewWithTag<View>("home.a2"))
                    assertEquals("여행 준비하기", root.findViewWithTag<TextView>("home.start").text.toString())
                    assertEquals("이전 여행 다시보기", root.findViewWithTag<TextView>("home.archive").text.toString())
                    assertNull(root.findViewWithTag<View>("a03.later"))
                }
                capture("home")
                click(activity, "home.start")
                await(activity, "a2.crew")
                activity.onActivity {
                    val root = it.window.decorView
                    assertEquals("새 크루를 등록해주세요.", root.findViewWithTag<TextView>("crew.title").text.toString())
                    assertNull(root.findViewWithTag<View>("crew.option.1"))
                }
                capture("crew")
                click(activity, "crew.next")
                await(activity, "a3.begin")
                activity.onActivity {
                    val root = it.window.decorView
                    assertSame(home, root.findViewWithTag<View>("home.a2"))
                    assertTrue(home.isShown)
                    assertNotNull(root.findViewWithTag<View>("trip.start.overlay"))
                    assertNotNull(root.findViewWithTag<View>("a3.people"))
                    assertEquals("비워 두면 '${DemoScenario.TITLE}'로 시작할게요.",
                        root.findViewWithTag<TextView>("a3.nameHelp").text.toString())
                }
                capture("new-crew")

                click(activity, "a3.close")
                await(activity, "home.start")
                activity.onActivity {
                    val root = it.window.decorView
                    assertSame(home, root.findViewWithTag<View>("home.a2"))
                    assertNull(root.findViewWithTag<View>("trip.start.overlay"))
                }
                TravelCrewStore(context).apply {
                    examples.asReversed().forEach { (name, people) -> save(name, people) }
                }
                click(activity, "home.start")
                await(activity, "crew.option.3")
                activity.onActivity {
                    val root = it.window.decorView
                    assertEquals("어떤 크루와 여행하시나요?", root.findViewWithTag<TextView>("crew.title").text.toString())
                    assertEquals("욜로 크루", root.findViewWithTag<TextView>("crew.name.2").text.toString())
                    assertEquals("탑승 인원 3명", root.findViewWithTag<TextView>("crew.people.2").text.toString())
                    assertTrue(root.findViewWithTag<View>("crew.option.1").isSelected)
                }
                capture("saved-crews")
                click(activity, "crew.option.2")
                activity.onActivity {
                    assertTrue(it.window.decorView.findViewWithTag<View>("crew.option.2").isSelected)
                }
                click(activity, "crew.next")
                await(activity, "a3.begin")
                activity.onActivity {
                    val root = it.window.decorView
                    assertSame(home, root.findViewWithTag<View>("home.a2"))
                    assertTrue(home.isShown)
                    assertNotNull(root.findViewWithTag<View>("trip.start.overlay"))
                    assertNull(root.findViewWithTag<View>("a3.people"))
                }
                capture("existing-crew")
                click(activity, "a3.close")

                click(activity, "home.archive")
                await(activity, "f1.catalog")
                capture("archive")
                click(activity, "f1.calendar")
                await(activity, "archive.calendar")
                activity.onActivity {
                    val calendar = it.window.decorView.findViewWithTag<View>("archive.calendar")
                    assertEquals("F1-1", calendar.getTag(R.id.ending_credit_screen_id))
                }
                click(activity, "archive.calendar.preset.3")
                capture("calendar")
                click(activity, "archive.calendar.apply")
                await(activity, "f1.calendar")
                activity.onActivity {
                    assertTrue(it.window.decorView.findViewWithTag<View>("f1.calendar").isSelected)
                    assertNull(it.window.decorView.findViewWithTag<View>("archive.calendar"))
                }
                click(activity, "f1.back")
                await(activity, "home.start")
                assertNull(TripStore(context).activeTrip)
            }
        } finally {
            preferences.edit().apply {
                if (saved == null) remove("crews") else putString("crews", saved)
                if (!seeded) remove("demo_crews_seeded")
            }.commit()
        }
    }

    private fun capture(name: String) {
        SystemClock.sleep(480)
        val folder = File(instrumentation.targetContext.filesDir, "final-flow-qa").apply { mkdirs() }
        val image = instrumentation.uiAutomation.takeScreenshot()
        try { File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }

    private fun setChecked(activity: ActivityScenario<MainActivity>, tag: String) {
        await(activity, tag)
        activity.onActivity {
            val check = it.window.decorView.findViewWithTag<CompoundButton>(tag)
            if (!check.isChecked) check.performClick()
        }
    }

    private fun click(activity: ActivityScenario<MainActivity>, tag: String) {
        await(activity, tag)
        activity.onActivity {
            assertTrue("Missing click $tag", it.window.decorView.findViewWithTag<View>(tag).performClick())
        }
    }

    private fun await(activity: ActivityScenario<MainActivity>, tag: String) {
        repeat(70) {
            if (exists(activity, tag)) return
            SystemClock.sleep(100)
        }
        error("Missing $tag")
    }

    private fun exists(activity: ActivityScenario<MainActivity>, tag: String): Boolean {
        var found = false
        activity.onActivity {
            val view = it.window.decorView.findViewWithTag<View>(tag)
            found = view != null && view.isShown && view.isLaidOut && view.isEnabled
        }
        return found
    }
}
