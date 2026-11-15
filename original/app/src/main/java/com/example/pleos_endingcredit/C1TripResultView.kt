package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.PopupMenu

/** C1 app window, including the timeline layers outside the linked content frame. */
internal class C1TripResultView(
    context: Context,
    private val content: TripResultContent,
    home: () -> Unit,
    costs: () -> Unit,
    editPlaces: () -> Unit,
    replay: () -> Unit,
    card: () -> Unit = {},
    removePlace: (String) -> Unit = {},
    selectedDay: Int = 0,
    private val onDay: (Int) -> Unit = {},
) : ResultDesignSurface(context, 1696f, 1180f) {
    private val pending = C1Places.entries(content).count { it.needsReview }
    private val reviewTop = FOOTER_TOP - FOOTER_GAP - (if (pending > 0) REVIEW_HEIGHT else 0f)
    private val timelineHeight = reviewTop - TIMELINE_TOP
    private val timeline = C1Timeline(context, content, timelineHeight, removePlace)
    private val dayTabs = TripDayTabs(context, "c1", timeline.dayCount, selectedDay, timeline::dayLabel, ::selectDay)
    private val placeCount = label("c1.places.count", "", 20f, color = 0xFF44464E.toInt(),
        centered = true, typeface = AppTypography.font(context))
    private val placeHandler = Handler(Looper.getMainLooper())
    private var lookups = 0
    private var expanded = false
    private var liveMap: KakaoTripMapView? = null
    private val mapArt = if (content.demo) ReferenceDetailMap(context) else null
    private val viewport = FrameLayout(context).apply {
        tag = "c1.map.viewport"
        background = shape(0xFFF0F0F0.toInt(), 16f)
        clipToOutline = true
    }
    private val collapsedViews = mutableListOf<View>()
    private val zoomIn: View
    private val zoomOut: View
    private val recenter: View
    private val expandControl: View
    private var menu: PopupMenu? = null

    private val requestedAddresses = mutableSetOf<String>()

    private val refreshPlaceNames = object : Runnable {
        override fun run() {
            placeHandler.removeCallbacks(this)
            val record = content.record ?: return
            timeline.refreshPlaceDetails(record, updateNames = content.trip.resultPlaces == null)
            updateMap(record)
            val missingNames = record.visibleStops.filter {
                it.selectedPlaceId != null && PlaceDisplayNames.get(it.selectedPlaceId) == null
            }
            missingNames.forEach { liveMap?.search(it) }
            val stopsNeedingAddress = record.visibleStops.filter {
                it.point?.valid() == true && it.id !in requestedAddresses &&
                    PlaceDisplayNames.getRoadAddress(it.selectedPlaceId).isNullOrBlank()
            }
            stopsNeedingAddress.forEach { stop ->
                val sent = liveMap?.lookupAddress(stop) { address ->
                    timeline.showAddress(stop, address)
                } == true
                if (sent) requestedAddresses.add(stop.id)
            }
            if ((missingNames.isNotEmpty() || stopsNeedingAddress.any { it.id !in requestedAddresses }) && lookups++ < 20) {
                placeHandler.postDelayed(this, 1000)
            }
        }
    }

    init {
        tag = "c1.result"
        val map = mapArt ?: content.record?.let {
            KakaoTripMapView(context) { _, decision ->
                PlaceDisplayNames.remember(decision.candidates)
                placeHandler.post(refreshPlaceNames)
            }.apply {
                tag = "c1.map.live"
                resultPresentation = true
                detailPresentation = true
                liveMap = this
            }
        } ?: android.widget.TextView(context).apply {
            tag = "c1.map.empty"
            text = "남겨진 경로가 없어요"
            setTextColor(muted)
            gravity = android.view.Gravity.CENTER
        }
        viewport.addView(map, FrameLayout.LayoutParams(-1, -1))
        place(viewport, 0f, 0f, 1696f, 1180f)

        val tripTitle = content.title.removeSuffix("을 기록했어요.")
        val firstLine = if (content.demo) "우리의 동해안" else tripTitle.removeSuffix(" 여행")
        val secondLine = if (content.demo || tripTitle.endsWith(" 여행")) "여행을 기록했어요." else "기록했어요."
        place(box("c1.title.card", 0xFF122241.toInt(), 24f, 1f, 0xFFC3C3C3.toInt()),
            30f, 38.52f, 544f, 252f)
        place(label("c1.title", firstLine, 40f, 700, Color.WHITE).apply {
            isAccessibilityHeading = true
            contentDescription = "$firstLine $secondLine"
        }, 62.63f, 74f, 460f, 58f)
        place(label("c1.title.continued", secondLine, 40f, 700, Color.WHITE).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 62.63f, 132f, 460f, 58f)
        place(label("c1.dates", content.dates, 30f, color = 0xB3FFFFFF.toInt()),
            62.63f, 210f, 480f, 44f)

        statCard(0, content.duration, "주행 시간", 30f, 288f, "c94f4.svg", 45f, 45f, 18f, 28f)
        statCard(1, if (content.demo) "23km" else wholeKilometers(content.record?.distanceMeters),
            "주행 거리", 330f, 287f, "b2719.svg", 42f, 42.38f, 23f, 29f)
        statCard(2, "${content.trip.people}명", "함께 한 인원", 629f, 289f,
            "afcdf.svg", 44.24f, 32.25f, 19f, 34f)

        place(box("c1.places.panel", Color.WHITE, 20.865f, 1f, 0xFFC3C3C3.toInt()),
            942f, 33.52f, 722f, 1102f)
        place(box("c1.places.handle", 0xFFE0E0E0.toInt(), 3f), 1236.79f, 53.73f, 132f, 6f)
        place(label("c1.places.title", "우리가 남긴 발자취", 40f, 700).apply {
            isAccessibilityHeading = true
            tooltipText = "길게 눌러 여행 결과 메뉴"
            setOnLongClickListener {
                openMenu(this, home, costs, editPlaces, card)
                true
            }
        }, 980.45f, 95.23f, 323f, 58f)
        place(placeCount.apply { background = shape(0xFFEDEEF2.toInt(), 8f) },
            1301f, 109f, 116f, 32f)
        place(asset("v5/8fc0c.svg", "c1.places.refresh.icon"), 1455f, 115f, 28f, 28f)
        place(button("c1.places.refresh", "", ::refreshDetails).apply {
            contentDescription = "발자취 최신 정보 업데이트"
        }, 1440f, 100f, 58f, 58f)
        place(button("c1.home", "홈으로", home, size = 22f).apply {
            background = shape(Color.WHITE, 12f, 1f, 0xFFE0E0E0.toInt())
            contentDescription = "홈 화면으로 돌아가기"
        }, 1510f, 103f, 114f, 52f)

        place(dayTabs, 978f, 191f, TripDayTabs.WIDTH, TripDayTabs.HEIGHT)

        place(timeline, 978f, TIMELINE_TOP, C1Timeline.DESIGN_WIDTH, timelineHeight)
        if (pending > 0) place(reviewButton(pending, editPlaces), 980f, reviewTop, 647f, REVIEW_HEIGHT)

        place(button("c1.replay", "▶ 크레딧 다시보기", replay, solid = true, size = 28f, astaSans = true).apply {
            background = shape(0xFF0064FF.toInt(), 16f)
        }, 978f, FOOTER_TOP, 315f, 92f)
        place(button("c1.costs", "이동비 확인하기", costs, size = 28f, astaSans = true).apply {
            background = shape(Color.WHITE, 16f, 1f, 0xFF131417.toInt())
            setTextColor(0xFF131417.toInt())
        }, 1306f, FOOTER_TOP, 315f, 92f)
        for (index in 1 until childCount) collapsedViews.add(getChildAt(index))

        expandControl = mapControl("c1.map.expand", "지도 크게 보기", "dc527.svg", ::toggleMap)
        zoomIn = mapControl("c1.map.zoom.in", "지도 확대", "18b91.svg", { zoom(1) }, true)
        zoomOut = mapControl("c1.map.zoom.out", "지도 축소", "bebad.svg", { zoom(-1) }, true)
        recenter = mapControl("c1.map.recenter", "전체 경로 보기", "8eaf4.svg", ::fitMap)
        layoutMapControls()
        showDay(selectedDay.coerceIn(0, timeline.dayCount))
    }

    private fun statCard(index: Int, value: String, caption: String, x: Float, width: Float,
        icon: String, iconWidth: Float, iconHeight: Float, iconX: Float, iconY: Float) {
        val y = 937.52f
        place(box("c1.stats.card.$index", Color.WHITE, 24f, 1f, 0xFFC3C3C3.toInt()), x, y, width, 200f)
        place(asset("v5/$icon", "c1.stat.icon.$index"), x + iconX, y + iconY, iconWidth, iconHeight)
        place(label("c1.stat.$index", value, 28f, 700), x + 20f, y + 99f, width - 40f, 41f)
        place(label("c1.stat.label.$index", caption, 22f, 500, 0xFFA1A1B3.toInt()),
            x + 20f, y + 140f, width - 40f, 32f)
    }

    private fun mapControl(tag: String, title: String, icon: String, click: () -> Unit, fullIcon: Boolean = false): View =
        object : ResultDesignSurface(context, 88f, 88f) {
            init {
                if (fullIcon) place(asset("v5/$icon", "$tag.icon"), -24f, -18f, 136f, 136f)
                else place(asset("v5/$icon", "$tag.icon"), 24f, 24f, 40f, 40f)
                place(button(tag, "", click).apply { contentDescription = title }, 0f, 0f, 88f, 88f)
            }
            override fun onDraw(canvas: Canvas) = Unit
        }.apply {
            background = shape(Color.WHITE, 16f, 1f, 0xFFC3C3C3.toInt())
            elevation = 2f * resources.displayMetrics.density
            clipChildren = false
        }

    private fun layoutMapControls() {
        val x = if (expanded) 1560f else 830f
        val controls = listOf(expandControl to 45.52f, zoomIn to 446f, zoomOut to 546f, recenter to 646f)
        for ((control, top) in controls) {
            if (control.parent == null) place(control, x, top, 88f, 88f)
            else moveSlot(control, x, top, 88f, 88f)
        }
    }

    private fun refreshDetails() {
        lookups = 0
        placeHandler.removeCallbacks(refreshPlaceNames)
        placeHandler.post(refreshPlaceNames)
    }

    private fun fitMap() {
        mapArt?.fit()
        liveMap?.fitResult(expanded)
    }

    private fun selectDay(day: Int) {
        showDay(day)
        onDay(day)
        fitMap()
    }

    private fun showDay(day: Int) {
        timeline.selectDate(day)
        val caption = if (day == 0) "전체" else "${day}일차"
        placeCount.text = "($caption ${timeline.visiblePlaceCount}곳)"
        mapArt?.selectDay(day)
        content.record?.let(::updateMap)
    }

    private fun updateMap(record: TripRecording) {
        val selected = timeline.selectedDate?.let { date ->
            val points = record.points.filter { travelLocalDate(it.at, timeline.zone) == date }
            record.copy(
                points = points,
                latest = record.latest?.takeIf { travelLocalDate(it.at, timeline.zone) == date }
                    ?: points.lastOrNull(),
                stops = record.stops.filter { travelLocalDate(it.at, timeline.zone) == date },
            )
        } ?: record
        liveMap?.update(selected, selected.visibleStops.associate { it.id to PlaceDisplayNames.display(it) }, true)
    }

    private fun wholeKilometers(meters: Double?): String =
        meters?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { "${(it / 1000).toLong()}km" } ?: "기록 없음"


    private fun reviewButton(pending: Int, editPlaces: () -> Unit): View =
        object : ResultDesignSurface(context, 647f, 54f) {
            init {
                val title = "확인이 필요한 곳 ${pending}곳"
                place(box("c1.places.review.icon.background", 0xFFF0F0F0.toInt(), 10f), 10f, 5f, 44f, 44f)
                place(asset("27fad.svg", "c1.places.review.icon"), 16f, 11f, 32f, 32f)
                place(label("c1.places.review.label", title, 24f, 700, 0xFF44464E.toInt(),
                    typeface = AppTypography.font(context, 700)), 66f, 12f, 535f, 29f)
                place(asset("v4/5782c.svg", "c1.places.review.chevron"), 611f, 15f, 37.5f, 24f)
                place(button("c1.places.review", "", editPlaces).apply {
                    contentDescription = title
                }, 0f, 0f, 647f, 54f)
            }
            override fun onDraw(canvas: Canvas) = Unit
        }.apply {
            background = shape(0xFFE6F0FF.toInt(), 14f)
        }

    private fun openMenu(anchor: View, home: () -> Unit, costs: () -> Unit, editPlaces: () -> Unit, card: () -> Unit) {
        val actions = listOf("장소 수정하기" to editPlaces, "이동비 내역 · 인원 수정" to costs,
            "여행 카드 미리보기" to card, "홈으로 돌아가기" to home)
        menu = PopupMenu(context, anchor).apply {
            actions.forEachIndexed { index, (label, _) -> menu.add(0, index, index, label) }
            setOnMenuItemClickListener { actions[it.itemId].second(); true }
            show()
        }
    }

    private fun zoom(direction: Int) {
        liveMap?.zoomResultBy(direction)
        mapArt?.zoomBy(direction)
    }

    private fun toggleMap() {
        expanded = !expanded
        collapsedViews.forEach { it.visibility = if (expanded) INVISIBLE else VISIBLE }
        layoutMapControls()
        mapArt?.setExpanded(expanded)
        fitMap()
        findViewWithTag<View>("c1.map.expand").contentDescription = if (expanded) "지도 원래 크기로 보기" else "지도 크게 보기"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        liveMap?.connect()
        placeHandler.post(refreshPlaceNames)
    }

    override fun onDetachedFromWindow() {
        menu?.dismiss()
        placeHandler.removeCallbacksAndMessages(null)
        liveMap?.close()
        super.onDetachedFromWindow()
    }

    fun onHostStarted() {
        liveMap?.resume()
        placeHandler.removeCallbacks(refreshPlaceNames)
        placeHandler.post(refreshPlaceNames)
    }

    fun onHostStopped() {
        dayTabs.dismissMenu()
        menu?.dismiss()
        placeHandler.removeCallbacksAndMessages(null)
        liveMap?.pause()
    }

    private companion object {
        const val TIMELINE_TOP = 299f
        const val REVIEW_HEIGHT = 54f
        const val FOOTER_GAP = 20f
        const val FOOTER_TOP = 1009f
    }
}
