package com.example.pleos_endingcredit

import android.view.View
import android.widget.CompoundButton
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertTrue

/** Enter the app through the same two required controls as the user. */
internal fun View.checkRequiredConsents() {
    for (tag in listOf("a0.locationConsent", "a0.drivingConsent")) {
        val checkbox = requireNotNull(findViewWithTag<CompoundButton>(tag)) { "Missing $tag" }
        if (!checkbox.isChecked) checkbox.performClick()
        assertTrue(checkbox.isChecked)
    }
    assertTrue(findViewWithTag<MaterialButton>("a0.accept").isEnabled)
}
