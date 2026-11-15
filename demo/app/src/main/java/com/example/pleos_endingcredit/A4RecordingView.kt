package com.example.pleos_endingcredit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** A4's driving information and places tabs share the live trip map. */
internal class A4RecordingView(context:Context,private val trip:Trip,private val allowed:()->Boolean,
    cancel:()->Unit,private val end:()->Unit,
    private val fixture:TripRecording?=null,private val review:Boolean=false,
    private val demoMotion: () -> DemoMapMotion? = { null },
):ResultDesignSurface(context,1700f,1184f) {
    private val scripted = DemoScenario.isDemo(trip.id)
    private val timeZone = if (scripted) DemoScenario.zone else ZoneId.systemDefault()
    private val repository=TripRecordingRepository.get(context)
    private val handler=Handler(Looper.getMainLooper())
    private val names=mutableMapOf<String,String>();private val decisions=mutableMapOf<String,PlaceDecision>()
    private val removed=mutableSetOf<String>();private var editing=false;private var rowScale=-1f
    private var signature="";private var lastMapAt=0L
    private var lastMapHadFix = false
    private var lastMapMotion: DemoMapMotion? = null
    private var lastMapInteractive = false
    private val panelMotion = PopupMotion(this)
    private var overlay: RecordingOverlay? = null
    private var dismissAction: (() -> Unit)? = null
    private var panel:RecordingCandidatesPanel?=null;private var queue=emptyList<String>();private var queueIndex=0
    private var speech:RecordingSpeechInput?=null
    private val previousAccessibility=mutableMapOf<View,Int>()
    private val clip=Path();private val outline=Paint(Paint.ANTI_ALIAS_FLAG)
    private val controls=mutableListOf<View>()
    private var placesSelected = !review
    private var expandedMap = false
    private var selectedDay: LocalDate? = null
    private var dateMenu: PopupMenu? = null
    private val panelViews = mutableListOf<View>()
    private val mapCards = mutableListOf<View>()
    private val mapButtons = mutableListOf<View>()
    private val summaryViews = mutableListOf<View>()
    private val editorStats = mutableListOf<View>()
    private val rows = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        tag = "a4.stops"
    }
    private val scroll = object : ScrollView(context) {
        override fun getTopFadingEdgeStrength() = 0f
        override fun getSolidColor() = Color.WHITE
    }.apply {
        tag = "a4.stops.viewport"
        isScrollbarFadingEnabled = false
        isVerticalFadingEdgeEnabled = true
        overScrollMode = OVER_SCROLL_NEVER
        addView(rows)
    }
    private val title = astaLabel("a4.title", trip.name, 40f, 700, Color.WHITE)
    private val titleDate = astaLabel("a4.title.date", "", 30f, color = 0xB3FFFFFF.toInt())
    private val durationCard = astaLabel("a4.card.duration", "", 28f, 700)
    private val distanceCard = astaLabel("a4.card.distance", "", 28f, 700)
    private val passengersCard = astaLabel("a4.card.passengers", "${trip.people}명", 28f, 700)
    private val distance = summaryValue("distance")
    private val duration = summaryValue("duration")
    private val departure = summaryValue("departure")
    private val efficiency = summaryValue("efficiency")
    private val energy = summaryValue("energy")
    private val editorDistance = label("a4.editor.distance", "—", 27f, 700).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val editorDuration = label("a4.editor.duration", "0분", 27f, 700).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val tabTrack = box("a4.tabs", Color.WHITE, 17.1f, 1f, 0xFFF1F1F1.toInt())
    private val summaryTab = button("a4.tab.summary", "총 주행 정보", { selectTab(false) }, size = 28f, weight = 400, selectedWeight = 700, astaSans = true)
        .apply { letterSpacing = 0f; outlineProvider = null; background = shape(Color.WHITE, 17f) }
    private val placesTab = button("a4.tab.places", "다녀온 곳", { selectTab(true) }, size = 28f, weight = 400, selectedWeight = 700, astaSans = true)
        .apply { letterSpacing = 0f; outlineProvider = null; background = shape(BLUE, 17f) }
    private val section = astaLabel("a4.stops.title", "우리가 남긴 발자취", 40f, 700).apply {
        isAccessibilityHeading = true
    }
    private val dateFilter = button("a4.day", "", { showDateMenu() }, size = 32f, astaSans = true)
        .apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; letterSpacing = 0f }
    private val allDates = button("a4.day.all", "전체 보기", { selectDay(null) }, size = 20f, weight = 400, astaSans = true)
        .apply { setTextColor(SECONDARY); gravity = Gravity.START or Gravity.CENTER_VERTICAL; letterSpacing = 0f }
    private val calendarArrow = recordingIcon("calendar-arrow.svg", "a4.day.arrow").apply { rotation = 180f }
    private val allDatesArrow = recordingIcon("all-arrow.svg", "a4.day.all.arrow")
    private val dateDivider = recordingIcon("divider.svg", "a4.day.rule")
    private val health = label("a4.health", "", 20f, color = 0xFF686A72.toInt())
    private val badgeDot = recordingIcon("recording-dot.svg", "a4.recording.badge.dot")
    private val badgeText = astaLabel("a4.recording.badge.text", "여행 기록 중", 26f, 700, GREEN)
    private val banner=box("a4.review.banner",0xFFD6EFFF.toInt(),14f).apply{
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;isClickable=true;isFocusable=true;setOnClickListener{openCandidates()}
    }
    private val placesBannerBackground = banner.background
    private val editorBannerBackground = shape(0xFFFFF6D6.toInt(), 14f, 2f, 0xFFE8C25C.toInt())
    private val bannerIconBox=box("a4.review.icon.box",0xFFF0F0F0.toInt(),10f)
    private val bannerIcon=RecordingAsset(context,"27fad.svg")
    private val bannerLabel=label("a4.review.count","",24f,700,0xFF212121.toInt()).apply{ellipsize=null}
    private val bannerArrow=label("a4.review.arrow","›",30f,700,0xFF212121.toInt()).apply{ellipsize=null}
    private val map = KakaoTripMapView(context) { id, decision -> acceptCandidates(id, decision) }.apply {
        recordingPresentation = true
        recordingPositionEnabled = !review
    }
    private val startNotice = label(
        "a4.start.notice", "이제부터 기록할게요. 운전하는 동안엔 화면을 띄우지 않아요.",
        28f, 500, Color.WHITE, centered = true,
    ).apply {
        background = shape(0xF01F2633.toInt(), 48f)
        setTag(R.id.ending_credit_screen_id, "A3-1")
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = INVISIBLE
    }
    private val hideStartNotice = Runnable {
        if (ValueAnimator.areAnimatorsEnabled() && startNotice.isShown) {
            startNotice.animate().alpha(0f).setInterpolator(SceneEase).setDuration(180L)
                .withEndAction { dismissStartNotice() }.start()
        } else {
            dismissStartNotice()
        }
    }
    private val finish = button(
        "a4.end", if (review) "닫기" else "여행 끝내기",
        { if (allowed()) { if (review) end() else showEnd() } },
        solid = true, size = if (review) 30f else 28f, astaSans = true,
    )
    private val editCancel=button("a4.edit.cancel","그대로 둘게요",{cancelEdit()},outlined=true,size=30f)
    private val editSave=button("a4.edit.save","이대로 저장하기",{
        if(allowed()){removed.forEach{id->repository.editStop(trip.id,id){it.copy(excluded=true)}};cancelEdit()}
    },solid=true,size=30f)
    private val tick=object:Runnable{override fun run(){refresh();handler.postDelayed(this,1000)}}
    private var blink:ValueAnimator?=null
    // The A4-2 editor has its own source layout.
    private data class EditorSlot(val view:View,val x:Float,val y:Float,val w:Float,val h:Float)
    private val editorSlots=listOf(
        EditorSlot(section,868f,341.88f,265f,46.4f),
        EditorSlot(banner,868f,416.88f,774f,78f),
        EditorSlot(bannerIconBox,881f,429.88f,52f,52f),EditorSlot(bannerIcon,891f,439.88f,32f,32f),
        EditorSlot(bannerLabel,945f,441.38f,620f,29f),EditorSlot(bannerArrow,1606f,437.88f,12f,36f),
        EditorSlot(scroll,868f,522.88f,774f,330f),
    )
    init {
        tag="a4.recording"
        place(map, 2f, 2f, 1696f, 1180f)
        place(box("a4.title.card", INK, 24f, 1f, BORDER), 30f, 40f, 544f, 192f)
        place(title, 70f, 80f, 470f, 52f)
        place(titleDate, 70f, 146f, 220f, 38f)
        place(badgeDot, 292f, 158f, 14f, 14f)
        place(badgeText, 318f, 148f, 240f, 34f)
        buildMapControls()
        buildMapCards()

        panel(box("a4.panel", Color.WHITE, 24f, 1f, BORDER), 950f, 40f, 722f, 1104f)
        panel(box("a4.panel.handle", BORDER, 3f), 1245f, 60f, 132f, 6f)
        panel(recordingIcon("refresh.svg", "a4.updated.icon"), 1460f, 125f, 24f, 24f)
        panel(astaLabel("a4.updated", "최신 업데이트", 26f, color = SUBTLE), 1492f, 120f, 148f, 34f)
        panel(tabTrack, 990f, 192f, 642f, 84f)
        panel(placesTab, 998f, 198f, 313f, 72f)
        panel(summaryTab, 1311f, 198f, 313f, 72f)
        controls += listOf(summaryTab, placesTab, dateFilter, allDates)
        panel(dateFilter, 990f, 299f, 260f, 58f)
        panel(calendarArrow, 1229f, 320f, 17.8624f, 16f)
        panel(allDates, 1538f, 299f, 94f, 58f)
        panel(allDatesArrow, 1613f, 318f, 11.7387f, 21.7387f)
        panel(dateDivider, 990f, 376f, 642f, 1f)
        buildSummary()

        place(box("a4.editor.background", Color.WHITE, 24f, 1f, BORDER).also { editorStats += it },
            850f, 32f, 822f, 1106f)
        editorStats += place(label("a4.editor.distance.label","달린 거리",26f),874f,158.17f,400f,37.7f)
        editorStats += place(editorDistance,1242f,157.73f,392f,39.15f)
        editorStats += place(box("a4.editor.distance.line",0xFFDDDDDD.toInt()),868f,212.94f,774f,2f)
        editorStats += place(label("a4.editor.duration.label","달린 시간",26f),874f,247.31f,400f,37.7f)
        editorStats += place(editorDuration,1242f,246.87f,392f,39.15f)
        editorStats += place(box("a4.editor.duration.line",0xFFDDDDDD.toInt()),868f,302.08f,774f,2f)
        place(section, 990f, 104f, 442f, 52f)
        place(scroll, 990f, 412f, 642f, 589f)
        place(banner, 1180f, 919f, 444f, 64f)
        controls += banner
        place(bannerIconBox, 1191f, 925f, 52f, 52f)
        place(bannerIcon, 1201f, 935f, 32f, 32f)
        place(bannerLabel, 1255f, 935f, 333f, 29f)
        place(bannerArrow, 1599f, 933f, 12f, 36f)
        place(health, 984f, 1094f, 340f, 32f)
        finish.background = shape(if (review) INK else BLUE, 16f)
        place(finish, if (review) 1250f else 990f, 1012f, if (review) 376f else 642f, 92f)
        controls += finish
        place(editCancel, 868f, 942f, 286f, 96f)
        place(editSave, 1170f, 942f, 472f, 96f)
        if (review && trip.mode.showsCosts) {
            place(button("a4.costs", "이동비", cancel, outlined = true, size = 30f),
                990f, 1012f, 246f, 92f)
        }
        place(startNotice, 461f, 915f, 779f, 82f)
    }
    private fun astaLabel(id: String, copy: String, size: Float, weight: Int = 400, color: Int = INK) =
        label(id, copy, size, weight, color, typeface = AppTypography.font(context, weight))

    private fun recordingIcon(filename: String, id: String) = asset(filename, id, "a4-recording")

    private fun summaryValue(id: String) =
        astaLabel("a4.$id", "—", 28f, 700).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

    private fun panel(view: View, x: Float, y: Float, width: Float, height: Float) {
        panelViews += place(view, x, y, width, height)
    }

    private fun buildMapControls() {
        mapButton("expand", "지도 펼치기", "expand.svg", 40f) {
            expandedMap = !expandedMap
            map.setRecordingExpanded(expandedMap)
            refresh()
        }
        mapButton("in", "지도 확대", "plus.svg", 448f) { map.zoomResultBy(1) }
        mapButton("out", "지도 축소", "minus.svg", 548f) { map.zoomResultBy(-1) }
        mapButton("fit", "내 위치", "recenter.svg", 648f) { map.recenterRecording() }
    }

    private fun mapButton(id: String, description: String, icon: String, y: Float, action: () -> Unit) {
        mapButtons += if (id == "in" || id == "out") {
            place(recordingIcon(icon, "a4.map.$id.icon"), 807f, y - 18f, 136f, 136f)
        } else {
            mapButtons += place(box("a4.map.$id.background", Color.WHITE, 16f, 1f, BORDER), 831f, y, 88f, 88f)
            place(recordingIcon(icon, "a4.map.$id.icon"), 855f, y + 24f, 40f, 40f)
        }
        mapButtons += place(button("a4.map.$id", "", { if (allowed()) action() })
            .apply { contentDescription = description }, 831f, y, 88f, 88f)
    }

    private fun buildMapCards() {
        val captions = listOf("주행 시간", "주행 거리", "함께 한 인원")
        val values = listOf(durationCard, distanceCard, passengersCard)
        val icons = listOf("duration.svg", "distance.svg", "passengers.svg")
        val iconWidths = listOf(51.4286f, 48f, 50.5717f)
        val iconHeights = listOf(51.4286f, 48f, 36.5804f)
        val iconTops = listOf(970f, 972f, 978f)
        captions.forEachIndexed { index, caption ->
            val x = 30f + 300f * index
            mapCards += place(box("a4.card.$index", Color.WHITE, 24f, 1f, BORDER), x, 944f, 288f, 200f)
            mapCards += place(recordingIcon(icons[index], "a4.card.$index.icon"),
                x + 19f, iconTops[index], iconWidths[index], iconHeights[index])
            mapCards += place(values[index], x + 20f, 1044f, 248f, 36f)
            mapCards += place(astaLabel("a4.card.$index.caption", caption, 26f, color = SUBTLE),
                x + 20f, 1084f, 248f, 34f)
        }
    }

    private fun buildSummary() {
        val metrics = listOf(
            "출발" to departure, "주행 거리" to distance, "주행 시간" to duration,
            "차량 전비" to efficiency, "사용한 전기" to energy,
            "충전" to summaryValue("charging"), "지나온 요금소" to summaryValue("tolls"),
            "탑승자" to summaryValue("passengers").apply { text = "${trip.people}명" },
        )
        metrics.forEachIndexed { index, (copy, value) ->
            val y = 320f + index * 80f
            summaryViews += place(astaLabel("${value.tag}.label", copy, 26f, color = SECONDARY),
                990f, y, 256f, 34f)
            summaryViews += place(value, 1246f, y - 1f, 386f, 36f)
            if (index < metrics.lastIndex) {
                summaryViews += place(recordingIcon("divider.svg", "a4.summary.rule.$index"),
                    990f, y + 58f, 642f, 1f)
            }
        }
    }

    private fun selectDay(day: LocalDate?) {
        if (!allowed() || editing) return
        selectedDay = day
        signature = ""
        scroll.scrollTo(0, 0)
        refresh()
    }

    private fun showDateMenu() {
        val record = current() ?: return
        dateMenu?.dismiss()
        dateMenu = PopupMenu(context, dateFilter).apply {
            menu.add("전체 보기").setOnMenuItemClickListener { selectDay(null); true }
            val dates = (listOf(record.startedAt) + record.visibleStops.map { it.at } +
                listOfNotNull(record.latest?.at, record.endedAt)).map { travelLocalDate(it, timeZone) }.distinct().sorted()
            dates.forEach { date ->
                menu.add(date.format(titleDateFormat)).setOnMenuItemClickListener { selectDay(date); true }
            }
            show()
        }
    }

    private fun displayedStops(record: TripRecording): List<RecordedStop> = record.visibleStops.filter { stop ->
        stop.id !in removed && (editing || selectedDay == null || travelLocalDate(stop.at, timeZone) == selectedDay)
    }

    private fun selectTab(showPlaces: Boolean) {
        if (!allowed() || editing || placesSelected == showPlaces) return
        placesSelected = showPlaces
        refresh()
        requestLayout()
        if (isAttachedToWindow && isLaidOut && ValueAnimator.areAnimatorsEnabled()) {
            val content = if (showPlaces) listOf(scroll) else summaryViews
            content.forEach { view ->
                view.animate().cancel()
                view.alpha = .55f
                view.animate().alpha(1f).setInterpolator(EnterEase).setDuration(170L).start()
            }
        }
    }
    private fun current()=fixture?:repository.load(trip.id)
    val blocksDemoControls get() = overlay != null || editing

    fun reviewDemoStop(id: String) {
        selectTab(true)
        openCandidates(id)
    }

    fun requestDemoFinish() {
        if (allowed()) showEnd()
    }

    internal fun acceptCandidates(id:String,decision:PlaceDecision){
        val stop=current()?.visibleStops?.find{it.id==id}?:return
        PlaceDisplayNames.remember(decision.candidates)
        decisions[id]=decision
        val selected=stop.selectedPlaceId?:if(stop.userLabel==null)decision.automaticId else null
        decision.candidates.find{it.id==selected}?.let{names[id]=it.name}
        if(fixture==null)repository.editStop(trip.id,id){old->old.copy(selectedPlaceId=old.selectedPlaceId?:if(old.userLabel==null)decision.automaticId else null,resolutionAttempted=true)}
        if(queue.getOrNull(queueIndex)==id)panel?.updateCandidates(decision.candidates)
        signature="";lastMapAt=0;refresh()
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();if(fixture==null){handler.post(tick);map.connect()}else refresh()
        if(!review&&ValueAnimator.areAnimatorsEnabled())blink=ValueAnimator.ofFloat(1f,.35f,1f).apply{duration=1600;repeatCount=ValueAnimator.INFINITE;addUpdateListener{badgeDot.alpha=it.animatedValue as Float};start()}}
    override fun onDetachedFromWindow(){handler.removeCallbacks(tick);dismissStartNotice();blink?.cancel();blink=null;dismissPanel(animate = false);speech?.close();map.close();names.clear();decisions.clear();dateMenu?.dismiss();super.onDetachedFromWindow()}
    fun onHostStopped(){dateMenu?.dismiss();handler.removeCallbacks(tick);dismissStartNotice();panelMotion.settle();speech?.close();map.pause()}
    fun onHostStarted(){map.resume();handler.removeCallbacks(tick);if(fixture==null)handler.post(tick)}
    fun showStartNotice() {
        if (review || !isAttachedToWindow || !allowed()) return
        handler.removeCallbacks(hideStartNotice)
        startNotice.animate().cancel()
        val animate = isLaidOut && ValueAnimator.areAnimatorsEnabled()
        startNotice.alpha = if (animate) 0f else 1f
        startNotice.visibility = VISIBLE
        if (animate) startNotice.animate().alpha(1f).setInterpolator(EnterEase).setDuration(180L).start()
        handler.postDelayed(hideStartNotice, 3_000L)
    }
    fun dismissStartNotice() {
        handler.removeCallbacks(hideStartNotice)
        startNotice.animate().cancel()
        startNotice.visibility = INVISIBLE
        startNotice.alpha = 1f
    }
    fun handleBack():Boolean{dismissAction?.let{it();return true};if(editing){cancelEdit();return true}
        if (expandedMap) { expandedMap = false; map.setRecordingExpanded(false); refresh(); return true }
        return false
    }
    fun refresh(){
        val record=current()?:return;val can=allowed()
        if(!can){dateMenu?.dismiss();dismissStartNotice();dismissPanel(animate = false);if(editing){editing=false;removed.clear()}}
        controls.forEach{it.isEnabled=can}
        finish.alpha = if (finish.isEnabled) 1f else .35f
        title.text = if (can) trip.name else "여행 기록 중"
        val startDate = travelLocalDate(record.startedAt, timeZone)
        titleDate.text = startDate.format(titleDateFormat)
        dateFilter.text = (selectedDay ?: startDate).format(titleDateFormat)
        dateFilter.contentDescription = "여행 날짜 선택, " + (selectedDay?.format(titleDateFormat) ?: "전체")
        listOf(badgeDot, badgeText).forEach { it.setVisible(record.active && !review) }
        setTag(R.id.ending_credit_screen_id, if (editing) "A4-2" else "A4")
        val showPanel = !expandedMap && !editing
        val showPlaces = can && showPanel && placesSelected
        val showSummary = showPanel && !showPlaces
        panelViews.forEach { it.setVisible(showPanel) }
        listOf(tabTrack, summaryTab, placesTab).forEach {
            it.setVisible(can && showPanel)
        }
        listOf(dateFilter, calendarArrow, allDates, allDatesArrow, dateDivider).forEach { it.setVisible(showPlaces) }
        summaryTab.isSelected = !placesSelected
        placesTab.isSelected = placesSelected
        listOf(summaryTab, placesTab).forEach { tab ->
            (tab.background as GradientDrawable).setColor(if (tab.isSelected) BLUE else Color.WHITE)
            tab.setTextColor(if (tab.isSelected) Color.WHITE else SECONDARY)
        }
        mapCards.forEach { it.setVisible(showPanel) }
        mapButtons.forEach { it.setVisible(can && !editing) }
        findViewWithTag<View>("a4.map.expand")?.contentDescription =
            if (expandedMap) "여행 정보 다시 열기" else "지도 펼치기"
        summaryViews.forEach { it.setVisible(showSummary) }
        editorStats.forEach { it.setVisible(editing) }
        section.text = if (editing) "다녀온 곳 수정" else "우리가 남긴 발자취"
        section.setVisible(showPanel || editing)
        scroll.setVisible(showPlaces || editing)
        scroll.isVerticalScrollBarEnabled = showPlaces
        finish.setVisible(can && showPanel && (!review || showSummary || !trip.mode.showsCosts))
        findViewWithTag<View>("a4.costs")?.setVisible(can && showSummary)
        health.setVisible(showPanel && can)
        findViewWithTag<View>("a4.permissions")?.setVisible(showPanel && can && !review)
        editCancel.setVisible(can && editing)
        editSave.setVisible(can && editing)
        distance.text = distanceText(record)
        val minutes = record.movingMillis / 60_000
        duration.text = if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
        departure.text = departureFormat.format(Instant.ofEpochMilli(record.startedAt).atZone(timeZone))
        val usedKwh = if (scripted) demoEnergyKwh(record) else null
        energy.text = usedKwh?.let { String.format(Locale.KOREAN, "%.1f kWh", it) } ?: "—"
        efficiency.text = usedKwh?.takeIf { it > 0.0 }?.let { used ->
            val kilometers = record.distanceMeters / 1_000.0
            (kilometers / used).takeIf { it.isFinite() && it > 0.0 }
                ?.let { String.format(Locale.KOREAN, "%.1f km/kWh", it) }
        } ?: "—"
        durationCard.text = duration.text
        distanceCard.text = distance.text
        editorDistance.text = distance.text
        editorDuration.text = duration.text
        val visible = record.visibleStops.filterNot { it.id in removed }
        val shownStops = displayedStops(record)
        val unresolved = shownStops.count { it.selectedPlaceId == null && it.userLabel == null }
        listOf(banner,bannerIconBox,bannerIcon,bannerLabel,bannerArrow).forEach {
            it.setVisible(unresolved > 0 && (showPlaces || editing))
        }
        val background = if (editing) editorBannerBackground else placesBannerBackground
        if (banner.background !== background) banner.background = background
        bannerLabel.text=if(editing)"어디였는지 확인이 필요한 곳이 ${unresolved}곳 있어요" else "확인이 필요한 곳 ${unresolved}곳";banner.contentDescription=bannerLabel.text
        health.text = when {
            !can || review -> ""
            repository.storageError != null -> repository.storageError
            else -> ""
        }
        if (scripted) {
            visible.filter { it.id !in decisions }.forEach { stop ->
                decisions[stop.id] = decidePlaces(DemoScenario.candidates(trip.id, stop.id), true)
            }
        }
        val next="$editing:$selectedDay:"+shownStops.joinToString{"${it.id}:${it.selectedPlaceId}:${it.userLabel}:${names[it.id]}"}
        val changed = next != signature
        if (changed || rows.childCount == 0) {
            signature = next
            populate(shownStops)
        }
        val hasFix = record.latest != null
        val now = SystemClock.uptimeMillis()
        val motion = demoMotion()
        val previousMotion = lastMapMotion
        val movementChanged = motion != null && (
            previousMotion == null || motion.scene != previousMotion.scene || motion.jump != previousMotion.jump ||
                motion.playing != previousMotion.playing || motion.arrived != previousMotion.arrived
            )
        val mapDue = if (motion != null) {
            motion.elapsed != previousMotion?.elapsed && now - lastMapAt >= 240L
        } else now - lastMapAt >= 5_000L
        if (lastMapAt == 0L || hasFix != lastMapHadFix || can != lastMapInteractive ||
            movementChanged || mapDue || (scripted && changed) || fixture != null) {
            val shown = record.copy(stops = record.stops.map { if (it.id in removed) it.copy(excluded = true) else it })
            map.update(shown, names, can, motion?.durationSince(previousMotion) ?: 0L, motion)
            lastMapHadFix = hasFix
            lastMapAt = now
            lastMapMotion = motion
            lastMapInteractive = can
        }
        if (!scripted && fixture == null) visible.filter { it.userLabel == null }.forEach(map::search)
    }
    private fun View.setVisible(show: Boolean) {
        visibility = if (show) VISIBLE else INVISIBLE
    }

    private fun populate(stops:List<RecordedStop>){
        rows.removeAllViews()
        val needsReview = stops.any { it.selectedPlaceId == null && it.userLabel == null }
        rows.setPadding(0, 0, 0, if (needsReview && !editing) px(72f) else 0)
        if(stops.isEmpty()){
            val copy = if (selectedDay == null || editing) "차를 세우고(P) 문을 열면 여기에 기록돼요" else "이 날짜에 기록된 곳이 없어요"
            val empty = recordingText(context,copy,26f,drawingScale,color=0xFF686A72.toInt())
            empty.tag = "a4.empty"
            empty.maxLines = 2
            empty.setPadding(if (editing) 0 else px(8f), 0, 0, 0)
            rows.addView(empty, LinearLayout.LayoutParams(-1,px(100f)))
            return
        }
        if (!editing) {
            populateTimeline(stops)
            return
        }
        stops.forEachIndexed{i,stop->
            val known=stop.userLabel!=null||stop.selectedPlaceId!=null
            val row=FrameLayout(context).apply{isClickable=true;isFocusable=true;setOnClickListener{if(allowed())openCandidates(stop.id)}}
            val icon=FrameLayout(context).apply{background=GradientDrawable().apply{setColor(if(known)0xFFE6E6E6.toInt() else 0xFFF0F0F0.toInt());cornerRadius=10*drawingScale}}
            if(known)icon.addView(recordingText(context,"${i+1}",20f,drawingScale,700).apply{gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))
            else icon.addView(RecordingAsset(context,"27fad.svg"),FrameLayout.LayoutParams(px(32f),px(32f)).apply{leftMargin=px(10f);topMargin=px(10f)})
            row.addView(icon,FrameLayout.LayoutParams(px(52f),px(52f)))
            val text="${stop.userLabel?:names[stop.id]?:PlaceDisplayNames.display(stop)} · ${RecordingCandidatesPanel.time(stop.at)}"
            row.contentDescription=text
            row.addView(recordingText(context,text,26f,drawingScale).apply{tag="a4.stop.$i"},FrameLayout.LayoutParams(px(if(editing)640f else 706f),px(38f)).apply{leftMargin=px(68f);topMargin=px(6.8f)})
            if(editing)row.addView(recordingText(context,"×",28f,drawingScale,500).apply{tag="a4.remove.${stop.id}";gravity=Gravity.CENTER;isClickable=true;isFocusable=true;contentDescription="$text 빼기";background=GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=24*drawingScale;setStroke(px(2f),0xFF333333.toInt())};setOnClickListener{if(allowed()){removed+=stop.id;signature="";lastMapAt=0;refresh()}}},FrameLayout.LayoutParams(px(48f),px(48f)).apply{leftMargin=px(726f);topMargin=px(2f)})
            rows.addView(row,LinearLayout.LayoutParams(-1,px(80f)))
        }
    }

    private fun populateTimeline(stops: List<RecordedStop>) {
        val record = current()
        val allStops = record?.visibleStops.orEmpty()
        val numbers = allStops.mapIndexed { index, stop -> stop.id to index + 1 }.toMap()
        stops.forEachIndexed { index, stop ->
            val number = numbers[stop.id] ?: index + 1
            val name = stop.userLabel ?: names[stop.id] ?: PlaceDisplayNames.display(stop)
            val address = decisions[stop.id]?.candidates?.firstOrNull {
                it.id == stop.selectedPlaceId || (scripted && it.name == stop.userLabel)
            }
                ?.address?.takeIf { it.isNotBlank() } ?: PlaceDisplayNames.getRoadAddress(stop.selectedPlaceId)?.takeIf(String::isNotBlank)
                ?: PlaceDisplayNames.getAddress(stop.selectedPlaceId)?.takeIf(String::isNotBlank)
                ?: "주소 정보 없음"
            val time = stopTimeFormat.format(Instant.ofEpochMilli(stop.at).atZone(timeZone))
            val row = FrameLayout(context).apply {
                contentDescription = "${number}번, $name, $address, $time"
                isFocusable = true
                setOnClickListener { if (allowed()) openCandidates(stop.id) }
            }
            fun line(y: Float, height: Float) {
                row.addView(View(context).apply { setBackgroundColor(BLUE) }, timelineSlot(34f, y, 4f, height))
            }
            if (index > 0) line(0f, 36f)
            if (index < stops.lastIndex) line(36f, 120f)
            val badge = recordingText(context, number.toString(), 28f, drawingScale, 700, color = Color.WHITE).apply {
                tag = "a4.stop.number.$index"
                typeface = AppTypography.font(context, 700)
                gravity = Gravity.CENTER
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(BLUE)
                }
            }
            row.addView(badge, timelineSlot(0f, 0f, 72f, 72f))
            row.addView(recordingText(context, name, 30f, drawingScale, 700, INK).apply {
                typeface = AppTypography.font(context, 700)
                tag = "a4.stop.$index"
            }, timelineSlot(96f, 0f, 538f, 38f))
            row.addView(recordingText(context, address, 24f, drawingScale, color = SUBTLE).apply {
                typeface = AppTypography.font(context)
                tag = "a4.stop.address.$index"
            }, timelineSlot(96f, 42f, 538f, 28f))
            row.addView(recordingIcon("row-time.svg", "a4.stop.clock.$index"),
                timelineSlot(95f, 83f, 25.7143f, 25.7143f))
            row.addView(recordingText(context, time, 24f, drawingScale, color = INK).apply {
                tag = "a4.stop.time.$index"
                typeface = AppTypography.font(context)
            }, timelineSlot(128f, 82f, 335f, 28f))
            rows.addView(row, LinearLayout.LayoutParams(-1, px(156f)))
        }
    }

    private fun px(value: Float) = (value * drawingScale).roundToInt().coerceAtLeast(1)

    private fun timelineSlot(x: Float, y: Float, width: Float, height: Float) =
        FrameLayout.LayoutParams(px(width), px(height)).apply {
            leftMargin = (x * drawingScale).roundToInt()
            topMargin = (y * drawingScale).roundToInt()
        }

    private fun cancelEdit(){editing=false;removed.clear();signature="";lastMapAt=0;refresh()}
    private fun openCandidates(id:String?=null){
        if(!allowed())return
        val visible=current()?.visibleStops.orEmpty().filterNot{it.id in removed}
        val target=visible.find{it.id==id}
        queue=if(target!=null&&(target.userLabel!=null||target.selectedPlaceId!=null))listOf(target.id)else visible.filter{it.userLabel==null&&it.selectedPlaceId==null}.map{it.id}
        queueIndex=if(id==null)0 else queue.indexOf(id).coerceAtLeast(0);showCandidate()
    }
    private fun showCandidate(){
        if(!allowed())return
        val stop=current()?.visibleStops?.find{it.id==queue.getOrNull(queueIndex)}
        if(stop==null){dismissPanel();queue=emptyList();return}
        val p=RecordingCandidatesPanel(context,stop,stop.userLabel?:names[stop.id]?:"확인 필요",queueIndex,queue.size,
            close={dismissPanel();queue=emptyList()},later={queueIndex++;showCandidate()},choose={candidate->if(candidate==null)showManual(stop)else{saveCandidate(stop,candidate);queueIndex++;showCandidate()}})
        p.updateCandidates(decisions[stop.id]?.candidates.orEmpty());showPanel(p,1100f,824f){dismissPanel();queue=emptyList()};panel=p
    }
    private fun saveCandidate(stop: RecordedStop, candidate: PlaceCandidate) {
        if (!allowed()) return
        repository.editStop(trip.id, stop.id) {
            it.copy(
                selectedPlaceId = candidate.id.takeUnless { scripted },
                userLabel = candidate.name.takeIf { scripted },
                resolutionAttempted = true,
            )
        }
        names[stop.id] = candidate.name
        signature = ""
        lastMapAt = 0
        refresh()
    }

    private fun showManual(stop:RecordedStop){
        if(!allowed())return
        val p=RecordingNamePanel(context,stop,stop.userLabel?:names[stop.id]?:"확인 필요",decisions[stop.id]?.candidates.orEmpty(),
            cancel={showCandidate()},save={name->if(allowed()){repository.editStop(trip.id,stop.id){it.copy(userLabel=name,selectedPlaceId=null,resolutionAttempted=true)};names.remove(stop.id);signature="";lastMapAt=0;refresh();queueIndex++;showCandidate()}},
            suggestion={candidate->saveCandidate(stop,candidate);queueIndex++;showCandidate()},voice={result->if(allowed()){speech?.close();speech=RecordingSpeechInput(context,result);speech?.start()}})
        showPanel(p,1100f,823f,62.36f){showCandidate()};p.openKeyboard()
    }
    private fun showEnd(){val record=current()?:return;showPanel(RecordingEndPanel(context,distanceText(record).let{if(it=="—")"0.0km" else it},record.visibleStops.size,{dismissPanel()},{if(allowed()){dismissPanel();end()}}),900f,350.8f){dismissPanel()}}
    private fun showPanel(view: View, w: Float, h: Float, top: Float? = null, dismiss: () -> Unit) {
        val replacing = overlay != null
        dismissPanel(animate = false)
        if (!allowed()) return
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            previousAccessibility[child] = child.importantForAccessibility
            child.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val layer = RecordingOverlay(context, view, w, h, top, dismiss)
        overlay = layer
        dismissAction = dismiss
        panelMotion.show(layer, animate = !replacing)
        layer.requestFocus()
        requestLayout()
    }

    private fun dismissPanel(animate: Boolean = true) {
        speech?.close()
        speech = null
        overlay?.let {
            context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0)
        }
        panelMotion.dismiss(animate)
        overlay = null
        panel = null
        dismissAction = null
        previousAccessibility.forEach { (view, value) -> view.importantForAccessibility = value }
        previousAccessibility.clear()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (rowScale != drawingScale) {
            rowScale = drawingScale
            signature = ""
            current()?.let { populate(displayedStops(it)) }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
        tabTrack.elevation = 6f * drawingScale
        finish.elevation = 4f * drawingScale
        summaryTab.translationZ = 7f * drawingScale
        placesTab.translationZ = 7f * drawingScale
        scroll.scrollBarSize = px(8f)
        scroll.setFadingEdgeLength(px(204f))
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            scroll.verticalScrollbarThumbDrawable = GradientDrawable().apply {
                setColor(SUBTLE)
                cornerRadius = 40f * drawingScale
            }
        }
        banner.elevation = if (editing) 0f else 4f * drawingScale
        bannerIconBox.translationZ = banner.elevation
        bannerIcon.translationZ = banner.elevation
        bannerLabel.translationZ = banner.elevation
        bannerArrow.translationZ = banner.elevation
        if(editing)editorSlots.forEach{slot->slot.view.measure(MeasureSpec.makeMeasureSpec((slot.w*drawingScale).roundToInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((slot.h*drawingScale).roundToInt(),MeasureSpec.EXACTLY))}
        else {
            // Preserve the count when it grows beyond the single-digit Figma example.
            val textPaint=Paint(bannerLabel.paint).apply{textSize=24f}
            val available=bannerLabel.measuredWidth/drawingScale-.25f
            val size=minOf(24f,24f*available/textPaint.measureText(bannerLabel.text.toString()).coerceAtLeast(1f))
            textPaint.textSize=size;val fm=textPaint.fontMetrics
            bannerLabel.setReferenceTypography(size,size*1.45f,(29f-fm.descent+fm.ascent)/2f-fm.ascent,drawingScale)
        }
        overlay?.measure(MeasureSpec.makeMeasureSpec((1700*drawingScale).roundToInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((1184*drawingScale).roundToInt(),MeasureSpec.EXACTLY))
        overlay?.translationZ = 12f * drawingScale
        clip.rewind();clip.addRoundRect(surface,24*drawingScale,24*drawingScale,Path.Direction.CW)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if(editing)editorSlots.forEach{slot->val x=(surface.left+slot.x*drawingScale).roundToInt();val y=(surface.top+slot.y*drawingScale).roundToInt();slot.view.layout(x,y,x+slot.view.measuredWidth,y+slot.view.measuredHeight)}
        overlay?.layout(surface.left.roundToInt(),surface.top.roundToInt(),surface.right.roundToInt(),surface.bottom.roundToInt())
    }
    override fun dispatchDraw(canvas:Canvas){val saved=canvas.save();canvas.clipPath(clip);super.dispatchDraw(canvas);canvas.restoreToCount(saved);outline.color=BORDER;outline.style=Paint.Style.STROKE;outline.strokeWidth=2*drawingScale;val rect=RectF(surface).apply{inset(drawingScale,drawingScale)};canvas.drawRoundRect(rect,23*drawingScale,23*drawingScale,outline)}
    private fun distanceText(record:TripRecording)=if(record.active&&record.latest==null&&record.distanceMeters==0.0)"—" else String.format(java.util.Locale.KOREA,if(record.distanceMeters<1000)"%.1fkm" else "%.0fkm",record.distanceMeters/1000)

    private companion object {
        const val INK = 0xFF131417.toInt()
        const val BLUE = 0xFF0064FF.toInt()
        const val GREEN = 0xFF55F165.toInt()
        const val BORDER = 0xFFE0E1E6.toInt()
        const val SECONDARY = 0xFF44464E.toInt()
        const val SUBTLE = 0xFF686A72.toInt()
        val titleDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd(E)", Locale.KOREAN)
        val departureFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd(E) HH:mm", Locale.KOREAN)
        val stopTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("a hh:mm", Locale.KOREAN)
    }
}
