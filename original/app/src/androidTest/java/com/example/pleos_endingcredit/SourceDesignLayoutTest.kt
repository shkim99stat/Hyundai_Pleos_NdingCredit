package com.example.pleos_endingcredit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
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
import kotlin.math.min

/** Coordinates and text expectations are independently extracted from the user's JSON. */
@RunWith(AndroidJUnit4::class)
class SourceDesignLayoutTest {
    @Test fun allFiveSourceScreensKeepGeometryTypographyAndBaselines() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val requestedScreen = InstrumentationRegistry.getArguments().getString("sourceScreen")
        val contract = JSONObject(instrument.context.assets.open("source-screen-contract.json")
            .bufferedReader().use { it.readText() })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val screens = contract.getJSONArray("screens")
                for (i in 0 until screens.length()) {
                    val screen = screens.getJSONObject(i)
                    val code = screen.getString("code")
                    if (requestedScreen != null && code != requestedScreen) continue
                    // The A0 and result mocks have dedicated checks for their updated designs.
                    if (code == "A0" || code == "A4") continue
                    val dw = screen.getDouble("width").toFloat()
                    val dh = screen.getDouble("height").toFloat()
                    for ((width, height) in listOf(dw.toInt() to dh.toInt(),
                        (dw / 2).toInt() to (dh / 2).toInt(), 1682 to 1210)) {
                        val root: ViewGroup = when (code) {
                            "A1" -> A1PayoutSetupView(activity) {}
                            "A3" -> A3TripStartView(activity)
                            else -> B2CreditPreparingView(activity, CreditContent(DemoContent.trips.first().copy(name = "우리의 강릉 여행"), null)) {}
                        }
                        AppTypography.applyTo(root)
                        root.measure(exact(width), exact(height))
                        root.layout(0, 0, width, height)
                        val scale = min(width / dw, height / dh)
                        val ox = (width - dw * scale) / 2
                        val oy = (height - dh * scale) / 2
                        val nodes = screen.getJSONArray("nodes")
                        for (j in 0 until nodes.length()) {
                            val expected = nodes.getJSONObject(j)
                            val tag = expected.getString("tag")
                            val view = requireNotNull(root.findViewWithTag<View>(tag)) { "$code missing $tag" }
                            val rect = Rect().also {
                                view.getDrawingRect(it)
                                root.offsetDescendantRectToMyCoords(view, it)
                            }
                            val prefix = "$code $tag ${width}x$height"
                            fun near(label: String, value: Double, actual: Int) =
                                assertEquals("$prefix $label", value, actual.toDouble(), 1.6)
                            if (!expected.optBoolean("skipPosition")) {
                                near("x", ox + expected.getDouble("x") * scale, rect.left)
                                near("y", oy + expected.getDouble("y") * scale, rect.top)
                            }
                            if (!expected.optBoolean("flexibleWidth"))
                                near("width", expected.getDouble("width") * scale, rect.width())
                            near("height", expected.getDouble("height") * scale, rect.height())
                            if (view is TextView && expected.has("fontSize")) {
                                val hint = expected.optBoolean("hint")
                                assertEquals(prefix, expected.getString("text"), (if(hint) view.hint else view.text).toString())
                                assertEquals(prefix, expected.getDouble("fontSize") * scale, view.textSize.toDouble(), .1)
                                assertEquals(prefix, expected.getInt("weight"), view.typeface.weight)
                                assertEquals(prefix, Color.parseColor(expected.getString("color")),
                                    if(hint) view.hintTextColors.defaultColor else view.currentTextColor)
                                near("baseline", expected.getDouble("baseline") * scale, view.baseline)
                                if (view is ReferenceDesignText) {
                                    val paint = android.text.TextPaint(view.paint).apply {
                                        textSize = view.designSize
                                        isSubpixelText = true; isLinearText = true
                                        hinting = android.graphics.Paint.HINTING_OFF
                                    }
                                    assertTrue("$prefix clipped drawn text",
                                        paint.measureText(view.text.toString()) * view.drawingScale <= view.width + 1f)
                                }
                            }
                        }
                        val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                        root.draw(Canvas(bitmap))
                        val folder = File(activity.filesDir,"design-verification").apply { mkdirs() }
                        File(folder,"audit-$code-${width}x$height.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                        }
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun exact(n: Int) = View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.EXACTLY)
}
