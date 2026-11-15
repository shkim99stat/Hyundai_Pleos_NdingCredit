package com.example.pleos_endingcredit

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run each method with freshly cleared test-app permissions. Uses the real OS dialog. */
@RunWith(AndroidJUnit4::class)
class StartupPermissionFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var originalPreferences: Map<String, *>? = null

    @Before fun launchWithoutPermissions() {
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        assertFalse("Run this test with fresh test-app permissions", TripRecordingService.hasRecordingPermissions(context))
        val prefs = context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE)
        originalPreferences = prefs.all.toMap()
        prefs.edit().clear().putInt(DemoContent.VERSION_KEY, DemoContent.VERSION).commit()
        TripStore(context).complete(Trip(name = "이전 여행", people = 2))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        await { it.findViewWithTag<View>("a0.accept")?.isShown == true }
        assertNoPermissionDialog()
    }

    @After fun stopTestRecording() {
        val original = originalPreferences ?: return
        val createdTrip = TripStore(context).activeTrip?.id
        TripRecordingService.stop(context)
        if (::scenario.isInitialized) scenario.close()
        createdTrip?.let { TripRecordingRepository.get(context).delete(it) }
        context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE).edit().clear().apply {
            original.forEach { (key, value) -> when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
            } }
        }.commit()
    }

    @Test fun deniedPermissionStaysAtA0AndRecreationDoesNotRequestAgain() {
        acceptA0()
        answerSystemPermissions(allow = false)
        await { it.findViewWithTag<View>("a0.accept")?.isShown == true }
        assertFalse(TripStore(context).hasConsent)
        assertNull(TripStore(context).activeTrip)
        live { it.recreate() }
        await { it.findViewWithTag<View>("a0.accept")?.isShown == true }
        assertNoPermissionDialog()
    }

    @Test fun a0GrantsPermissionsBeforeHomeAndTripStartNeverRequestsAgain() {
        acceptA0()
        answerSystemPermissions(allow = true)
        await { it.findViewWithTag<View>("a03.setup")?.isShown == true }
        tap("a03.later")
        await { it.findViewWithTag<View>("home.a2")?.isShown == true }
        assertTrue(TripStore(context).hasConsent)
        assertTrue(TripRecordingService.hasRecordingPermissions(context))
        assertNull("A0 must not create a trip", TripStore(context).activeTrip)
        startDraft("A0 권한 연결", "3")
        tap("a3.begin")
        await { it.findViewWithTag<View>("a4.start.notice")?.isShown == true }
        live {
            val notice = it.window.decorView.findViewWithTag<TextView>("a4.start.notice")
            assertEquals("이제부터 기록할게요. 운전하는 동안엔 화면을 띄우지 않아요.", notice.text.toString())
        }
        assertEquals("A0 권한 연결", TripStore(context).activeTrip?.name)
        assertEquals(3, TripStore(context).activeTrip?.people)
        assertNoPermissionDialog()
        await { it.findViewWithTag<View>("a4.start.notice")?.isShown == false }
        live { it.recreate() }
        await { it.findViewWithTag<View>("a4.recording")?.isShown == true }
        live { assertFalse(it.window.decorView.findViewWithTag<View>("a4.start.notice").isShown) }
    }

    @Test fun missingPermissionInRestoredA3ReturnsToA0AndPreservesTheDraft() {
        // Reproduce a saved home from the previous build, whose A0 only stored consent.
        TripStore(context).hasConsent = true
        live { activity ->
            MainActivity::class.java.getDeclaredMethod("restore", Bundle::class.java).apply { isAccessible = true }
                .invoke(activity, Bundle().apply { putString("page", "HOME") })
            MainActivity::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(activity)
        }
        startDraft("유지할 여행", "5")
        tap("a3.begin")
        await { it.findViewWithTag<View>("a0.accept")?.isShown == true }
        assertNoPermissionDialog()
        assertNull(TripStore(context).activeTrip)
        live { it.recreate() }
        await { it.findViewWithTag<View>("a0.accept")?.isShown == true }
        assertNoPermissionDialog()
        acceptA0()
        answerSystemPermissions(allow = true)
        await { it.findViewWithTag<View>("a03.setup")?.isShown == true }
        tap("a03.later")
        await { it.findViewWithTag<View>("home.a2")?.isShown == true }
        tap("home.start")
        await { it.findViewWithTag<View>("trip.start.overlay")?.isShown == true }
        live { activity ->
            assertEquals("유지할 여행", activity.window.decorView.findViewWithTag<EditText>("a3.name").text.toString())
            assertEquals("5", activity.window.decorView.findViewWithTag<EditText>("a3.people").text.toString())
        }
        assertNull("Granting permission must not automatically begin the saved draft", TripStore(context).activeTrip)
        tap("a3.begin")
        await { it.findViewWithTag<View>("a4.recording")?.isShown == true }
        assertEquals("유지할 여행", TripStore(context).activeTrip?.name)
        assertNoPermissionDialog()
    }

    private fun acceptA0() {
        live { it.window.decorView.checkRequiredConsents() }
        tap("a0.accept")
    }

    private fun startDraft(name: String, people: String) {
        tap("home.start")
        await { it.findViewWithTag<View>("trip.start.overlay")?.isShown == true }
        live { activity ->
            activity.window.decorView.findViewWithTag<EditText>("a3.name").setText(name)
            activity.window.decorView.findViewWithTag<EditText>("a3.people").setText(people)
        }
    }

    private fun answerSystemPermissions(allow: Boolean) {
        var answered = 0
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val standardButton = root?.let { nodes(it).firstOrNull { node ->
                val id = node.viewIdResourceName.orEmpty()
                if (allow) id.endsWith("/permission_allow_foreground_only_button") || id.endsWith("/permission_allow_button")
                else id.endsWith("/permission_deny_button")
            } }
            val carLabel = nodes(root).firstOrNull { node ->
                node.viewIdResourceName.orEmpty().endsWith("/car_ui_list_item_title") &&
                    node.text?.toString() in if (allow) listOf("앱 사용 중에만 허용", "허용", "While using the app", "Allow")
                    else listOf("허용 안함", "허용 안 함", "Don't allow")
            }
            val button = standardButton ?: carLabel?.parent?.let { nodes(it).firstOrNull { node -> node.isClickable } }
            if (button != null) {
                assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                answered++
                SystemClock.sleep(400)
            } else if (answered > 0 && !requestInFlight()) {
                assertEquals(allow, TripRecordingService.hasRecordingPermissions(context))
                return
            } else SystemClock.sleep(100)
        }
        fail("No completed OS permission response; active window=" + instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun requestInFlight(): Boolean {
        var value = false
        live { value = MainActivity::class.java.getDeclaredField("requestingConsentPermissions")
            .apply { isAccessible = true }.getBoolean(it) }
        return value
    }

    private fun assertNoPermissionDialog() {
        SystemClock.sleep(400)
        assertFalse(nodes(instrumentation.uiAutomation.rootInActiveWindow).any {
            it.viewIdResourceName.orEmpty().contains("permission_allow") ||
                it.viewIdResourceName.orEmpty().endsWith("/permission_deny_button") ||
                it.packageName?.toString() == "com.android.permissioncontroller"
        })
    }

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (root == null) emptyList()
        else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }

    private fun tap(tag: String) {
        await { it.findViewWithTag<View>(tag)?.isShown == true }
        live { assertTrue(it.window.decorView.findViewWithTag<View>(tag).performClick()) }
    }

    private fun live(action: (MainActivity) -> Unit) = instrumentation.runOnMainSync {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().last()
        action(activity)
    }

    private fun await(condition: (View) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var found = false
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().lastOrNull()?.let { found = condition(it.window.decorView) }
            }
            if (found) { SystemClock.sleep(300); return }
            SystemClock.sleep(100)
        }
        fail("Expected app surface did not appear")
    }
}
