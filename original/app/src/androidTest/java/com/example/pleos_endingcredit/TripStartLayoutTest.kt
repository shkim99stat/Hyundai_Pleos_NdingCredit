package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Expected values are extracted from the supplied JSON, not from view code. */
@RunWith(AndroidJUnit4::class)
class TripStartLayoutTest {
    @Test fun a3MatchesTheSuppliedWindowAtFullAndHalfSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val contract = JSONObject(instrumentation.context.assets.open("a3-contract.json")
            .bufferedReader().use { it.readText() })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for ((width, height, scale) in listOf(Triple(1700, 1184, 1f), Triple(850, 592, .5f))) {
                    val surface = A3TripStartView(activity)
                    AppTypography.applyTo(surface)
                    measure(surface, width, height)
                    capture(surface, activity.filesDir, "a3-${width}x$height-empty.png")
                    val fixtures = contract.getJSONArray("nodes")
                    for (index in 0 until fixtures.length()) {
                        val spec = fixtures.getJSONObject(index)
                        val tag = spec.getString("tag")
                        val view = surface.findViewWithTag<View>(tag)
                        assertNotNull("Missing A3 node: $tag", view)
                        val actual = Rect().also {
                            view.getDrawingRect(it)
                            (surface as ViewGroup).offsetDescendantRectToMyCoords(view, it)
                        }
                        fun near(key: String, value: Int) = assertEquals("$tag $key",
                            spec.getDouble(key) * scale, value.toDouble(), 1.1)
                        near("x", actual.left); near("y", actual.top)
                        near("width", actual.width()); near("height", actual.height())
                        if (view is TextView && spec.has("text")) {
                            val copy = spec.getString("text")
                            val hint = spec.optBoolean("hint")
                            assertEquals(tag, copy, (if (hint) view.hint else view.text).toString())
                            assertEquals(tag, spec.getDouble("fontSize") * scale, view.textSize.toDouble(), .1)
                            assertEquals(tag, spec.getDouble("lineHeight") * scale, view.lineHeight.toDouble(), 1.1)
                            assertEquals(tag, Color.parseColor(spec.getString("color")),
                                if (hint) view.hintTextColors.defaultColor else view.currentTextColor)
                            assertEquals(tag, spec.getInt("weight"), view.typeface.weight)
                            val glyphWidth = if (view is ReferenceDesignText) {
                                // Reference labels shape at source size before scaling.
                                val drawingPaint = TextPaint(view.paint).apply {
                                    textSize = view.designSize
                                    flags = flags or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
                                    hinting = Paint.HINTING_OFF
                                }
                                drawingPaint.measureText(copy) * view.drawingScale
                            } else view.paint.measureText(copy)
                            assertTrue("Clipped A3 text: $copy ($glyphWidth > ${view.width})", glyphWidth <=
                                view.width - view.paddingLeft - view.paddingRight + 1)
                        }
                    }
                    for (index in 0 until fixtures.length()) {
                        val spec = fixtures.getJSONObject(index)
                        if (!spec.optBoolean("hint")) continue
                        val input = surface.findViewWithTag<TextView>(spec.getString("tag"))
                        assertTrue("Example saved as input", input.text.isEmpty())
                        assertEquals(spec.getDouble("radius") * scale,
                            (input.background as GradientDrawable).cornerRadius.toDouble(), .1)
                        assertEquals(spec.getDouble("paddingLeft") * scale, input.paddingLeft.toDouble(), 1.1)
                        assertEquals(spec.getDouble("paddingTop") * scale, input.paddingTop.toDouble(), 1.1)
                    }
                    val image = bitmap(surface)
                    assertEquals(Color.rgb(199, 201, 206), image.getPixel((845 * scale).toInt(),
                        (445 * scale).toInt() + 1))
                    assertEquals(Color.rgb(54, 125, 255), image.getPixel((400 * scale).toInt(),
                        (960 * scale).toInt()))
                    assertEquals(Color.rgb(251, 252, 255), image.getPixel((1100 * scale).toInt(),
                        (750 * scale).toInt()))
                    image.recycle()
                    assertFalse(nodes(surface).any { it.contentDescription?.toString() == "닫기" })
                }
            }
        }
    }

    @Test fun inputCallbacksAndShortViewportKeepTheFormUsable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var name = ""; var people = ""; var date = ""; var began = false
                val surface = A3TripStartView(activity, "", "", "",
                    { name = it }, { people = it }, { date = it }, { began = true })
                AppTypography.applyTo(surface)
                measure(surface, 850, 320)
                surface.findViewWithTag<TextView>("a3.name").text = "제주 여행"
                surface.findViewWithTag<TextView>("a3.people").text = "3"
                assertEquals("제주 여행", name)
                assertEquals("3", people)
                surface.findViewWithTag<View>("a3.plus").performClick()
                assertEquals("4", people)
                surface.findViewWithTag<View>("a3.minus").performClick()
                assertEquals("3", people)
                surface.findViewWithTag<TextView>("a3.people").text = "99"
                surface.findViewWithTag<View>("a3.plus").performClick()
                assertEquals("99", people)
                surface.findViewWithTag<TextView>("a3.people").text = "1"
                surface.findViewWithTag<View>("a3.minus").performClick()
                assertEquals("1", people)
                assertEquals("", date)
                assertFalse(began)
                assertTrue(surface.getChildAt(0).height >= 592)
                surface.scrollTo(0, surface.getChildAt(0).height - surface.height)
                val action = surface.findViewWithTag<View>("a3.begin")
                val rect = Rect().also {
                    action.getDrawingRect(it)
                    surface.offsetDescendantRectToMyCoords(action, it)
                    it.offset(-surface.scrollX, -surface.scrollY)
                }
                assertTrue("Start action is unreachable in short viewport", rect.top >= 0 && rect.bottom <= surface.height)
                assertTrue(action.performClick())
                assertTrue(began)
                capture(surface, activity.filesDir, "a3-850x320-scrolled.png")
            }
        }
    }

    private fun measure(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
    private fun bitmap(view: View) = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
        view.draw(Canvas(it))
    }
    private fun capture(view: View, filesDir: File, name: String) {
        val image = bitmap(view)
        val directory = File(filesDir, "design-verification").apply { mkdirs() }
        File(directory, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun nodes(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { nodes(root.getChildAt(it)) } else emptyList()
}
