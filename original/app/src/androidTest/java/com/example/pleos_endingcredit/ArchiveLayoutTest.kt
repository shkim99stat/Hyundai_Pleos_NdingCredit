package com.example.pleos_endingcredit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.min

/** Expected geometry, font metrics and original-image hashes come from frame 96:21524. */
@RunWith(AndroidJUnit4::class)
class ArchiveLayoutTest {
    @Test fun requestedArchiveMatchesSourceAndUsesAllFourOriginalPhotos() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val contract=JSONObject(instrument.context.assets.open("archive-contract.json").bufferedReader().use { it.readText() })
        val source=contract.getJSONArray("nodes").let { a -> (0 until a.length()).associate {
            val n=a.getJSONObject(it); n.getString("id") to n
        } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val resources=listOf(R.drawable.archive_gangneung,R.drawable.archive_gyeongju,
                    R.drawable.archive_busan,R.drawable.archive_sokcho)
                resources.forEachIndexed { i,res ->
                    val bytes=activity.resources.openRawResource(res).use { it.readBytes() }
                    val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                    assertEquals(contract.getJSONArray("images").getJSONObject(i).getString("sha256"),hash)
                }
                val trips=listOf("강릉 여행","경주 여행","부산 여행","속초 여행").map {
                    Trip(name=it,people=4,createdOn="2026-11-08")
                }
                for((width,height) in listOf(1700 to 1184,850 to 592,1682 to 1210)) {
                    val root=ReferenceArchiveView(activity,trips,pending=trips.first().copy(name="우리의 강릉 여행"),pendingFrame=2)
                    AppTypography.applyTo(root)
                    measure(root,width,height)
                    val scale=min(width/1700f,height/1184f)
                    val ox=(width-1700*scale)/2; val oy=(height-1184*scale)/2
                    fun check(tag:String,id:String,widthToo:Boolean=true,textSource:String?=null) {
                        val view=requireNotNull(root.findViewWithTag<View>(tag)) { "Missing $tag" }
                        val n=source.getValue(id)
                        val b=Rect().also { view.getDrawingRect(it); root.offsetDescendantRectToMyCoords(view,it) }
                        fun near(label:String,expected:Double,actual:Int)=assertEquals("$tag $label ${width}x$height",expected,actual.toDouble(),1.6)
                        near("x",ox+n.getDouble("x")*scale,b.left)
                        near("y",oy+n.getDouble("y")*scale,b.top)
                        if(widthToo)near("width",n.getDouble("width")*scale,b.width())
                        near("height",n.getDouble("height")*scale,b.height())
                        if(view is TextView) {
                            val text=source.getValue(textSource?:id)
                            assertEquals(text.getDouble("fontSize")*scale,view.textSize.toDouble(),.1)
                            assertEquals(text.getInt("weight"),view.typeface.weight)
                            assertEquals(Color.parseColor(text.getString("color")),view.currentTextColor)
                            val baseline=text.getDouble("baseline") + if(textSource!=null)
                                text.getDouble("y")-n.getDouble("y") else 0.0
                            near("baseline",baseline*scale,view.baseline)
                        }
                    }
                    check("96:21546","96:21546")
                    check("96:21531","96:21531")
                    check("96:21543","96:21543")
                    check("96:21541","96:21541",false)
                    check("96:21528","96:21528",false)
                    check("96:21537","96:21537",textSource="96:21538")
                    check("96:21535","96:21535",textSource="96:21536")
                    check("96:21533","96:21533",textSource="96:21534")
                    check("96:21529","96:21529",textSource="96:21530")
                    check("96:21579","96:21579")
                    listOf("96:21547","96:21555","96:21563","96:21571").forEachIndexed { i,id ->
                        check("archive.card.$i",id)
                        val first=id.substringAfter(':').toInt()
                        check("archive.card.$i.title","96:${first+3}")
                        val title=root.findViewWithTag<TextView>("archive.card.$i.title")
                        val titlePaint=android.text.TextPaint(title.paint).apply {
                            textSize=32f; isSubpixelText=true; isLinearText=true
                            hinting=android.graphics.Paint.HINTING_OFF
                        }
                        assertTrue("Reference card name was unnecessarily ellipsized",
                            titlePaint.measureText(title.text.toString())*scale<=title.width+.01f)
                        check("archive.card.$i.date","96:${first+4}",false)
                        check("archive.card.$i.more","96:${first+5}")
                        val card=root.findViewWithTag<View>("archive.card.$i")
                        val image=bitmap(card)
                        val color=image.getPixel(image.width/2,image.height/4)
                        val sample=contract.getJSONArray("images").getJSONObject(i).getJSONArray("sample")
                        for((channel,actual) in listOf(Color.red(color),Color.green(color),Color.blue(color)).withIndex())
                            assertTrue("Wrong photo/crop for card $i: $color",abs(sample.getInt(channel)-actual)<30)
                        image.recycle()
                    }
                    assertEquals("최근 여행",root.findViewWithTag<TextView>("96:21528").text.toString())
                    assertTrue(root.findViewWithTag<TextView>("96:21541").text.contains("장면 3 / 6"))
                    capture(root,activity.filesDir,"archive-${width}x$height.png")
                    val scroll=root.findViewWithTag<HorizontalScrollView>("archive.scroll")
                    assertTrue("Fourth source card must be horizontally scrollable",scroll.canScrollHorizontally(1))
                    val max=scroll.getChildAt(0).width-scroll.width
                    scroll.scrollTo(max,0)
                    assertFalse("Last card remains unreachable",scroll.canScrollHorizontally(1))
                    val fourth=Rect().also {
                        val v=root.findViewWithTag<View>("archive.card.3")
                        v.getDrawingRect(it); root.offsetDescendantRectToMyCoords(v,it)
                    }
                    assertTrue(fourth.right<=width)
                    capture(root,activity.filesDir,"archive-${width}x$height-last.png")
                }
                val empty=ReferenceArchiveView(activity,emptyList())
                measure(empty,850,592)
                assertNotNull(empty.findViewWithTag<View>("archive.empty"))
                assertNull(empty.findViewWithTag<View>("96:21531"))
                assertNull(empty.findViewWithTag<View>("archive.card.0"))
                assertTrue(empty.findViewWithTag<View>("96:21529").isEnabled)
                capture(empty,activity.filesDir,"archive-empty.png")
            }
        }
    }
    private fun measure(v:View,w:Int,h:Int) { v.measure(exact(w),exact(h));v.layout(0,0,w,h) }
    private fun exact(n:Int)=View.MeasureSpec.makeMeasureSpec(n,View.MeasureSpec.EXACTLY)
    private fun bitmap(v:View)=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888).also {
        fun invalidateTree(n:View) { n.invalidate(); if(n is ViewGroup)
            for(i in 0 until n.childCount)invalidateTree(n.getChildAt(i)) }
        invalidateTree(v)
        v.draw(Canvas(it))
    }
    private fun capture(v:View,files:File,name:String) {
        val b=bitmap(v); val folder=File(files,"design-verification").apply { mkdirs() }
        File(folder,name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    }
}
