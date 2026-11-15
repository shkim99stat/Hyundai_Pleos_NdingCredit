package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class A4RecordingUiTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrument.targetContext
    private val repository get()=TripRecordingRepository.get(context)
    private val options=(1..3).map{PlaceCandidate("test-place-$it","후보 가게 $it","카페",it*50.0,address="서울특별시 시험로 $it")}
    private fun measure(view:View,w:Int=1700,h:Int=1184){AppTypography.applyTo(view);view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)}
    private fun click(view:View,tag:String){val child=requireNotNull(view.findViewWithTag<View>(tag)){tag};assertTrue("$tag disabled",child.isEnabled);assertTrue(tag,child.performClick())}
    private fun render(name:String,view:View){
        measure(view);val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val dir=File(context.filesDir,"a4-revision-verification").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun record(id:String):TripRecording {
        val now=System.currentTimeMillis();val point=TripPoint(37.5665,126.9780,now-12_000_000)
        return TripRecording(id,point.at,points=listOf(point),latest=point,departed=true,distanceMeters=184_000.0,movingMillis=11_400_000,
            stops=listOf(RecordedStop("known",now-9_000_000,point,userLabel="옥계휴게소",departedAt=now-8_000_000),
                RecordedStop("first",now-7_000_000,point,departedAt=now-5_200_000),RecordedStop("second",now-2_000_000,point,departedAt=now-800_000)))
    }
    @Test fun summaryAndPlacesTabsFitWindowsAndShareTheMap(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
            val trip=Trip(name="우리의 강릉 여행",people=4)
            val source = record(trip.id)
            val data = source.copy(stops = listOf(source.stops.first().copy(
                userLabel = null, selectedPlaceId = options.first().id)) + source.stops.drop(1))
            val root=A4RecordingView(activity,trip,{true},{},{},{},data);root.refresh()
            root.acceptCandidates("known", PlaceDecision(options, null))
            val output=File(context.filesDir,"a4-tabs-verification").apply{mkdirs()}
            val map = root.findViewWithTag<View>("a4.map")
            assertEquals("4명", root.findViewWithTag<TextView>("a4.passengers").text.toString())
            assertEquals("—", root.findViewWithTag<TextView>("a4.efficiency").text.toString())
            for((w,h)in listOf(1700 to 1184,850 to 592,720 to 1000)){
                val scale=min(w/1700f,h/1184f);val ox=(w-1700*scale)/2;val oy=(h-1184*scale)/2
                for ((tab, expected) in listOf(
                    "summary" to mapOf("a4.summary" to floatArrayOf(863f,160.29f,774f,709f),
                        "a4.end" to floatArrayOf(868f,1025.21f,774f,96f)),
                    "places" to mapOf("a4.review.banner" to floatArrayOf(1309f,924.43f,329f,64f),
                        "a4.stops.viewport" to floatArrayOf(860f,252f,819f,752f),
                        "a4.end" to floatArrayOf(868f,1025.21f,774f,96f)),
                )) {
                    click(root, "a4.tab.$tab")
                    measure(root,w,h)
                    assertSame(map, root.findViewWithTag<View>("a4.map"))
                    for ((tag, bounds) in expected) {
                        val view=root.findViewWithTag<View>(tag)
                        assertEquals(tag, View.VISIBLE, view.visibility)
                        assertEquals(tag,ox+bounds[0]*scale,view.left.toFloat(),1.1f)
                        assertEquals(tag,oy+bounds[1]*scale,view.top.toFloat(),1.1f)
                        assertEquals(tag,bounds[2]*scale,view.width.toFloat(),1.1f)
                        assertEquals(tag,bounds[3]*scale,view.height.toFloat(),1.1f)
                    }
                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
                    File(output,"a4-$tab-${w}x$h.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                    bitmap.recycle()
                }
                val text=root.findViewWithTag<TextView>("a4.review.count")
                assertEquals("확인이 필요한 곳 2곳",text.text.toString());assertEquals(24*scale,text.textSize,.1f)
                val address = root.findViewWithTag<TextView>("a4.stop.address.0")
                assertEquals(options.first().address, address.text.toString())
                assertEquals(255, android.graphics.Color.alpha(address.currentTextColor))
            }
            click(root,"a4.end")
            assertNotNull(root.findViewWithTag<View>("a4.finish.confirm"))
            click(root,"a4.finish.cancel")
            click(root,"a4.review.banner");assertEquals("1 / 2",root.findViewWithTag<TextView>("a4.candidates.progress").text.toString());assertTrue(root.handleBack())
            click(root,"a4.edit");click(root,"a4.edit.cancel");measure(root)
            assertEquals(1309,root.findViewWithTag<View>("a4.review.banner").left)
            val manyData=data.copy(stops=(0 until 100).map{RecordedStop("stop-$it",data.startedAt+it,null)})
            val many=A4RecordingView(activity,trip,{true},{},{},{},manyData);many.refresh()
            click(many,"a4.tab.places");measure(many,850,592)
            val count=many.findViewWithTag<TextView>("a4.review.count")
            assertEquals("확인이 필요한 곳 100곳",count.text.toString());assertTrue(count.paint.measureText(count.text.toString())<=count.width+1f)
            val empty=A4RecordingView(activity,trip,{true},{},{},{},data.copy(stops=emptyList()));empty.refresh()
            click(empty,"a4.tab.places");measure(empty)
            assertEquals(View.INVISIBLE,empty.findViewWithTag<View>("a4.review.banner").visibility)
            assertTrue(empty.findViewWithTag<TextView>("a4.empty").text.toString().startsWith("차를 세우고(P)"))
        }}
    }
    @Test fun updatedA4FamilyRendersAtSourceHalfAndPortraitSizes(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
            val trip=Trip(name="우리의 강릉 여행",people=4);var allowed=true;var ended=0
            val root=A4RecordingView(activity,trip,{allowed},{},{ended++},{},record(trip.id))
            root.refresh();root.acceptCandidates("first",PlaceDecision(options,null));root.acceptCandidates("second",PlaceDecision(options,null))
            for((w,h)in listOf(1700 to 1184,850 to 592,720 to 1000)){
                measure(root,w,h);val scale=min(w/1700f,h/1184f);val ox=(w-1700*scale)/2;val oy=(h-1184*scale)/2
                for((tag,bounds)in mapOf("a4.map" to floatArrayOf(2f,2f,828f,1180f),"a4.end" to floatArrayOf(868f,1025.21f,774f,96f))){
                    val v=root.findViewWithTag<View>(tag);assertEquals(ox+bounds[0]*scale,v.left.toFloat(),1.1f);assertEquals(oy+bounds[1]*scale,v.top.toFloat(),1.1f)
                    assertEquals(bounds[2]*scale,v.width.toFloat(),1.1f);assertEquals(bounds[3]*scale,v.height.toFloat(),1.1f)
                }
                assertEquals("184km",root.findViewWithTag<TextView>("a4.distance").text.toString())
                assertEquals("3시간 10분",root.findViewWithTag<TextView>("a4.duration").text.toString())
                assertEquals("우리의 강릉 여행",root.findViewWithTag<TextView>("a4.title").text.toString())
            }
            render("a4",root);click(root,"a4.tab.places");click(root,"a4.review.banner");render("a4-1",root)
            assertEquals("1 / 2",root.findViewWithTag<TextView>("a4.candidates.progress").text.toString())
            assertEquals("여기서 30분 있었어요",root.findViewWithTag<TextView>("a4.candidates.duration").text.toString())
            click(root,"a4.candidates.manual");click(root,"a4.candidates.confirm");render("a4-1-1",root)
            root.findViewWithTag<EditText>("a4.manual.input").setText("가".repeat(30));assertEquals(20,root.findViewWithTag<EditText>("a4.manual.input").length())
            click(root,"a4.manual.cancel");assertTrue(root.handleBack());click(root,"a4.edit");render("a4-2",root)
            click(root,"a4.edit.cancel");click(root,"a4.tab.summary");click(root,"a4.end");render("a4-3",root);assertEquals(0,ended)
            click(root,"a4.finish.cancel");click(root,"a4.end");click(root,"a4.finish.confirm");assertEquals(1,ended)
            click(root,"a4.tab.places");click(root,"a4.review.banner");allowed=false;root.refresh();assertNull(root.findViewWithTag<View>("a4.overlay"));assertFalse(root.findViewWithTag<View>("a4.end").isEnabled)
            val empty=A4RecordingView(activity,Trip(name="빈 여행",people=2),{true},{},{},{},TripRecording("empty",1L));empty.refresh();measure(empty)
            assertEquals("차를 세우고(P) 문을 열면 여기에 기록돼요",empty.findViewWithTag<TextView>("a4.empty").text.toString())
            assertEquals(View.INVISIBLE,empty.findViewWithTag<View>("a4.review.banner").visibility)
            val exports=setOf("27fad.svg","b6899.svg","72f5c.svg","fea53.svg","54976.svg","2dc4c.svg","a8c99.svg","a22e6.svg","cb3f7.svg","880ee.svg","45823.svg","5910b.svg","f67cf.svg")
            assertEquals(exports,activity.assets.list("a4")!!.toSet());exports.forEach{activity.assets.open("a4/$it").use{stream->assertTrue(stream.available()>0)}}
            render("b2-skip",B2CreditPreparingView(activity,CreditContent(trip,null),{}))
            for(frame in listOf(0,1,4,5))render("b3-skip-$frame",B3CreditView(activity,CreditContent(trip,record(trip.id)),frame,1000,false))
        }}
    }
    @Test fun reviewQueueManualEntryAndDraftRemovalPreserveRecordedRoute(){
        val trip=Trip(name="A4 편집 검증",people=2);repository.sensor(record(trip.id),true)
        try{ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
            val root=A4RecordingView(activity,trip,{true},{},{},{})
            root.refresh();root.acceptCandidates("first",PlaceDecision(options,null));root.acceptCandidates("second",PlaceDecision(options,null));measure(root)
            click(root,"a4.tab.places");click(root,"a4.review.banner");click(root,"a4.candidates.row.1")
            assertNull(repository.load(trip.id)!!.stops[1].selectedPlaceId)
            click(root,"a4.candidates.confirm");assertEquals("test-place-2",repository.load(trip.id)!!.stops[1].selectedPlaceId)
            assertNull(repository.load(trip.id)!!.stops[1].userLabel)
            assertEquals("후보 가게 2",CreditContent(trip,repository.load(trip.id)).stops[1].name)
            assertFalse(TripResultContent(trip,repository.load(trip.id)).places[1].needsReview)
            assertEquals("후보 가게 2",TripResultContent(trip,repository.load(trip.id)).places[1].name)
            assertEquals("2 / 2",root.findViewWithTag<TextView>("a4.candidates.progress").text.toString())
            click(root,"a4.candidates.manual");click(root,"a4.candidates.confirm")
            root.findViewWithTag<EditText>("a4.manual.input").setText("직접 기억한 가게");click(root,"a4.manual.save")
            assertNull(root.findViewWithTag<View>("a4.overlay"));assertEquals("직접 기억한 가게",repository.load(trip.id)!!.stops[2].userLabel)
            click(root,"a4.edit");click(root,"a4.remove.known");assertEquals(3,repository.load(trip.id)!!.visibleStops.size)
            click(root,"a4.edit.cancel");assertEquals(3,repository.load(trip.id)!!.visibleStops.size)
            click(root,"a4.edit");click(root,"a4.remove.known");click(root,"a4.edit.save")
            val saved=repository.load(trip.id)!!;assertEquals(2,saved.visibleStops.size);assertEquals(184_000.0,saved.distanceMeters,0.0);assertEquals(record(trip.id).points.size,saved.points.size)
            assertNotNull(saved.stops[1].departedAt)
        }}}finally{repository.delete(trip.id)}
    }
    @Test fun laterDoesNotChangePlaceAndNoCandidatesStillAllowsManualEntry(){
        val trip=Trip(name="A4 나중에 검증",people=2);repository.sensor(record(trip.id),true)
        try{ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
            val root=A4RecordingView(activity,trip,{true},{},{},{})
            root.refresh();measure(root);click(root,"a4.tab.places");click(root,"a4.review.banner");assertFalse(root.findViewWithTag<View>("a4.candidates.confirm").isEnabled)
            click(root,"a4.candidates.later");assertEquals("2 / 2",root.findViewWithTag<TextView>("a4.candidates.progress").text.toString());click(root,"a4.candidates.later")
            assertNull(root.findViewWithTag<View>("a4.overlay"));assertTrue(repository.load(trip.id)!!.stops.drop(1).all{it.selectedPlaceId==null&&it.userLabel==null})
            click(root,"a4.review.banner");click(root,"a4.candidates.manual");click(root,"a4.candidates.confirm");assertNotNull(root.findViewWithTag<View>("a4.manual.input"))
            click(root,"a4.manual.cancel");assertNotNull(root.findViewWithTag<View>("a4.candidates"));assertTrue(root.handleBack())
        }}}finally{repository.delete(trip.id)}
    }
    @Test fun finishAndBothCreditSkipActionsReachResultWithoutLeavingResume(){
        val prefs=context.getSharedPreferences("ending_credit_mock",Context.MODE_PRIVATE);val original=prefs.all.toMap();val trip=Trip(name="A4 종료 검증",people=2)
        TripStore(context).activeTrip=trip;repository.sensor(record(trip.id).copy(pending=listOf(RecordedStop("endpoint",System.currentTimeMillis(),null))),true)
        try{ActivityScenario.launch(MainActivity::class.java).use{scenario->
            await(scenario){it.window.decorView.findViewWithTag<View>("a4.end")?.isShown==true}
            scenario.onActivity{a->
                val root=a.window.decorView.findViewWithTag<A4RecordingView>("a4.recording")
                root.acceptCandidates("first",PlaceDecision(options,null));click(root,"a4.tab.places");click(root,"a4.review.banner");click(root,"a4.candidates.manual");click(root,"a4.candidates.confirm")
            }
            await(scenario){it.window.decorView.findViewWithTag<EditText>("a4.manual.input")?.isShown==true}
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withTagValue(org.hamcrest.Matchers.equalTo<Any>("a4.manual.input")))
                .perform(androidx.test.espresso.action.ViewActions.replaceText("실제 입력 검증"))
            scenario.onActivity{a->
                assertEquals("실제 입력 검증",a.window.decorView.findViewWithTag<EditText>("a4.manual.input").text.toString())
                click(a.window.decorView,"a4.manual.cancel");a.window.decorView.findViewWithTag<A4RecordingView>("a4.recording").handleBack()
            }
            scenario.onActivity{a->
                a.onBackPressedDispatcher.onBackPressed();assertNotNull(TripStore(context).activeTrip)
                click(a.window.decorView,"a4.end");click(a.window.decorView,"a4.finish.cancel");assertTrue(repository.load(trip.id)!!.active)
                click(a.window.decorView,"a4.end");click(a.window.decorView,"a4.finish.confirm")
                assertFalse(repository.load(trip.id)!!.active);assertTrue(repository.load(trip.id)!!.pending.isEmpty());assertNull(TripStore(context).activeTrip)
                click(a.window.decorView,"b1.skip")
                assertTrue(a.window.decorView.findViewWithTag<TextView>("b2.description").text.toString().contains("184km"))
                click(a.window.decorView,"b2.skip")
            }
            await(scenario){it.window.decorView.findViewWithTag<View>("c1.result")?.isShown==true};assertNull(TripStore(context).resume)
            scenario.onActivity{a->
                TripStore(context).resume=CreditResume(trip.id,0,0)
                MainActivity::class.java.getDeclaredField("page").apply{isAccessible=true}.set(a,Page.B3_CREDIT)
                MainActivity::class.java.getDeclaredMethod("render").apply{isAccessible=true}.invoke(a)
            }

            await(scenario){it.window.decorView.findViewWithTag<B3CreditView>("b3.credit")?.frame==5}
            scenario.onActivity{a->click(a.window.decorView,"b3.next")}
            await(scenario){it.window.decorView.findViewWithTag<View>("c1.result")?.isShown==true};assertNull(TripStore(context).resume)
            scenario.onActivity{TripRecordingService.stop(it)}
        }}finally{
            prefs.edit().clear().apply{original.forEach{(k,v)->when(v){is String->putString(k,v);is Boolean->putBoolean(k,v);is Int->putInt(k,v);is Long->putLong(k,v);is Float->putFloat(k,v)}}}.commit();repository.delete(trip.id)
        }
    }
    private fun await(scenario:ActivityScenario<MainActivity>,predicate:(MainActivity)->Boolean){val deadline=System.currentTimeMillis()+12_000;while(System.currentTimeMillis()<deadline){var ready=false;scenario.onActivity{ready=predicate(it)};if(ready)return;Thread.sleep(100)};scenario.onActivity{assertTrue("A4 transition timed out",predicate(it))}}
}
