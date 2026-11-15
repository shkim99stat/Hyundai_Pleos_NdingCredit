package com.example.pleos_endingcredit

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.CompoundButton
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A3 input, calendar, validation and saved-state integration through the real app. */
@RunWith(AndroidJUnit4::class)
class TripStartFlowTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE)
    private var original: Map<String, *> = emptyMap<String, Any>()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launchCleanFixture() {
        original = prefs.all.toMap()
        prefs.edit().clear().putInt(DemoContent.VERSION_KEY, DemoContent.VERSION).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle()
    }

    @After fun restorePreferences() {
        scenario.close()
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

    @Test fun a3DraftDateAndValidationSurviveRecreation() {
        acceptConsent()
        clickText("나중에 할게요")
        assertShown("N딩 크레딧")
        assertFalse(prefs.contains("active_trip"))
        clickText("여행 기록 시작하기")
        fill("예: 우리의 강릉 여행", "제주 여행")
        fill("4명", "0")
        clickDescription("종료 예정일 (선택): 날짜 선택")
        androidx.test.espresso.Espresso.onView(
            androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1)
        ).perform(androidx.test.espresso.action.ViewActions.click())
        settle()
        val today = java.time.LocalDate.now()
        val dateText = today.format(java.time.format.DateTimeFormatter.ofPattern(
            "yyyy.MM.dd(E)", java.util.Locale.KOREAN))
        assertShown(dateText)
        scenario.recreate()
        settle()
        assertShown("제주 여행")
        assertShown(dateText)
        clickText("여행 시작")
        assertShown("새 여정 만들기")
        assertFalse(prefs.contains("active_trip"))
        fill("4명", "3")
        clickText("여행 시작")
        val trip = requireNotNull(TripStore(context).activeTrip)
        org.junit.Assert.assertEquals("제주 여행", trip.name)
        org.junit.Assert.assertEquals(3, trip.people)
        org.junit.Assert.assertEquals(today.toString(), trip.plannedEnd)
    }

    private fun acceptConsent() {
        scenario.onActivity { activity ->
            assertFalse(findText(activity.window.decorView, "동의 후 시작").isEnabled)
            activity.window.decorView.checkRequiredConsents()
        }
        clickText("동의 후 시작")
    }

    private fun assertShown(value: String) = scenario.onActivity {
        assertTrue("Missing visible text: $value", nodes(it.window.decorView).any { view ->
            view is TextView && view.text.toString() == value && view.isShown
        })
    }

    private fun awaitShown(value: String) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            var visible = false
            scenario.onActivity { activity -> visible = nodes(activity.window.decorView).any {
                it is TextView && it.text.toString() == value && it.isShown
            } }
            if (visible) return
            Thread.sleep(100)
        }
        assertShown(value)
    }

    private fun clickText(value: String) {
        awaitShown(value)
        scenario.onActivity { activity ->
            val view = findText(activity.window.decorView, value)
            assertTrue("Disabled control: $value", view.isEnabled)
            if (view is CompoundButton) {
                val previous = view.isChecked
                view.performClick()
                assertTrue("Checkbox did not toggle: $value", previous != view.isChecked)
            } else assertTrue("No click listener: $value", view.performClick())
        }
        settle()
    }

    private fun clickDescription(value: String) {
        scenario.onActivity { activity ->
            val view = nodes(activity.window.decorView).last { it.contentDescription?.toString() == value && it.isShown }
            assertTrue(view.performClick())
        }
        settle()
    }

    private fun fill(hint: String, value: String) = scenario.onActivity { activity ->
        (nodes(activity.window.decorView).first { it is EditText && it.hint.toString() == hint } as EditText).setText(value)
    }

    private fun findText(root: View, value: String): View {
        val labels = nodes(root).filterIsInstance<TextView>().filter { it.isShown }
        return labels.lastOrNull { it.text.toString() == value }
            ?: error("Missing visible text '$value'; present: " + labels.joinToString { it.text.toString() })
    }
    private fun nodes(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { nodes(root.getChildAt(it)) } else emptyList()
    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(180)
    }
}
