package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

/** Opt-in network check: -e verifyKakao true. Only public, predefined sample
 * coordinates are transmitted. Existing records and preferences are restored;
 * temporary test records are deleted. Provider responses are not persisted.
 */
@RunWith(AndroidJUnit4::class)
class KakaoMapIntegrationTest {
    @Test fun c1LooksUpRoadAddressForRecordedStopWithoutAPlaceId() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyKakao") == "true")
        val trip = Trip(id = "public-c1-city-hall", name = "서울시청", people = 2)
        val at = System.currentTimeMillis()
        val fix = TripPoint(37.5662952, 126.9779451, at)
        val stop = RecordedStop("public-city-hall-stop", at, fix, userLabel = "서울시청")
        val record = TripRecording(trip.id, at - 1000, endedAt = at + 1000,
            points = listOf(fix), latest = fix, stops = listOf(stop))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var surface: C1TripResultView
            scenario.onActivity { activity ->
                surface = C1TripResultView(activity, TripResultContent(trip, record), {}, {}, {}, {})
                (activity.window.decorView as ViewGroup).addView(surface, ViewGroup.LayoutParams(-1, -1))
            }
            try {
                val deadline = System.currentTimeMillis() + 35_000
                var address = ""
                do {
                    scenario.onActivity {
                        address = surface.findViewWithTag<TextView>("c1.place.address.0").text.toString()
                    }
                    if (address.isNotBlank()) break
                    Thread.sleep(250)
                } while (System.currentTimeMillis() < deadline)
                assertTrue("Road address lookup returned: $address", address.contains("세종대로"))
            } finally {
                scenario.onActivity { (surface.parent as? ViewGroup)?.removeView(surface) }
            }
        }
    }

    @Test fun liveSdkLoadsTilesRendersRouteAndSearchesNearbyPlaces() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyKakao")=="true")
        assumeTrue(BuildConfig.KAKAO_MAP_JS_KEY.isNotBlank())
        val received=CountDownLatch(1)
        val decision=AtomicReference<PlaceDecision>()
        val console=mutableListOf<String>()
        val now=System.currentTimeMillis()
        // Around the public Pangyo sample coordinate in Kakao's Web guide.
        val points=listOf(
            TripPoint(37.4018,127.1078,now-30_000),
            TripPoint(37.402056,127.108212,now-20_000),
            TripPoint(37.4025,127.1087,now-10_000,gapBefore=true),
            TripPoint(37.4028,127.1090,now),
        )
        val stop=RecordedStop("kakao-integration-public-stop",now-20_000,points[1],userLabel="지도 연결 확인")
        val record=TripRecording("kakao-integration-public-route",points.first().at,points=points,
            latest=points.last(),stops=listOf(stop),departed=true)
        lateinit var probe:KakaoTripMapView
        lateinit var web:WebView
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                probe=KakaoTripMapView(activity) { id,result ->
                    if(id==stop.id) { decision.set(result); received.countDown() }
                }
                (activity.window.decorView as ViewGroup).addView(probe,ViewGroup.LayoutParams(774,896))
                probe.update(record,emptyMap(),false)
                probe.connect()
                web=(0 until probe.childCount).map(probe::getChildAt).filterIsInstance<WebView>().single()
                web.webChromeClient=object:WebChromeClient() {
                    override fun onConsoleMessage(message:ConsoleMessage):Boolean {
                        if(message.messageLevel()==ConsoleMessage.MessageLevel.ERROR)
                            console+=message.message().replace(Regex("[A-Fa-f0-9]{32}"),"<redacted>").take(180)
                        return true
                    }
                }
            }
            var state=JSONObject()
            val deadline=System.currentTimeMillis()+35_000
            while(System.currentTimeMillis()<deadline) {
                state=inspect(scenario,web)
                var ready=false
                scenario.onActivity {
                    ready=probe.findViewWithTag<TextView>("a4.map.status").visibility==View.GONE
                }
                if(ready&&state.optInt("roadFeatures")>0&&state.optInt("routeLines")==2)break
                Thread.sleep(250)
            }
            assertTrue("Map failed to initialize: $state",state.optBoolean("ready"))
            assertTrue("Roads were not rendered: $state",state.optInt("roadFeatures")>0)
            assertEquals("The basemap must have no labels or POI symbols",0,state.getInt("symbolLayers"))
            assertEquals("Missing interval must not be connected by an invented route",2,state.optInt("routeLines"))
            assertFalse(state.getBoolean("draggable"));assertFalse(state.getBoolean("zoomable"))
            scenario.onActivity { probe.update(record,emptyMap(),true);probe.search(stop) }
            assertTrue("Place search callback timed out",received.await(90,TimeUnit.SECONDS))
            val result=requireNotNull(decision.get())
            assertTrue("No real nearby candidates were returned",result.candidates.isNotEmpty())
            assertTrue(result.candidates.size<=3)
            assertEquals(result.candidates.size,result.candidates.distinctBy{it.id}.size)
            assertTrue(result.candidates.all{it.name.isNotBlank()&&it.distanceMeters in 0.0..200.0})
            state=inspect(scenario,web)
            assertTrue(state.getBoolean("draggable"));assertTrue(state.getBoolean("zoomable"))
            scenario.onActivity {
                assertEquals("Map error overlay became visible",View.GONE,probe.findViewWithTag<TextView>("a4.map.status").visibility)
                assertTrue("WebView errors: $console",console.isEmpty())
            }
            capture(scenario,probe)
        } finally {
            scenario.onActivity { (probe.parent as? ViewGroup)?.removeView(probe);probe.close() }
            scenario.close()
        }
    }

    @Test fun emptyA4AutomaticallyShowsSeoulAndRecentersOnFirstFix() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyKakao")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repository=TripRecordingRepository.get(context)
        val legacy=context.getSharedPreferences("trip_map_preferences",Context.MODE_PRIVATE)
        val previous=legacy.all["external_map_consent"] as? Boolean
        // A previous refusal of the removed prompt must not block the map.
        legacy.edit().putBoolean("external_map_consent",false).commit()
        val trip=Trip(id="map-default-${UUID.randomUUID()}",name="우리의 강릉 여행",people=2)
        repository.ensure(trip.id)
        lateinit var surface:A4RecordingView
        lateinit var probe:KakaoTripMapView
        lateinit var web:WebView
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                surface=A4RecordingView(activity,trip,{true},{},{},{})
                (activity.window.decorView as ViewGroup).addView(surface,ViewGroup.LayoutParams(850,592))
                probe=surface.findViewWithTag("a4.map")
                web=(0 until probe.childCount).map(probe::getChildAt).filterIsInstance<WebView>().single()
            }
            var state=JSONObject()
            val deadline=System.currentTimeMillis()+35_000
            while(System.currentTimeMillis()<deadline) {
                state=inspect(scenario,web)
                if(state.optBoolean("ready")&&state.optInt("roadFeatures")>0&&state.optBoolean("defaultLocation"))break
                Thread.sleep(250)
            }
            assertTrue("Automatic map startup failed: $state",state.optBoolean("ready"))
            assertTrue("Seoul roads did not render: $state",state.optInt("roadFeatures")>0)
            assertTrue(state.getBoolean("defaultLocation"))
            // Check rendered-centre proximity while keeping recorded fixes exact.
            assertTrue("Map did not center on Seoul",metersBetween(
                TripPoint(37.5665,126.9780,System.currentTimeMillis()),
                TripPoint(state.getDouble("latitude"),state.getDouble("longitude"),System.currentTimeMillis()))<5.0)
            assertTrue(state.getJSONArray("labels").toString().contains("기본 위치 · 서울"))
            assertFalse("Default map center must not get a current-position dot",state.getBoolean("positionArtLoaded"))
            assertTrue("Recording title backdrop is missing",state.getBoolean("headerBackdropVisible"))
            assertTrue("WebView did not apply backdrop blur",state.getString("headerBackdropFilter").contains("blur("))
            assertEquals("none",state.getString("headerBackdropPointerEvents"))
            val empty=repository.load(trip.id)!!
            assertNull(empty.latest);assertTrue(empty.points.isEmpty())
            assertTrue(empty.stops.isEmpty());assertEquals(0.0,empty.distanceMeters,0.0)
            capture(scenario,surface,"a4-seoul-default.png")
            scenario.onActivity{probe.headerBackdropEnabled=false;probe.update(empty,emptyMap(),true);web.invalidate()}
            Thread.sleep(600);assertFalse(inspect(scenario,web).getBoolean("headerBackdropVisible"));capture(scenario,surface,"a4-header-before.png")
            scenario.onActivity{probe.headerBackdropEnabled=true;probe.update(empty,emptyMap(),true);web.invalidate()}
            Thread.sleep(600);assertTrue(inspect(scenario,web).getBoolean("headerBackdropVisible"));capture(scenario,surface,"a4-header-blur.png")

            val fix=TripPoint(37.402056,127.108212,System.currentTimeMillis())
            repository.sensor(empty.copy(latest=fix,points=listOf(fix)),true)
            val fixDeadline=System.currentTimeMillis()+10_000
            while(System.currentTimeMillis()<fixDeadline) {
                scenario.onActivity { surface.refresh() }
                state=inspect(scenario,web)
                if(state.optBoolean("positionArtLoaded")&&kotlin.math.abs(state.optDouble("latitude")-fix.latitude)<.00001)break
                Thread.sleep(250)
            }
            assertFalse("Map stayed on the display default",state.getBoolean("defaultLocation"))
            assertTrue("Map did not center on the first fix",metersBetween(fix,
                TripPoint(state.getDouble("latitude"),state.getDouble("longitude"),System.currentTimeMillis()))<5.0)
            assertTrue("The first fix must show both Figma position assets",state.getBoolean("positionArtLoaded"))
            assertEquals(120.0,state.getDouble("positionRadiusWidth"),1.0)
            assertEquals(36.0,state.getDouble("positionDotWidth"),1.0)
            assertTrue(state.getBoolean("positionCentered"))
            assertTrue(state.getString("departureLabel").startsWith("출발 · "))
            capture(scenario,surface,"a4-first-fix.png")
            assertEquals(listOf(fix),repository.load(trip.id)!!.points)
            assertEquals(0.0,repository.load(trip.id)!!.distanceMeters,0.0)

            val nextFix = fix.copy(latitude = fix.latitude + .004, longitude = fix.longitude + .007,
                at = fix.at + 1_000)
            repository.sensor(empty.copy(latest = nextFix, points = listOf(fix, nextFix)), true)
            scenario.onActivity { surface.refresh() }
            val routeDeadline = System.currentTimeMillis() + 10_000
            do {
                state = inspect(scenario, web)
                if (state.optInt("routeFeatures") > 0) break
                Thread.sleep(250)
            } while (System.currentTimeMillis() < routeDeadline)
            assertRouteGradient(state)
            capture(scenario, surface, "a4-route-gradient.png")
        } finally {
            scenario.onActivity { (surface.parent as? ViewGroup)?.removeView(surface) }
            scenario.close();repository.delete(trip.id)
            legacy.edit().apply {
                if(previous==null)remove("external_map_consent") else putBoolean("external_map_consent",previous)
            }.commit()
        }
    }

    @Test fun manualPanAndZoomSurviveUpdatesUntilRecenterIsPressed() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyKakao")=="true")
        assumeTrue(BuildConfig.KAKAO_MAP_JS_KEY.isNotBlank())
        val now=System.currentTimeMillis()
        val empty=TripRecording("map-manual-public",now)
        lateinit var probe:KakaoTripMapView
        lateinit var web:WebView
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                probe=KakaoTripMapView(activity){_,_->}.apply {tag="map.gesture.probe";headerBackdropEnabled=true}
                (activity.window.decorView as ViewGroup).addView(probe,ViewGroup.LayoutParams(774,896))
                probe.update(empty,emptyMap(),true);probe.connect()
                web=(0 until probe.childCount).map(probe::getChildAt).filterIsInstance<WebView>().single()
            }
            val deadline=System.currentTimeMillis()+35_000
            while(System.currentTimeMillis()<deadline) {
                val state=inspect(scenario,web)
                if(state.optBoolean("ready")&&state.optInt("roadFeatures")>0)break
                Thread.sleep(250)
            }
            assertTrue(inspect(scenario,web).optBoolean("ready"))
            assertTrue(inspect(scenario,web).getBoolean("headerBackdropVisible"))
            // Drag directly through the frosted area; it must pass touches to the map.
            onView(withTagValue(org.hamcrest.Matchers.equalTo<Any>("map.gesture.probe"))).perform(
                androidx.test.espresso.action.GeneralSwipeAction(androidx.test.espresso.action.Swipe.SLOW,
                    {view->val at=IntArray(2);view.getLocationOnScreen(at);floatArrayOf(at[0]+view.width*.75f,at[1]+view.height*.07f)},
                    {view->val at=IntArray(2);view.getLocationOnScreen(at);floatArrayOf(at[0]+view.width*.2f,at[1]+view.height*.07f)},
                    androidx.test.espresso.action.Press.FINGER))
            Thread.sleep(500)
            assertEquals("Native drag was not detected","free",inspect(scenario,web).getString("viewportMode"))
            execute(scenario,web,"void map.setZoom(12)")
            Thread.sleep(600)
            val chosen=inspect(scenario,web)
            assertEquals(12.0,chosen.getDouble("level"),0.01)
            val center=TripPoint(chosen.getDouble("latitude"),chosen.getDouble("longitude"),now)
            val fix=TripPoint(37.402056,127.108212,now+1000)
            val moved=fix.copy(longitude=127.108312,at=now+2000)
            val stop=RecordedStop("manual-place",now+1000,fix,userLabel="공개 좌표 검증")
            val updated=empty.copy(latest=moved,points=listOf(fix,moved),stops=listOf(stop))
            scenario.onActivity {probe.update(updated,emptyMap(),true)}
            Thread.sleep(300)
            val after=inspect(scenario,web)
            assertEquals("free",after.getString("viewportMode"))
            assertEquals(chosen.getDouble("level"),after.getDouble("level"),0.01)
            assertTrue("GPS / new stop reset the user's viewport",metersBetween(center,
                TripPoint(after.getDouble("latitude"),after.getDouble("longitude"),now))<5.0)
            execute(scenario,web,"document.getElementById('recenter').click()")
            val recentered=inspect(scenario,web)
            assertEquals("follow",recentered.getString("viewportMode"));assertEquals(14.5,recentered.getDouble("level"),0.01)
            assertTrue(metersBetween(moved,TripPoint(recentered.getDouble("latitude"),recentered.getDouble("longitude"),now))<5.0)
            scenario.onActivity {probe.update(updated,emptyMap(),false)}
            assertFalse(inspect(scenario,web).getBoolean("recenterVisible"))
        } finally {
            scenario.onActivity {(probe.parent as? ViewGroup)?.removeView(probe);probe.close()}
            scenario.close()
        }
    }

    @Test fun tripResultShowsCoastalTerrainAndAttributionAtBottom() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyKakao") == "true")
        val now = System.currentTimeMillis()
        val trip = Trip(id = "public-coast-preview", name = "부산 해안 여행", people = 2)
        val points = listOf(
            TripPoint(35.1584, 129.1134, now - 60_000),
            TripPoint(35.1531, 129.1194, now - 10_000),
        )
        val record = TripRecording(trip.id, points.first().at, endedAt = now,
            points = points, latest = points.last(), distanceMeters = 1100.0, movingMillis = 50_000,
            stops = points.mapIndexed { index, fix ->
                RecordedStop("public-coast-$index", fix.at, fix, userLabel = "확인 지점 ${index + 1}")
            })
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var surface: C1TripResultView
        lateinit var web: WebView
        try {
            scenario.onActivity { activity ->
                surface = C1TripResultView(activity, TripResultContent(trip, record), {}, {}, {}, {})
                (activity.window.decorView as ViewGroup).addView(surface, ViewGroup.LayoutParams(-1, -1))
                val map = surface.findViewWithTag<KakaoTripMapView>("c1.map.live")
                web = (0 until map.childCount).map(map::getChildAt).filterIsInstance<WebView>().single()
            }

            var state = JSONObject()
            val deadline = System.currentTimeMillis() + 35_000
            while (System.currentTimeMillis() < deadline) {
                state = inspect(scenario, web)
                if (state.optInt("roadFeatures") > 0 && state.optInt("waterFeatures") > 0 &&
                    state.optBoolean("attributionAtBottom") && state.optInt("routeFeatures") > 0) break
                Thread.sleep(250)
            }
            assertTrue("Coastal roads were not rendered: $state", state.optInt("roadFeatures") > 0)
            assertTrue("Coastal water was not rendered: $state", state.optInt("waterFeatures") > 0)
            assertTrue("Source attribution is outside the lower-left corner: $state",
                state.optBoolean("attributionAtBottom"))
            assertEquals(3, state.getInt("symbolLayers"))
            assertTrue(state.optInt("routeFeatures") > 0)
            assertEquals("#0066ff", state.getString("routeColor"))
            assertEquals("""["1","2"]""", state.getJSONArray("stopNumbers").toString())
            assertEquals("rgb(255, 255, 255)", state.getString("firstStopFill"))
            capture(scenario, surface, "result-coastal-map.png")
        } finally {
            scenario.onActivity { (surface.parent as? ViewGroup)?.removeView(surface) }
            scenario.close()
        }
    }

    private fun assertRouteGradient(state: JSONObject) {
        assertTrue("Route was not rendered: $state", state.optInt("routeFeatures") > 0)
        assertEquals("""["interpolate",["linear"],["line-progress"],0,"#0cebd7",0.5,"#20bff0",1,"#1074fa"]""",
            state.getJSONArray("routeGradient").toString())
    }

    private fun execute(scenario:ActivityScenario<MainActivity>,web:WebView,source:String) {
        val done=CountDownLatch(1)
        scenario.onActivity {web.evaluateJavascript(source){done.countDown()}}
        assertTrue("WebView action timed out",done.await(5,TimeUnit.SECONDS))
    }

    private fun inspect(scenario:ActivityScenario<MainActivity>,web:WebView):JSONObject {
        val callback=CountDownLatch(1);val reply=AtomicReference<String>()
        scenario.onActivity {
            web.evaluateJavascript("""(function(){
                if (typeof rendered === 'undefined' || !rendered) return '{}';
                const attribution = document.querySelector('#map .maplibregl-ctrl-attrib');
                const credit = attribution?.getBoundingClientRect();
                const viewport = map.getContainer().getBoundingClientRect();
                const radius = document.querySelector('.recording-position-radius');
                const dot = document.querySelector('.recording-position-dot');
                const dotRect = dot?.getBoundingClientRect();
                const radiusRect = radius?.getBoundingClientRect();
                const fix = last?.latest ? map.project([last.latest.lng,last.latest.lat]) : null;
                return JSON.stringify({
                ready:typeof rendered!=='undefined'&&rendered,
                routeLines:typeof route==='undefined'?0:route.geometry.coordinates.length,
                routeFeatures:map.queryRenderedFeatures({layers:['route']}).length,
                routeColor:map.getPaintProperty('route','line-color'),
                stopNumbers:Array.from(document.querySelectorAll('.route-stop-number'), item => item.textContent),
                firstStopFill:document.querySelector('.route-stop-number')
                    ? getComputedStyle(document.querySelector('.route-stop-number')).backgroundColor : '',
                routeGradient:map.getPaintProperty('route','line-gradient'),
                draggable:typeof map!=='undefined'&&!!map&&!!map.dragPan.isEnabled(),
                zoomable:typeof map!=='undefined'&&!!map&&!!map.scrollZoom.isEnabled(),
                defaultLocation:typeof lastWasDefault!=='undefined'&&lastWasDefault===true,
                latitude:typeof map!=='undefined'&&map?map.getCenter().lat:null,
                longitude:typeof map!=='undefined'&&map?map.getCenter().lng:null,
                level:typeof map!=='undefined'&&map?map.getZoom():null,
                viewportMode:typeof viewportMode==='undefined'?null:viewportMode,
                recenterVisible:!document.getElementById('recenter').hidden,
                headerBackdropVisible:!document.getElementById('recording-header-backdrop').hidden,
                headerBackdropFilter:getComputedStyle(document.getElementById('recording-header-backdrop')).backdropFilter||getComputedStyle(document.getElementById('recording-header-backdrop')).webkitBackdropFilter||'none',
                headerBackdropPointerEvents:getComputedStyle(document.getElementById('recording-header-backdrop')).pointerEvents,
                labels:Array.from(document.querySelectorAll('.label')).map(e=>e.textContent),
                positionArtLoaded:radius?.naturalWidth===120 && dot?.naturalWidth===36,
                positionRadiusWidth:radiusRect ? radiusRect.width * 828 / viewport.width : 0,
                positionDotWidth:dotRect ? dotRect.width * 828 / viewport.width : 0,
                positionCentered:!!fix && !!dotRect && !!radiusRect &&
                    Math.abs(dotRect.left + dotRect.width / 2 - viewport.left - fix.x)<2 &&
                    Math.abs(dotRect.top + dotRect.height / 2 - viewport.top - fix.y)<2 &&
                    Math.abs(dotRect.left + dotRect.width / 2 - radiusRect.left - radiusRect.width / 2)<1 &&
                    Math.abs(dotRect.top + dotRect.height / 2 - radiusRect.top - radiusRect.height / 2)<1,
                departureLabel:document.querySelector('.departure-label')?.textContent || '',
                symbolLayers:typeof map!=='undefined'&&map?.getStyle()?.layers?.filter(l=>l.type==='symbol').length,
                roadFeatures:map.queryRenderedFeatures({layers:['road-edge']}).length,
                waterFeatures:map.queryRenderedFeatures({layers:['water']}).length,
                attributionAtBottom:!!credit && attribution.innerText.includes('OpenStreetMap') &&
                    credit.height>0 && credit.left>=viewport.left && credit.left-viewport.left<=8 &&
                    credit.right<=viewport.right && viewport.bottom-credit.bottom>=0 &&
                    viewport.bottom-credit.bottom<=4
            })})()""".trimIndent()) { reply.set(it);callback.countDown() }
        }
        assertTrue("WebView JavaScript callback timed out",callback.await(5,TimeUnit.SECONDS))
        val decoded=JSONTokener(reply.get()).nextValue()
        return if(decoded is String)JSONObject(decoded) else JSONObject()
    }

    private fun capture(scenario:ActivityScenario<MainActivity>,view:View,name:String="kakao-map-live.png") {
        val copied=CountDownLatch(1);val result=AtomicReference<Int>()
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        scenario.onActivity { activity ->
            val at=IntArray(2);view.getLocationInWindow(at)
            PixelCopy.request(activity.window,Rect(at[0],at[1],at[0]+view.width,at[1]+view.height),bitmap,
                { status -> result.set(status);copied.countDown() },Handler(Looper.getMainLooper()))
        }
        assertTrue(copied.await(5,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result.get().toInt())
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"design-verification").apply{mkdirs()}
        File(folder,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
