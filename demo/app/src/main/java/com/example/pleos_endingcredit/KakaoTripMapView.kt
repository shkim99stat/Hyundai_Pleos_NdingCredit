package com.example.pleos_endingcredit

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Monochrome vector basemap and trip overlays in a WebView.
 * Kakao Places supplies stop candidates. Responses are cleared on detach.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class KakaoTripMapView(context: Context,
    private val onCandidates: (String, PlaceDecision) -> Unit) : FrameLayout(context) {
    private var browser: WebView? = null
    private var demoPreview: DemoRouteFallbackView? = null
    private var scripted = false
    private var ready = false
    private var pageReady = false
    private var closed = false
    private var interactive = false
    private var payload: String? = null
    private val addressCallbacks = mutableMapOf<String, (String) -> Unit>()
    private val queried = mutableSetOf<String>()
    private val message = TextView(context).apply {
        tag = "a4.map.status"; gravity = Gravity.CENTER; setTextColor(Color.rgb(102,102,102))
        textSize = 15f; setPadding(22,22,22,22)
        text = "지도를 연결하고 있어요"
    }

    var resultPresentation = false
    var recordingPresentation = false
    private var recordingExpanded = false
    var detailPresentation = false
    private var detailExpanded = false
    var headerBackdropEnabled = false
    var recordingPositionEnabled = false

    init {
        tag = "a4.map"; setBackgroundColor(Color.rgb(242,242,242))
        addView(message, LayoutParams(-1,-1))
    }

    fun connect() {
        if (browser != null || closed) return
        val configuredOrigin = Uri.parse(BuildConfig.KAKAO_MAP_ORIGIN)
        val validOrigin = configuredOrigin.scheme == "https" && !configuredOrigin.host.isNullOrBlank() &&
            configuredOrigin.userInfo == null
        val origin = if (validOrigin) configuredOrigin.toString() else "https://appassets.androidplatform.net"
        val searchKey = if (validOrigin) BuildConfig.KAKAO_MAP_JS_KEY else ""
        val web = WebView(context).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = false
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.setGeolocationEnabled(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            addJavascriptInterface(Bridge(), "TripMap")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net") return null
                    val (filename, mimeType) = when (uri.path) {
                        "/endingcredit/maplibre-gl.js" -> "maplibre/maplibre-gl.js" to "text/javascript"
                        "/endingcredit/maplibre-gl.css" -> "maplibre/maplibre-gl.css" to "text/css"
                        "/endingcredit/a4-pin.svg" -> "travel_refresh/f4-pin.svg" to "image/svg+xml"
                        "/endingcredit/a4-pin-ring.svg" -> "travel_refresh/f4-pin-ring.svg" to "image/svg+xml"
                        "/endingcredit/f4-pin.svg" -> "f4_834/pin.svg" to "image/svg+xml"
                        "/endingcredit/f4-pin-ring.svg" -> "f4_834/pin-ring.svg" to "image/svg+xml"
                        "/endingcredit/a4-car.png" -> "travel_refresh/f4-car.png" to "image/png"
                        "/endingcredit/departure-dot.svg" -> "a4-location/departure-dot.svg" to "image/svg+xml"
                        "/endingcredit/departure-radius.svg" -> "a4-location/departure-radius.svg" to "image/svg+xml"
                        else -> return null
                    }
                    return WebResourceResponse(mimeType, "UTF-8", context.assets.open(filename))
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) post { showStatus("지도를 불러오지 못했어요. 연결을 확인해 주세요") }
                }
            }
        }
        browser = web
        web.alpha = 0f
        addView(web, if (demoPreview == null) 0 else 1, LayoutParams(-1,-1))
        val html = context.assets.open("kakao_trip_map.html").bufferedReader().use { it.readText() }
            .replace("__KAKAO_KEY_JSON__", JSONObject.quote(searchKey))
            .replace("__TRIP_DETAIL_STYLE__", (detailPresentation || resultPresentation || recordingPresentation).toString())
            .replace("__TRIP_RECORDING_STYLE__", recordingPresentation.toString())
        web.loadDataWithBaseURL(origin.trimEnd('/') + "/", html, "text/html", "UTF-8", null)
        postDelayed({ if(!closed&&!ready)showStatus("지도를 불러오지 못했어요. 연결을 확인해 주세요") },20_000)
    }

    fun update(record: TripRecording, names: Map<String,String>, interactive: Boolean,
        animationMillis: Long = 0, demoMotion: DemoMapMotion? = null) {
        this.interactive=interactive
        scripted = DemoScenario.isDemo(record.tripId)
        val animate = interactive && ValueAnimator.areAnimatorsEnabled()
        val duration = if (animate) animationMillis else 0L
        if (scripted && !ready) {
            if (demoPreview == null) {
                demoPreview = DemoRouteFallbackView(context, recordingPresentation, detailPresentation)
                    .also { addView(it, 0, LayoutParams(-1, -1)) }
                val height = (36 * resources.displayMetrics.density).toInt()
                message.layoutParams = LayoutParams(-1, height, Gravity.BOTTOM)
                message.setPadding(4, 0, 4, 0)
                message.textSize = 11f
            }
            demoPreview?.update(record, duration)
        }
        fun point(p: TripPoint) = JSONObject().put("lat",p.latitude).put("lng",p.longitude).put("gap",p.gapBefore).put("at",p.at)
        val stops = record.visibleStops
        val markers = RouteStopMarkers(stops)
        payload = JSONObject().put("interactive",interactive).put("demo",scripted)
            .put("demoArrived",demoMotion?.arrived)
            .put("demoScene",demoMotion?.scene)
            .put("demoBounds",demoMotion?.let { JSONArray(DemoScenario.routeBounds(record.tripId, it.scene)) })
            .put("demoTrack",demoMotion?.takeIf { duration > 0 }?.let {
                JSONArray(DemoScenario.animationTrack(record, it).map(::point))
            })
            .put("animationMillis",duration)
            .put("animateCamera",animate)
            .put("headerBackdrop",headerBackdropEnabled)
            .put("recordingExpanded", recordingExpanded)
            .put("tripDetail",detailPresentation)
            .put("tripResult",resultPresentation)
            .put("detailExpanded",detailExpanded)
            .put("recordingPosition",recordingPositionEnabled)
            .put("startedAt",record.startedAt)
            .put("active",record.active)
            .put("points",JSONArray((if (detailPresentation) TripRoutePreview.compact(record.points) else DemoScenario.mapPoints(record)).map(::point)))
            .put("latest",record.latest?.let(::point))
            .put("stops",JSONArray(stops.mapIndexedNotNull { index,stop -> stop.point?.let {
                point(it).put("index",index+1).put("pending",stop.selectedPlaceId == null && stop.userLabel == null)
                    .put("accent", markers.color(stop) and 0xFFFFFF)
                    .put("name",if(interactive)stop.userLabel ?: names[stop.id] ?: "확인 필요" else "")
            } })).toString()
        if (ready) browser?.evaluateJavascript("window.tripRender($payload)",null)
    }

    fun search(stop: RecordedStop) {
        if (scripted) return
        val point = stop.point ?: return
        if (!pageReady || !queried.add(stop.id)) return
        browser?.evaluateJavascript("window.tripSearch(${JSONObject.quote(stop.id)},${point.latitude},${point.longitude})",null)
    }

    fun lookupAddress(stop: RecordedStop, onAddress: (String) -> Unit): Boolean {
        val point = stop.point?.takeIf { it.valid() } ?: return false
        val web = browser ?: return false
        if (closed || !pageReady) return false
        addressCallbacks[stop.id] = onAddress
        web.evaluateJavascript("window.tripLookupAddress(${JSONObject.quote(stop.id)},${point.latitude},${point.longitude})", null)
        return true
    }

    fun setDetailExpanded(expanded: Boolean) {
        detailExpanded = expanded
        payload = payload?.let { JSONObject(it).put("detailExpanded", expanded).toString() }
        if (ready) browser?.evaluateJavascript("window.tripDetailFit($expanded)", null)
    }

    fun fitResult(expanded: Boolean) {
        detailExpanded = expanded
        payload = payload?.let { JSONObject(it).put("detailExpanded", expanded).toString() }
        if (ready) browser?.evaluateJavascript("window.tripResultFit($expanded)", null)
    }

    fun setRecordingExpanded(expanded: Boolean) {
        recordingExpanded = expanded
        demoPreview?.expandRecordingMap(expanded)
        payload = payload?.let { JSONObject(it).put("recordingExpanded", expanded).toString() }
        if (ready) browser?.evaluateJavascript("window.tripRecordingExpand($expanded)", null)
    }

    fun recenterRecording() {
        if (ready) browser?.evaluateJavascript("window.tripRecordingRecenter()", null)
    }

    fun zoomResultBy(direction: Int) {
        if (!ready) return
        val step = direction.coerceIn(-1, 1)
        browser?.evaluateJavascript("if (typeof map !== 'undefined' && map) { if (recordingStyle) holdViewport(); map.setZoom(map.getZoom() + $step); }", null)
    }

    private fun showStatus(copy: String) { message.text=if(interactive)copy else "지도 연결 대기"; message.visibility=View.VISIBLE }
    inner class Bridge {
        @JavascriptInterface fun searchPageReady() { post { if (!closed) pageReady = true } }
        @JavascriptInterface fun ready() { post {
            if (closed) return@post
            ready = true
            browser?.alpha = 1f
            demoPreview?.settlePosition()
            demoPreview?.visibility = View.GONE
            message.visibility = View.GONE
            payload?.let { browser?.evaluateJavascript("window.tripRender($it)",null) }
        } }
        @JavascriptInterface fun status(copy: String) { post { if (!closed) showStatus(copy.take(150)) } }
        @JavascriptInterface fun resolvedAddress(stopId: String, address: String) {
            if (stopId.length > 150) return
            post {
                if (!closed) addressCallbacks.remove(stopId)?.invoke(address.take(250))
            }
        }
        @JavascriptInterface fun candidates(stopId: String, json: String, complete: Boolean) {
            if (stopId.length>150 || json.length>64_000) return
            val candidates=runCatching {
                val list=JSONArray(json)
                (0 until minOf(list.length(),3)).map { index ->
                    val row=list.getJSONObject(index)
                    PlaceCandidate(row.getString("id"),row.getString("name").take(120),row.optString("category").take(250),
                        row.getDouble("distance"),row.optBoolean("restArea"),row.optString("address").take(250), row.optString("roadAddress").take(250))
                }.filter { it.id.matches(Regex("[0-9]{1,32}")) }
            }.getOrNull() ?: return
            post { if(!closed) onCandidates(stopId,decidePlaces(candidates,complete)) }
        }
    }

    fun close() {
        closed=true; queried.clear(); addressCallbacks.clear(); payload=null
        browser?.apply { this@KakaoTripMapView.removeView(this); stopLoading(); removeJavascriptInterface("TripMap"); loadUrl("about:blank");
            if (!scripted) clearCache(true)
            removeAllViews(); destroy() }
        browser=null
    }
    fun pause(){
        demoPreview?.settlePosition()
        if (scripted && ready) browser?.evaluateJavascript("window.tripPause()",null)
        browser?.onPause()
        browser?.pauseTimers()
    }
    fun resume(){browser?.onResume();browser?.resumeTimers()}
}
