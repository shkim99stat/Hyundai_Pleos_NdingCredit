package com.example.pleos_endingcredit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
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
import java.io.File

/** Exercises the actual navigation callbacks and writes the six rendered surfaces. */
@RunWith(AndroidJUnit4::class)
class DesignedScreensNavigationTest {
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

    @Test fun sixScreensAndCreditResumeRemainConnected() {
        scenario.onActivity { activity -> assertFalse(findText(activity.window.decorView, "동의 후 시작").isEnabled) }
        capture("01-consent")
        acceptConsent()
        capture("02-home")
        assertShown("이동비를 받을 곳을")
        assertShown("휴대폰 연결을 기다리는 중...")
        clickText("나중에 할게요")
        clickText("여행 기록 시작하기")
        fill("예: 우리의 강릉 여행", "우리의 제주도 여행")
        fill("4명", "4")
        scenario.recreate()
        settle()
        assertShown("우리의 제주도 여행")
        capture("04-start")
        clickText("여행 시작")
        clickText("여행 끝")
        clickText("여행 끝")
        capture("05-choice")
        clickDescription("N딩 크레딧 시작")
        clickText("영상 없이 만들기")
        capture("06-preparing")
        // Re-entering a partially viewed credit should work from the archive too.
        awaitShown("N딩 크레딧  1 / 6")
        clickText("닫기")
        capture("03-archive")
        scenario.onActivity { assertTrue(it.window.decorView.findViewWithTag<View>("f1.year").performClick()) }
        selectCatalogResume("보던 크레딧 처음부터")
        settle()
        assertShown("N딩 크레딧  1 / 6")
        clickText("닫기")
        assertShown("전체 상영관")
        scenario.recreate()
        settle()
        assertShown("전체 상영관")
    }

    @Test fun eachChoiceStartsItsOwnResultMode() {
        // One UI transaction keeps the preparation timer and other app windows
        // from advancing/hiding the short-lived B2 surface between assertions.
        scenario.onActivity { activity ->
            fun current() = nodes(activity.window.decorView)
            fun click(value: String) {
                if (creditTestAction(activity, value)) return
                val control = current().filterIsInstance<TextView>().last { it.text.toString() == value }
                assertTrue("Disabled control: $value", control.isEnabled)
                if (control is CompoundButton) {
                    val before = control.isChecked
                    control.performClick()
                    assertTrue("Checkbox did not toggle: $value", before != control.isChecked)
                } else assertTrue("Missing click listener: $value", control.performClick())
            }
            fun contains(value: String) = creditChapterShown(activity.window.decorView, value) || current().filterIsInstance<TextView>().any { it.text.toString() == value }
            activity.window.decorView.checkRequiredConsents()
            click("동의 후 시작")
            click("나중에 할게요")
            listOf("N빵 정산", "둘 다").forEach { mode ->
                assertTrue(current().last { it.tag == "home.start" }.performClick())
                click("여행 시작")
                click("여행 끝")
                click("여행 끝")
                assertTrue(current().last { it.contentDescription?.toString() == "$mode 시작" }.performClick())
                if (mode == "둘 다") {
                    click("영상 없이 만들기")
                    assertTrue(current().any { it is B2CreditPreparingView })
                    capturePreparingComponent(activity)
                    val skip = activity.window.decorView.findViewWithTag<View>("b2.skip")
                    assertTrue(skip.performClick())
                }
                if (mode == "둘 다") {
                    assertTrue(contains("이동비도 나누기"))
                    click("이동비도 나누기")
                    assertTrue(contains("27,320원 → 1인 6,830원"))
                    click("닫기")
                    assertTrue(current().last { it.contentDescription?.toString() == "닫기" }.performClick())
                } else {
                    assertTrue(contains("1인 6,830원"))
                    click("닫기")
                }
            }
        }
    }

    @Test fun introductionReturnsOnFreshLaunchAndLauncherReentry() {
        acceptConsent()
        assertTrue(prefs.getBoolean("consent", false))

        // A window/configuration recreation must keep the current workflow.
        scenario.recreate()
        settle()
        assertShown("이동비를 받을 곳을")

        // A new launch must show the introduction even after prior acceptance.
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle()
        assertShown("여행 기록을 위해 약관에 동의해 주세요.")

        acceptConsent()

        // The manifest reuses MainActivity when the launcher is tapped again.
        context.startActivity(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitShown("여행 기록을 위해 약관에 동의해 주세요.")
        scenario.onActivity { activity ->
            assertFalse(findText(activity.window.decorView, "동의 후 시작").isEnabled)
        }
    }

    @Test fun renewedConsentShowsA1ThenA2BeforeTheUserReopensTheSavedDraft() {
        scenario.onActivity { activity ->
            val state = Bundle().apply {
                putString("page", "CONSENT")
                putString("consent_return_page", "START")
                putString("draft_name", "유지할 여행")
                putString("draft_people", "5")
            }
            MainActivity::class.java.getDeclaredMethod("restore", Bundle::class.java)
                .apply { isAccessible = true }.invoke(activity, state)
        }
        acceptConsent()
        assertShown("이동비를 받을 곳을")
        assertAbsent("새 여정 만들기")
        scenario.recreate()
        settle()
        assertShown("이동비를 받을 곳을")
        clickText("나중에 할게요")
        assertShown("N딩 크레딧")
        assertAbsent("유지할 여행")
        clickText("여행 기록 시작하기")
        assertShown("유지할 여행")
        assertShown("5")
        assertFalse(prefs.contains("active_trip"))
    }

    private fun acceptConsent() {
        scenario.onActivity { activity ->
            assertFalse(findText(activity.window.decorView, "동의 후 시작").isEnabled)
            activity.window.decorView.checkRequiredConsents()
        }
        clickText("동의 후 시작")
    }

    private fun assertAbsent(value: String) = scenario.onActivity {
        assertFalse(nodes(it.window.decorView).any { view ->
            view is TextView && view.text.toString() == value && view.isShown
        })
    }

    private fun assertShown(value: String) = scenario.onActivity {
        assertTrue("Missing visible text: $value", creditChapterShown(it.window.decorView, value) || nodes(it.window.decorView).any { view ->
            view is TextView && view.text.toString() == value && view.isShown
        })
    }

    private fun awaitShown(value: String) {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            var visible = false
            scenario.onActivity { activity -> visible = creditChapterShown(activity.window.decorView, value) || nodes(activity.window.decorView).any {
                it is TextView && it.text.toString() == value && it.isShown
            } }
            if (visible) return
            Thread.sleep(100)
        }
        assertShown(value)
    }

    private fun clickText(value: String) {
        scenario.onActivity { activity ->
            if (creditTestAction(activity, value)) return@onActivity
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

    private fun capture(name: String) {
        settle()
        if (name in listOf("02-home", "05-choice", "06-preparing")) {
            // Pleos may return a null hardware buffer for an unfocused window.
            // The required native-window capture below still runs in that case.
            runCatching { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() }
                .getOrNull()?.let { device ->
                    val folder = File(context.filesDir, "design-verification").apply { mkdirs() }
                    File(folder, "$name-device.png").outputStream().use { device.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    device.recycle()
                }
        }
        scenario.onActivity { activity ->
            if (name == "06-preparing") capturePreparingComponent(activity)
            if (name == "02-home") {
                val component = A1PayoutSetupView(activity) {}
                component.measure(View.MeasureSpec.makeMeasureSpec(1010, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(712, View.MeasureSpec.EXACTLY))
                component.layout(0, 0, 1010, 712)
                val reference = Bitmap.createBitmap(1010, 712, Bitmap.Config.ARGB_8888)
                component.draw(Canvas(reference))
                val target = File(context.filesDir, "design-verification").apply { mkdirs() }
                File(target, "home-reference-scale.png").outputStream().use {
                    reference.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                reference.recycle()
            }
            val root = activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val sharedOutput = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            val folder = (sharedOutput?.let { File(it) }
                ?: File(context.filesDir, "design-verification")).apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun capturePreparingComponent(activity: MainActivity) {
        for ((width, height) in listOf(1700 to 1184, 850 to 592, 420 to 592)) {
            val component = B2CreditPreparingView(activity,
                CreditContent(Trip(name = "우리의 강릉 여행", people = 4), null)) {}
            AppTypography.applyTo(component)
            component.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            component.layout(0, 0, width, height)
            val reference = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            component.draw(Canvas(reference))
            nodes(component).filterIsInstance<TextView>().forEach { label ->
                // The source-sized run is drawn uniformly; the platform's
                // separately hinted small-size Layout is not the displayed run.
                val metrics = label as ReferenceDesignText
                val paint = android.text.TextPaint(label.paint).apply {
                    textSize = metrics.designSize
                    isSubpixelText = true; isLinearText = true
                    hinting = android.graphics.Paint.HINTING_OFF
                }
                val drawnWidth = paint.measureText(label.text.toString()) * metrics.drawingScale
                assertTrue("B2 copy clipped at ${width}x$height: ${label.text}",
                    drawnWidth <= label.width + 1f)
            }
            val target = File(context.filesDir, "design-verification").apply { mkdirs() }
            File(target, "b2-${width}x$height.png").outputStream().use {
                reference.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            reference.recycle()
        }
    }

    private fun findText(root: View, value: String) = nodes(root).lastOrNull {
        it is TextView && it.text.toString() == value && it.isShown
    } ?: error("Missing visible text: $value; shown: ${nodes(root).filterIsInstance<TextView>().filter { it.isShown }.map { it.text }}")
    private fun nodes(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { nodes(root.getChildAt(it)) } else emptyList()
    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(180)
    }
}
