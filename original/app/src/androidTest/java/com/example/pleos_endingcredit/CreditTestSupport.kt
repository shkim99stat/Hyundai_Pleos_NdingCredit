package com.example.pleos_endingcredit

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.withText

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup

/** Existing navigation tests now assert the real B3 chapter rather than the removed wireframe header. */
internal fun creditChapterShown(root: View, expected: String): Boolean {
    val number = Regex("N딩 크레딧\\s+(\\d+) / 6").matchEntire(expected)?.groupValues?.get(1)?.toIntOrNull() ?: return false
    return creditTestView(root)?.let { it.isShown && it.frame == number - 1 } == true
}

internal fun creditTestAction(activity: MainActivity, action: String): Boolean {
    val credit = creditTestView(activity.window.decorView)?.takeIf { it.isShown } ?: return false
    return when (action) {
        "닫기" -> { activity.onBackPressedDispatcher.onBackPressed(); true }
        "다음", "결과 보기" -> credit.onKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        else -> false
    }
}

private fun creditTestView(root: View): B3CreditView? {
    if (root is B3CreditView) return root
    if (root is ViewGroup) for (i in root.childCount - 1 downTo 0)
        creditTestView(root.getChildAt(i))?.let { return it }
    return null
}
internal fun selectCatalogResume(copy: String = "보던 크레딧 이어 보기") {
    onView(withText(copy)).inRoot(isPlatformPopup()).perform(click())
}
