package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.Canvas
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** A3-1 map and timeline layout for a completed trip. */
internal class F4CatalogDetailView(
    context: Context,
    private val content: TripResultContent,
    selectedDay: Int,
    private val onDay: (Int) -> Unit,
    onClose: () -> Unit,
    private val onEdit: () -> Unit,
    onReplay: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onShare: (() -> Unit)?,
    onCosts: () -> Unit = {},
) : CatalogWindow(context) {
    private val dateZone = tripDisplayZone(content.trip)
    private val record = content.record
    private val start = record?.startedAt?.let { travelLocalDate(it, dateZone) } ?: parseTripDate(content.trip.createdOn)
    private val end = record?.endedAt?.let { travelLocalDate(it, dateZone) } ?: if (content.demo) start?.plusDays(1) else start
    private val days = if (start != null && end != null) ChronoUnit.DAYS.between(start, end)
        .plus(1).coerceIn(1, Int.MAX_VALUE.toLong()).toInt() else 1
    private var day = selectedDay.coerceIn(0, days)
    private var expanded = false
    private val storedStops = record?.visibleStops.orEmpty().associateBy { it.id }
    private val addresses = mutableMapOf<String, String>()
    private val addressRequests = mutableSetOf<String>()
    private var addressRetries = 0
    private val map = record?.takeIf { it.points.isNotEmpty() || it.visibleStops.any { stop -> stop.point != null } }
        ?.let { KakaoTripMapView(context) { _, _ -> }.apply {
            tag = "f4.map.live"
            detailPresentation = true
        } }
    private val sampleMap = if (content.demo) ReferenceDetailMap(context) else null
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = TripTimelineScroll(context, 642f).apply {
        tag = "f4.places"
        isVerticalScrollBarEnabled = true
        addView(rows, FrameLayout.LayoutParams(-1, -2))
    }
    private val titleDate = label("f4.date", "", 30f, color = 0xB3FFFFFF.toInt(), typeface = AppTypography.font(context))
    private val places = C1Places.entries(content)
    private val count = label("f4.places.count", "", 20f, color = 0xFF44464E.toInt(),
        centered = true, typeface = AppTypography.font(context))
    private val dayTabs = TripDayTabs(context, "f4", days, day, { index ->
        start?.plusDays((index - 1).toLong())?.format(DATE).orEmpty()
    }, ::selectDay)
    private val stats = listOf(
        label("f4.stat.0.value", "", 28f, 700),
        label("f4.stat.1.value", "", 28f, 700),
        label("f4.stat.3.value", "${content.trip.people}명", 28f, 700),
    )
    private val panelViews = mutableListOf<View>()
    private val summaryViews = mutableListOf<View>()
    private var menu: PopupMenu? = null
    private var renameDialog: AlertDialog? = null
    private var lastScale = 0f
    private val loadAddresses = object : Runnable {
        override fun run() {
            val tripMap = map ?: return
            val pending = selectedPlaces().mapNotNull { storedStops[it.id] }.filter {
                it.point?.valid() == true && it.id !in addressRequests && storedAddress(it).isBlank()
            }
            pending.forEach { stop ->
                if (tripMap.lookupAddress(stop) { address ->
                    if (address.isNotBlank()) {
                        addresses[stop.id] = address
                        scroll.findViewWithTag<TextView>("f4.address.${stop.id}")?.text = address
                    }
                }) addressRequests += stop.id
            }
            if (++addressRetries < 20 && pending.any { it.id !in addressRequests }) postDelayed(this, 1_000L)
        }
    }

    init {
        tag = "f4.detail"
        val mapSurface = FrameLayout(context).apply {
            tag = "f4.map"
            setBackgroundColor(0xFFF0F3F8.toInt())
            addView(map ?: sampleMap ?: TextView(context).apply {
                text = "남겨진 경로가 없어요"
                gravity = android.view.Gravity.CENTER
                setTextColor(muted)
            }, FrameLayout.LayoutParams(-1, -1))
        }
        place(mapSurface, 0f, 0f, 1696f, 1180f)
        place(box("f4.title.card", INK, 24f, 1f, BORDER), 30f, 39f, 544f, 192f)
        place(label("f4.title", content.trip.name, 40f, 700, Color.WHITE, typeface = AppTypography.font(context, 700)).apply {
            isAccessibilityHeading = true
            tooltipText = "길게 눌러 여행 이름 수정"
            setOnLongClickListener { rename(onRename); true }
        }, 69f, 78f, 464f, 52f)
        place(titleDate, 69f, 145f, 464f, 38f)

        val expand = iconButton("f4.map.expand", "지도 펼치기", "expand.svg", 830f, 40f, 40f, 40f) {
            expanded = !expanded
            panelViews.forEach { it.visibility = if (expanded) INVISIBLE else VISIBLE }
            summaryViews.forEach { it.visibility = if (expanded) INVISIBLE else VISIBLE }
            map?.setDetailExpanded(expanded)
            sampleMap?.setExpanded(expanded)
        }
        expand.isEnabled = map != null || sampleMap != null
        for ((index, data) in listOf(Triple("in", "지도 확대", "zoom-in.svg"),
            Triple("out", "지도 축소", "zoom-out.svg")).withIndex()) {
            val y = 446f + 100f * index
            place(asset(data.third, "f4.zoom.${data.first}.icon", "f4_834"), 806f, y - 18f, 136f, 136f)
            place(button("f4.zoom." + data.first, "", {
                val step = if (index == 0) 1 else -1
                map?.zoomResultBy(step)
                sampleMap?.zoomBy(step)
            })
                .apply { contentDescription = data.second; isEnabled = map != null || sampleMap != null }, 830f, y, 88f, 88f)
        }
        iconButton("f4.map.fit", "여행 경로 전체 보기", "fit.svg", 830f, 646f, 40f, 40f) {
            map?.setDetailExpanded(expanded)
            sampleMap?.fit()
        }.isEnabled = map != null || sampleMap != null

        val captions = listOf("주행 시간", "주행 거리", "함께 한 인원")
        val icons = listOf("time.svg", "distance.svg", "people.svg")
        val iconSizes = listOf(51.4286f to 51.4286f, 48f to 48f, 50.5717f to 36.5804f)
        captions.forEachIndexed { index, caption ->
            val x = 30f + 300f * index
            summaryViews += place(box("f4.stats.$index", Color.WHITE, 24f, 1f, BORDER), x, 938f, 288f, 200f)
            val (width, height) = iconSizes[index]
            summaryViews += place(asset(icons[index], "f4.stat.$index.icon", "f4_834"),
                x + 17f, 996f - height / 2, width, height)
            summaryViews += place(stats[index], x + 20f, 1037f, 248f, 41f)
            summaryViews += place(label("f4.stat.$index.caption", caption, 22f, 500, 0xFFA1A1B3.toInt()),
                x + 20f, 1078f, 248f, 32f)
        }

        panel(box("f4.panel", Color.WHITE, 24f, 1f, BORDER), 948f, 40f, 722f, 1104f)
        panel(label("f4.places.title", "우리가 남긴 발자취", 40f, 700, INK,
            typeface = AppTypography.font(context, 700)).apply {
            isAccessibilityHeading = true
            tooltipText = "길게 눌러 여행 관리"
            setOnLongClickListener {
                showMenu(it, onEdit, onRename, onDelete, onShare)
                true
            }
        }, 987f, 103f, 300f, 52f)
        panel(count.apply { background = shape(0xFFEDEEF2.toInt(), 8f) }, 1294f, 113f, 120f, 32f)
        panel(button("f4.back", "×", onClose, size = 36f).apply {
            contentDescription = "여행 상세 닫기"
        }, 1574f, 97f, 64f, 64f)
        panel(dayTabs, 987f, 191f, TripDayTabs.WIDTH, TripDayTabs.HEIGHT)
        panel(scroll, 987f, TIMELINE_TOP, 642f, TIMELINE_HEIGHT)
        panel(button("f4.replay", "▶ 크레딧 다시보기", onReplay, solid = true, size = 28f, astaSans = true).apply {
            background = shape(BLUE, 16f)
        }, 987f, FOOTER_TOP, 315f, 92f)
        panel(button("f4.costs", "이동비 확인하기", onCosts, size = 28f, astaSans = true).apply {
            background = shape(Color.WHITE, 16f, 1f, INK)
            setTextColor(INK)
        }, 1315f, FOOTER_TOP, 315f, 92f)

        refresh()
    }

    private fun panel(view: View, x: Float, y: Float, w: Float, h: Float) {
        panelViews += place(view, x, y, w, h)
    }

    private fun iconButton(tag: String, description: String, icon: String, x: Float, y: Float,
        width: Float, height: Float, action: () -> Unit): View {
        place(box("$tag.background", Color.WHITE, 16f, 1f, BORDER), x, y, 88f, 88f)
        place(asset(icon, "$tag.icon", "f4_834"), x + (88f - width) / 2f, y + (88f - height) / 2f, width, height)
        return place(button(tag, "", action).apply { contentDescription = description }, x, y, 88f, 88f)
    }

    private fun showMenu(anchor: View, edit: () -> Unit, saveName: (String) -> Unit, delete: () -> Unit, share: (() -> Unit)?) {
        menu?.dismiss()
        menu = PopupMenu(context, anchor).apply {
            menu.add("다녀온 곳 수정").setOnMenuItemClickListener { edit(); true }
            menu.add("총 주행 정보").setOnMenuItemClickListener { showDrivingSummary(); true }
            menu.add("여행 이름 수정").setOnMenuItemClickListener { rename(saveName); true }
            share?.let { action -> menu.add("여행 요약 QR").setOnMenuItemClickListener { action(); true } }
            menu.add("여행 지우기").setOnMenuItemClickListener { delete(); true }
            show()
        }
    }

    private fun rename(save: (String) -> Unit) {
        val input = EditText(context).apply {
            setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(60))
            setText(content.trip.name)
            selectAll()
        }
        renameDialog?.dismiss()
        renameDialog = AlertDialog.Builder(context).setTitle("여행 이름 수정").setView(input)
            .setNegativeButton("취소", null).setPositiveButton("저장") { _, _ ->
                input.text.toString().trim().takeIf(String::isNotEmpty)?.let(save)
            }.show()
    }

    private fun selectDay(value: Int) {
        day = value
        onDay(value)
        refresh()
        loadSelectedAddresses()
    }

    private fun selectedDate() = if (day == 0) null else start?.plusDays((day - 1).toLong())

    private fun selectedPlaces(): List<ResultPlace> {
        val chosen = selectedDate()
        if (content.demo && content.trip.resultPlaces == null) {
            return when (day) {
                1 -> places.take(4)
                2 -> places.drop(4)
                else -> places
            }
        }
        return places.filter { place ->
            if (chosen == null) true
            else if (place.id in storedStops) travelLocalDate(storedStops.getValue(place.id).at, dateZone) == chosen
            else if (start == chosen) !place.time.startsWith("다음날") && !place.time.startsWith("2일차")
            else day == 2 && (place.time.startsWith("다음날") || place.time.startsWith("2일차"))
        }
    }

    private fun refresh() {
        val date = selectedDate()
        val dateText = (date ?: start)?.format(DATE).orEmpty()
        titleDate.text = when {
            date != null -> "${day}일차 · $dateText"
            start != end && end != null -> "$dateText – ${end.format(DATE)}"
            else -> dateText
        }
        val visiblePlaces = selectedPlaces()
        val number = visiblePlaces.size
        count.text = if (day == 0) "(전체 ${number}곳)" else "(${day}일차 ${number}곳)"
        setTag(R.id.ending_credit_screen_id, if (day == 1) "F4-1" else "F4")
        val selected = record?.let { trip ->
            if (date == null) trip else trip.copy(
                points = trip.points.filter { travelLocalDate(it.at, dateZone) == date },
                latest = trip.latest?.takeIf { travelLocalDate(it.at, dateZone) == date },
                stops = trip.stops.filter { travelLocalDate(it.at, dateZone) == date },
            )
        }
        if (selected != null) {
            map?.update(selected, places.associate { it.id to it.name }, true)
            map?.setDetailExpanded(expanded)
        }
        val dailySamples = selected?.points?.filter(TripPoint::valid).orEmpty()
        val dailySegments = dailySamples.zipWithNext().filter { (from, to) ->
            !to.gapBefore && to.at - from.at in 1L..30_000L
        }
        val distance = when {
            date == null && record != null -> record.distanceMeters
            content.demo -> TripSample.distanceKm * 1_000.0
            dailySegments.isNotEmpty() -> dailySegments.sumOf { (from, to) -> metersBetween(from, to) }
            else -> null
        }
        stats[0].text = when {
            content.demo -> TripSample.duration
            date == null -> content.duration
            else -> "기록 없음"
        }
        stats[1].text = distance?.takeIf { it.isFinite() && it >= 0 }
            ?.let { "${(it / 1_000).toLong()}km" } ?: "기록 없음"
        sampleMap?.selectDay(day)
        populateRows(visiblePlaces)
    }

    private fun storedAddress(stop: RecordedStop): String =
        PlaceDisplayNames.getRoadAddress(stop.selectedPlaceId).orEmpty().ifBlank {
            PlaceDisplayNames.getAddress(stop.selectedPlaceId).orEmpty()
        }

    private fun populateRows(places: List<ResultPlace> = selectedPlaces()) {
        rows.removeAllViews()
        scroll.scrollTo(0, 0)
        if (places.isEmpty()) {
            addRow(EMPTY_HEIGHT) {
                put(text("f4.places.empty", "기록된 장소가 없어요", 26f, color = muted), 0f, 0f, 620f, 70f)
            }
            return
        }
        val contentHeight = places.lastIndex * ROW_HEIGHT + LAST_ROW_HEIGHT
        val rowHeight = ROW_HEIGHT + timelineRowGap(TIMELINE_HEIGHT, contentHeight, places.size)
        places.forEachIndexed { index, place ->
            val stop = storedStops[place.id]
            val time = stop?.at?.let { java.time.Instant.ofEpochMilli(it)
                .atZone(dateZone).format(TIME) } ?: place.time
            val address = addresses[place.id] ?: C1Places.address(place, record)
            val isLast = index == places.lastIndex
            addRow(if (isLast) LAST_ROW_HEIGHT else rowHeight, index * rowHeight) {
                if (index > 0) put(line(), 34.5f, 0f, 3f, 36f)
                if (!isLast) put(line(), 34.5f, 36f, 3f, rowHeight - 36f)
                put(number(index + 1), 0f, 0f, 72f, 72f)
                val name = if (place.needsReview) "${place.name} · 확인 필요" else place.name
                put(text("f4.place.${place.id}", name, 30f, 700), 96f, 0f,
                    if (time.isBlank()) 502f else 350f, 38f)
                put(text("f4.address.${place.id}", address, 24f, color = 0xFF686A72.toInt()), 96f, 42f, 502f, 28f)
                if (time.isNotBlank()) put(text("f4.place.time", time, 24f, color = 0xFF686A72.toInt()).apply {
                    gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                }, 451f, 4f, 147f, 28f)
            }
        }
    }

    private fun showDrivingSummary() {
        renameDialog?.dismiss()
        renameDialog = AlertDialog.Builder(context).setTitle("총 주행 정보")
            .setMessage("주행 거리  ${stats[1].text}\n주행 시간  ${stats[0].text}\n밤 구간  ${if (day == 0) content.night else "기록 없음"}\n함께 한 인원  ${stats[2].text}")
            .setPositiveButton("닫기", null).show()
    }

    private fun addRow(height: Float, top: Float = 0f, init: DetailRow.() -> Unit) {
        val row = DetailRow(context, height).apply(init)
        val pixels = ((top + height) * drawingScale).roundToInt() - (top * drawingScale).roundToInt()
        rows.addView(row, LinearLayout.LayoutParams(-1, pixels.coerceAtLeast(1)))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (lastScale != drawingScale) {
            lastScale = drawingScale
            populateRows()
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        map?.connect()
        loadSelectedAddresses()
    }

    private fun loadSelectedAddresses() {
        removeCallbacks(loadAddresses)
        addressRetries = 0
        if (isAttachedToWindow && map != null) post(loadAddresses)
    }

    override fun onDetachedFromWindow() {
        onHostStopped()
        map?.close()
        super.onDetachedFromWindow()
    }

    fun onHostStarted() { map?.resume(); loadSelectedAddresses() }
    fun onHostStopped() {
        removeCallbacks(loadAddresses)
        dayTabs.dismissMenu()
        menu?.dismiss()
        renameDialog?.dismiss()
        map?.pause()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saved = canvas.save()
        val clip = Path().apply { addRoundRect(surface, 24f * drawingScale, 24f * drawingScale, Path.Direction.CW) }
        canvas.clipPath(clip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
    }

    private class DetailRow(context: Context, height: Float) : ResultDesignSurface(context, 642f, height) {
        fun put(view: View, x: Float, y: Float, w: Float, h: Float) = place(view, x, y, w, h)
        fun text(id: String, value: String, size: Float, weight: Int = 400, color: Int = ink) =
            label(id, value, size, weight, color, typeface = AppTypography.font(context, weight))
        fun number(index: Int) = label("f4.place.number", index.toString(), 28f, 700, Color.WHITE,
            centered = true, typeface = AppTypography.font(context, 700))
            .apply { background = shape(BLUE, 36f); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        fun line() = box("f4.place.line", BLUE)
        override fun onDraw(canvas: Canvas) = Unit
    }

    private companion object {
        const val TIMELINE_TOP = 315f
        const val EMPTY_HEIGHT = 85f
        const val ROW_HEIGHT = 128f
        const val LAST_ROW_HEIGHT = 90f
        const val FOOTER_GAP = 20f
        const val FOOTER_TOP = 1011f
        const val TIMELINE_HEIGHT = FOOTER_TOP - FOOTER_GAP - TIMELINE_TOP
        const val BLUE = 0xFF0064FF.toInt()
        const val INK = 0xFF131417.toInt()
        const val BORDER = 0xFFE0E1E6.toInt()
        val DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd(E)", Locale.KOREA)
        val TIME = DateTimeFormatter.ofPattern("a hh:mm", Locale.KOREA)
    }
}

