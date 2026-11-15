package com.example.pleos_endingcredit

import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ConsentLayoutTest {
    @Test fun bothRequiredCheckboxesGateTheButtonAndDeselectionDisablesItAgain() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for ((width, height) in listOf(1696 to 1184, 848 to 592)) {
                    var accepted = 0
                    val surface = A0ConsentView(activity) { accepted++ }
                    AppTypography.applyTo(surface)
                    surface.measure(exact(width),exact(height))
                    surface.layout(0,0,width,height)
                    assertEquals(0,accepted)
                    assertEquals(2,nodes(surface).count { it is CompoundButton })
                    val action = surface.findViewWithTag<MaterialButton>("a0.accept")
                    val location = surface.findViewWithTag<CompoundButton>("a0.locationConsent")
                    val driving = surface.findViewWithTag<CompoundButton>("a0.drivingConsent")
                    val scale = width / 1696f
                    val buttonBounds = Rect()
                    action.getDrawingRect(buttonBounds)
                    surface.offsetDescendantRectToMyCoords(action, buttonBounds)
                    assertEquals(276.47f * scale, buttonBounds.left.toFloat(), 1f)
                    assertEquals(899 * scale, buttonBounds.top.toFloat(), 1f)
                    assertEquals(1143 * scale, buttonBounds.width().toFloat(), 1f)
                    assertEquals(96 * scale, buttonBounds.height().toFloat(), 1f)
                    assertEquals("동의 후 시작", action.text.toString())
                    fun expect(enabled: Boolean) {
                        assertEquals(enabled,action.isEnabled)
                        assertEquals(if(enabled) Color.rgb(0,102,255) else Color.rgb(225,230,238),
                            action.backgroundTintList!!.getColorForState(action.drawableState,Color.TRANSPARENT))
                        if(!enabled) {
                            action.performClick()
                            assertEquals("Accepted without both consents",0,accepted)
                        }
                    }
                    expect(false)
                    capture(surface,File(activity.filesDir,"design-verification"),"a0-new-$width-unchecked.png")
                    assertEquals(700,action.typeface.weight)
                    surface.findViewWithTag<View>("a0.location.label").performClick()
                    assertTrue(location.isChecked)
                    expect(false)
                    driving.performClick(); expect(true)
                    location.performClick(); expect(false)
                    driving.performClick(); expect(false)
                    driving.performClick(); expect(false)
                    location.performClick(); expect(true)
                    capture(surface,File(activity.filesDir,"design-verification"),"a0-new-$width-checked.png")
                    assertTrue(action.performClick())
                    assertEquals(1,accepted)
                }
            }
        }
    }

    @Test fun termsLinksOpenMatchingPopupsAndCloseWithoutChangingConsent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val surface = A0ConsentView(activity) { fail("Opening a popup must not grant consent") }
                fun layout() {
                    surface.measure(exact(1696), exact(1184))
                    surface.layout(0, 0, 1696, 1184)
                }
                layout()
                val location = surface.findViewWithTag<CompoundButton>("a0.locationConsent")
                val driving = surface.findViewWithTag<CompoundButton>("a0.drivingConsent")
                location.performClick()
                for ((link, title) in listOf(
                    "a0.location.first.view" to "위치·경로",
                    "a0.location.second.view" to "위치·경로",
                    "a0.driving.first.view" to "주행 기록",
                    "a0.driving.second.view" to "주행 기록",
                )) {
                    assertTrue(surface.findViewWithTag<View>(link).performClick())
                    layout()
                    val popup = surface.findViewWithTag<ViewGroup>("a0.terms")
                    assertEquals(title, popup.findViewWithTag<TextView>("a0.terms.title").text.toString())
                    assertFalse(popup.findViewWithTag<View>("a0.terms.full").isEnabled)
                    if (link.endsWith("first.view")) {
                        val name = if (link.contains("location")) "location" else "driving"
                        capture(surface, File(activity.filesDir, "design-verification"), "a0-new-terms-$name.png")
                    }
                    val close = if (link == "a0.location.second.view") "a0.terms.close" else "a0.terms.confirm"
                    assertTrue(popup.findViewWithTag<View>(close).performClick())
                    assertNull(surface.findViewWithTag<View>("a0.terms"))
                    assertTrue(location.isChecked)
                    assertFalse(driving.isChecked)
                    assertFalse(surface.findViewWithTag<View>("a0.accept").isEnabled)
                }
            }
        }
    }

    private fun capture(root:View,folder:File,name:String) {
        folder.mkdirs()
        root.jumpDrawablesToCurrentState()
        val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(folder,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    private fun exact(n:Int) = View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.EXACTLY)
    private fun nodes(v:View):List<View> = listOf(v) + if(v is ViewGroup)
        (0 until v.childCount).flatMap { nodes(v.getChildAt(it)) } else emptyList()
}
