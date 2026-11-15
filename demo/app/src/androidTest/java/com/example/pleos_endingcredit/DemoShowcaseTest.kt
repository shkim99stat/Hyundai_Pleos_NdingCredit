package com.example.pleos_endingcredit

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoShowcaseTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun activeControl(view: View, tag: String): View? {
        if (!view.isShown || view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
            return null
        if (view.tag == tag) return view
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                activeControl(view.getChildAt(i), tag)?.let { return it }
            }
        }
        return null
    }

    private fun await(scenario:ActivityScenario<MainActivity>, tag:String, enabled:Boolean=false):View {
        val until=SystemClock.uptimeMillis()+15_000
        while(SystemClock.uptimeMillis()<until) {
            var found:View?=null
            scenario.onActivity { a-> found=activeControl(a.window.decorView, tag)?.takeIf { !enabled || it.isEnabled } }
            if(found!=null)return found!!
            SystemClock.sleep(80)
        }
        throw AssertionError("Visible control not found: $tag")
    }
    private fun click(scenario:ActivityScenario<MainActivity>,tag:String) {
        await(scenario,tag,enabled=true)
        scenario.onActivity { a->
            val view=checkNotNull(activeControl(a.window.decorView, tag))
            assertTrue("$tag enabled",view.isEnabled)
            val before=(view as? android.widget.CompoundButton)?.isChecked
            val handled=view.performClick()
            if(before!=null) assertEquals("$tag toggled",!before,(view as android.widget.CompoundButton).isChecked)
            else assertTrue("$tag handled click",handled)
        }
        SystemClock.sleep(350)
    }
    private fun openControls(activity: ActivityScenario<MainActivity>) {
        await(activity, "demo.toggle", enabled = true)
        activity.onActivity { a ->
            val decor = a.window.decorView
            if (!decor.findViewWithTag<View>("demo.panel").isShown) {
                decor.findViewWithTag<View>("demo.toggle").performClick()
            }
        }
    }
    private fun clickControl(activity: ActivityScenario<MainActivity>, tag: String) {
        openControls(activity)
        click(activity, tag)
    }
    private fun arrive(activity:ActivityScenario<MainActivity>) {
        await(activity,"demo.console")
        activity.onActivity { a ->
            val prefs=a.getSharedPreferences("showcase_playback",0)
            val scene=prefs.getInt("scene",0)
            if(prefs.getLong("elapsed",0)<DemoScenario.legs[scene].playbackMillis) {
                val decor = a.window.decorView
                if (!decor.findViewWithTag<View>("demo.panel").isShown) {
                    decor.findViewWithTag<View>("demo.toggle").performClick()
                }
                val next=decor.findViewWithTag<View>("demo.next")
                assertTrue(next.isEnabled)
                next.performClick()
            }
        }
        SystemClock.sleep(350)
    }
    private fun completeEntry(activity:ActivityScenario<MainActivity>) {
        await(activity,"a0.accept")
        click(activity,"a0.locationConsent")
        click(activity,"a0.drivingConsent")
        click(activity,"a0.accept")

    }
    private fun screenshot(name:String) {
        SystemClock.sleep(600) // Capture after the page crossfade has completed.
        val target=instrumentation.targetContext
        val image=instrumentation.uiAutomation.takeScreenshot()
        val folder=File(target.filesDir,"demo-qa").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
        image.recycle()
    }
    @Test fun completedTripHasReadableCreditRouteStopsAndResult() {
        val base=DemoScenario.snapshot(DemoScenario.CURRENT_PREFIX + "visual-qa",DemoScenario.legs.lastIndex,1.0)
        val record=base.copy(endedAt=base.latest!!.at,stops=base.stops.mapIndexed{i,stop->
            stop.copy(userLabel=DemoScenario.legs[i].venues.first().name)
        })
        val places=record.visibleStops.map{stop->ResultPlace(stop.id,stop.userLabel!!,
            RecordingCandidatesPanel.time(stop.at),DemoScenario.category(stop.userLabel))}
        val trip=Trip(id=record.tripId,name=DemoScenario.TITLE,people=4,
            createdOn=DemoScenario.DATE,resultPlaces=places)
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            completeEntry(activity)
            await(activity,"home.start")
            activity.onActivity { a ->
                val credit = B3CreditView(a, CreditContent(trip, record), 1, 4_000)
                a.setContentView(credit)
                assertEquals("000504", credit.findViewWithTag<TextView>("b3.odometer").text.toString())
            }
            screenshot("09-credit-route")
            activity.onActivity { a ->
                val credit = B3CreditView(a, CreditContent(trip, record), 2, 1_000)
                a.setContentView(credit)
                assertEquals("504.4km를 달려왔어요", credit.findViewWithTag<TextView>("b3.routeCaption").text.toString())
            }
            screenshot("10-credit-summary")
            activity.onActivity { a ->
                val credit = B3CreditView(a, CreditContent(trip, record), 5, 1_500)
                a.setContentView(credit)
                val roll = credit.findViewWithTag<View>("b3.roll").contentDescription.toString()
                assertTrue(places.all { it.name in roll })
                assertTrue("주행 시간 7시간 16분" in roll)
                assertTrue("밤길 121km" in roll)
                assertTrue("4명" in roll)
            }
            screenshot("14-credit-film-bridge")
            activity.onActivity { a ->
                a.window.decorView.findViewWithTag<B3CreditView>("b3.credit").seekTo(4_080)
            }
            screenshot("15-credit-film-narrow")
            activity.onActivity { a ->
                a.window.decorView.findViewWithTag<B3CreditView>("b3.credit").seekTo(8_900)
            }
            screenshot("16-credit-film-cat")
            activity.onActivity { a->a.setContentView(C1TripResultView(a,TripResultContent(trip,record),{},{},{},{},{})) }
            SystemClock.sleep(4500)
            screenshot("11-result-stable")
        }
    }
    @Test fun everyEntryShowsA0EvenAfterConsentWasSaved() {
        val context=instrumentation.targetContext
        context.getSharedPreferences("ending_credit_mock",0).edit().clear().commit()
        TripStore(context).hasConsent=true
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            await(activity,"a0.accept")
            activity.onActivity { a ->
                assertFalse(a.window.decorView.findViewWithTag<android.widget.CheckBox>("a0.locationConsent").isChecked)
                assertFalse(a.window.decorView.findViewWithTag<View>("a0.accept").isEnabled)
            }
            screenshot("12-a0-entry")
            completeEntry(activity);await(activity,"home.start")
            activity.onActivity { a ->
                a.startActivity(Intent.makeMainActivity(ComponentName(a,MainActivity::class.java)))
            }
            completeEntry(activity);await(activity,"home.start")
            activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            completeEntry(activity);await(activity,"home.start")
        }
    }
    @Test fun endDemoSkipsUnconfirmedStopsAndOpensCredit() {
        val context = instrumentation.targetContext
        context.getSharedPreferences("ending_credit_mock", 0).edit().clear().commit()
        context.getSharedPreferences("showcase_playback", 0).edit().clear().commit()
        val store = TripStore(context)
        val repository = TripRecordingRepository.get(context)
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            completeEntry(activity)
            click(activity, "home.start")
            click(activity, "crew.next")
            click(activity, "a3.begin")
            arrive(activity)
            val id = requireNotNull(store.activeTrip).id
            val savedLabel = "시연에서 수정한 시장"
            val market = requireNotNull(repository.load(id)).visibleStops.single()
            repository.editStop(id, market.id) { it.copy(userLabel = savedLabel) }
            clickControl(activity, "demo.next")
            arrive(activity)
            assertNull(requireNotNull(repository.load(id)).visibleStops.last().userLabel)

            clickControl(activity, "demo.end")
            await(activity, "b1.skip")
            assertFalse(await(activity, "b1.make").isEnabled)
            screenshot("17-b1-waiting")
            clickControl(activity, "demo.video.insert")
            assertEquals("영상을 받았어요", (await(activity, "b1.status") as TextView).text.toString())
            screenshot("18-b1-received")
            click(activity, "b1.make")
            activity.onActivity { a ->
                assertNotNull(a.window.decorView.findViewWithTag<View>("b2.preparing"))
            }
            await(activity, "b3.credit")
            val record = requireNotNull(repository.load(id))
            assertEquals(DemoScenario.at("15T19:07"), record.endedAt)
            assertEquals(504_400.0, record.distanceMeters, .01)
            assertEquals(120_600.0, requireNotNull(record.nightDistanceMeters), .01)
            assertEquals(8, record.visibleStops.size)
            assertEquals(savedLabel, record.visibleStops[0].userLabel)
            assertEquals(DemoScenario.legs[1].venues.first().name, record.visibleStops[1].userLabel)
            assertEquals(DemoScenario.legs[6].venues.first().name, record.visibleStops[6].userLabel)
            assertTrue(record.visibleStops.all { it.userLabel != null && it.resolutionAttempted })
            assertNull(store.activeTrip)
            assertEquals(8, store.trips.first { it.id == id }.resultPlaces?.size)

            click(activity, "b3.next")
            await(activity, "c1.result")
        }
    }

    @Test fun completeTwoDayTripWithCheckpointChoicesAndCredit() {
        val context=instrumentation.targetContext
        context.getSharedPreferences("ending_credit_mock",0).edit().clear().commit()
        context.getSharedPreferences("showcase_playback",0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            completeEntry(activity)
            click(activity,"home.start")
            click(activity, "crew.next")
            click(activity,"a3.begin")
            await(activity,"demo.toggle")
            activity.onActivity { a ->
                val decor = a.window.decorView
                val button = decor.findViewWithTag<View>("demo.toggle")
                val panel = decor.findViewWithTag<View>("demo.panel")
                val controls = decor.findViewWithTag<View>("demo.console")
                val recordView = decor.findViewWithTag<View>("a4.recording")
                assertFalse(panel.isShown)
                assertEquals(button.width, controls.width)
                assertEquals(button.height, controls.height)
                assertTrue(kotlin.math.abs((48 * a.resources.displayMetrics.density).toInt() - button.width) <= 1)
                val buttonLocation = IntArray(2).also(button::getLocationInWindow)
                val recordLocation = IntArray(2).also(recordView::getLocationInWindow)
                assertTrue(buttonLocation[0] - recordLocation[0] < recordView.width / 4)
                assertTrue(buttonLocation[1] - recordLocation[1] > recordView.height * 3 / 4)
            }
            screenshot("01-driving")
            clickControl(activity,"demo.pause")
            val prefs=context.getSharedPreferences("showcase_playback",0)
            val saved=prefs.getLong("elapsed",-1)
            SystemClock.sleep(600)
            assertEquals(saved,prefs.getLong("elapsed",-1))
            clickControl(activity,"demo.pause")
            for(i in 0..7) {
                arrive(activity)
                val trip=TripStore(context).activeTrip!!
                val arrived=TripRecordingRepository.get(context).load(trip.id)!!.visibleStops.last()
                if(i==1 || i==6) {
                    assertNull(arrived.userLabel)
                    clickControl(activity,"demo.review")
                    await(activity,"a4.candidates")
                    if(i==1)screenshot("03-manual-candidates")
                    click(activity,"a4.candidates.confirm")
                    if(i==1) {
                        clickControl(activity,"demo.review")
                        click(activity,"a4.candidates.row.1")
                        click(activity,"a4.candidates.confirm")
                        assertEquals(DemoScenario.legs[i].venues[1].name,
                            TripRecordingRepository.get(context).load(trip.id)!!.visibleStops.last().userLabel)
                        clickControl(activity,"demo.review")
                        click(activity,"a4.candidates.row.0")
                        click(activity,"a4.candidates.confirm")
                    }
                } else {
                    assertNotNull(arrived.userLabel)
                    assertTrue(DemoScenario.automaticallyResolved(trip.id,arrived))
                    assertEquals(1,DemoScenario.candidates(trip.id,arrived.id).size)
                    if(i==0)screenshot("02-automatic-checkpoint")
                }
                val stop=TripRecordingRepository.get(context).load(trip.id)!!.visibleStops.last()
                assertEquals(DemoScenario.legs[i].venues.first().name,stop.userLabel)
                if(i==3) {
                    activity.recreate()
                    completeEntry(activity)
                    await(activity,"demo.console")
                    assertEquals(3,prefs.getInt("scene",-1))
                }
                if(i==4) {
                    openControls(activity)
                    assertTrue((await(activity,"demo.status") as TextView).text.toString().startsWith("2일차"))
                    screenshot("04-day-two")
                }
                clickControl(activity,"demo.next")
            }
            openControls(activity)
            assertTrue((await(activity,"demo.status") as TextView).text.toString().contains("야간"))
            screenshot("13-night-driving")
            arrive(activity)
            screenshot("05-return-seoul")
            val id=TripStore(context).activeTrip!!.id
            clickControl(activity,"demo.review")
            click(activity,"a4.finish.confirm")
            click(activity,"b1.skip")
            await(activity,"b3.credit")
            val record=TripRecordingRepository.get(context).load(id)!!
            assertEquals(DemoScenario.at("15T19:07"),record.endedAt)
            assertEquals(8,record.visibleStops.size)
            assertTrue(record.visibleStops.all{it.userLabel!=null})
            assertEquals(6,record.visibleStops.count{DemoScenario.automaticallyResolved(id,it)})
            assertEquals(504_400.0,record.distanceMeters,.01)
            assertEquals(120_600.0,record.nightDistanceMeters!!,.01)
            assertEquals(104L*60_000,record.nightMovingMillis)
            val trip=TripStore(context).trips.first{it.id==id}
            assertEquals(8,trip.resultPlaces!!.size)
            assertEquals("market",trip.resultPlaces!![3].category)
            assertEquals("121km",TripResultContent(trip,record).night)
            assertEquals(listOf("주행 시간 7시간 16분", "밤길 121km"), CreditContent(trip,record).moments)
            click(activity, "b3.next")
            await(activity,"c1.result")
            screenshot("07-result")
            val savedJson=org.json.JSONObject(File(context.filesDir,"trip-records/$id.json").readText())
            assertEquals(120_600.0,savedJson.getDouble("nightDistance"),.01)
            assertEquals(104L*60_000,savedJson.getLong("nightMillis"))
            activity.onActivity { a ->
                val fallback=KakaoTripMapView(a){_,_->}.apply{update(record,emptyMap(),true)}
                a.setContentView(fallback)
                assertNotNull(fallback.findViewWithTag<View>("demo.map.fallback"))
            }
            screenshot("08-offline-preview")
        }
    }
}
