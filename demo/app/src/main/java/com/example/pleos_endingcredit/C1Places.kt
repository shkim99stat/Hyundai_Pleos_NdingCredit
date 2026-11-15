package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal object C1Places {
    private data class Example(val name: String, val address: String, val time: String = "")

    private val examples = linkedMapOf(
        "demo-rest" to Example("횡성휴게소(강릉방향)", "강원특별자치도 횡성군 안흥면 영동고속도로 154-1", "오전 10:20"),
        "demo-market" to Example("중앙시장", "강원특별자치도 강릉시 금성로 21", "오전 11:53"),
        "demo-cafe" to Example("강릉커피거리", "강원특별자치도 강릉시 창해로14번길 20-1", "오전 10:20"),
        "demo-restaurant" to Example("초당순두부마을", "강원특별자치도 강릉시 초당순두부길 89-8", "오전 10:20"),
        "demo-beach" to Example("경포해변", "강원특별자치도 강릉시 창해로 514", "다음날 09:30"),
    )

    fun entries(content: TripResultContent): List<ResultPlace> {
        if (!content.demo || content.trip.resultPlaces != null) return content.places
        val byId = content.places.associateBy { it.id }
        val visibleExamples = examples.mapNotNull { (id, example) ->
            byId[id]?.copy(name = example.name, time = example.time)
        }
        return visibleExamples + content.places.filterNot { it.id in examples }.map {
            if (it.id == "demo-stay") it.copy(needsReview = true) else it
        }
    }

    fun address(place: ResultPlace, record: TripRecording?): String {
        val example = examples[place.id]
        if (example != null && example.name == place.name) return example.address
        val stop = record?.visibleStops?.firstOrNull { it.id == place.id } ?: return ""
        return recordedAddress(record.tripId, stop).orEmpty()
    }

    fun recordedAddress(tripId: String, stop: RecordedStop, coordinateAddress: String? = null): String? {
        if (DemoScenario.isDemo(tripId)) {
            return DemoScenario.indexOfStop(tripId, stop.id)?.let { index ->
                DemoScenario.legsFor(tripId)[index].address.takeIf(String::isNotBlank)
            }
        }
        return PlaceDisplayNames.getRoadAddress(stop.selectedPlaceId)?.takeIf(String::isNotBlank)
            ?: coordinateAddress?.takeIf(String::isNotBlank)
            ?: PlaceDisplayNames.getAddress(stop.selectedPlaceId)?.takeIf(String::isNotBlank)
    }
}

internal open class TripTimelineScroll(
    context: Context,
    private val designWidth: Float,
    fadeBottom: Boolean = true,
) : ScrollView(context) {
    init {
        isVerticalFadingEdgeEnabled = fadeBottom
    }

    override fun getTopFadingEdgeStrength() = 0f
    override fun getSolidColor() = if (isVerticalFadingEdgeEnabled) Color.WHITE else super.getSolidColor()

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (isVerticalFadingEdgeEnabled) setFadingEdgeLength((112f * width / designWidth).roundToInt())
    }
}

/** Scrollable content with the short thumb drawn in the Figma track. */
internal open class C1PlaceScroll(context: Context, private val designWidth: Float,
    private val thumbWidth: Float, private val thumbHeight: Float, fadeBottom: Boolean = false,
) : TripTimelineScroll(context, designWidth, fadeBottom) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9D9D9.toInt() }

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        val child = getChildAt(0) ?: return
        val range = child.height - height
        if (range <= 0) return
        val scale = width / designWidth
        val barHeight = thumbHeight * scale
        val top = scrollY + scrollY.toFloat() / range * (height - barHeight)
        val barWidth = thumbWidth * scale
        canvas.drawRoundRect(width - barWidth, top, width.toFloat(), top + barHeight,
            barWidth / 2, barWidth / 2, paint)
    }
}

internal fun timelineRowGap(viewportHeight: Float, contentHeight: Float, rowCount: Int): Float {
    if (rowCount < 2 || contentHeight >= viewportHeight) return 0f
    val gap = (viewportHeight - contentHeight) / (rowCount - 1)
    return if (gap <= 72f) gap else 0f
}

internal class C1Timeline(
    context: Context,
    content: TripResultContent,
    private val viewportHeight: Float,
    remove: (String) -> Unit,
) :
    C1PlaceScroll(context, DESIGN_WIDTH, 8f, 62f, fadeBottom = true) {
    private val places = C1Places.entries(content)
    private val record = content.record
    private val names = mutableMapOf<String, ReferenceDesignLabel>()
    private val addresses = mutableMapOf<String, ReferenceDesignLabel>()
    private val coordinateAddresses = mutableMapOf<String, String>()
    private var openRow: C1SwipeRow? = null
    private var downX = 0f
    private var downY = 0f

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd(E)", Locale.KOREA)
    private val timeFormat = DateTimeFormatter.ofPattern("a hh:mm", Locale.KOREA)
    val zone = tripDisplayZone(content.trip)
    private val visitTimes = record?.visibleStops.orEmpty().associate { it.id to it.at }
    private val startDate = record?.startedAt?.let { travelLocalDate(it, zone) }
        ?: parseTripDate(content.trip.createdOn)
    private val placeDates = places.map { place ->
        visitTimes[place.id]?.let { travelLocalDate(it, zone) }
            ?: startDate?.plusDays(if (place.time.startsWith("다음날")) 1 else 0)
    }
    private val endDate = listOfNotNull(
        record?.endedAt?.let { travelLocalDate(it, zone) },
        placeDates.filterNotNull().maxOrNull(),
        if (content.demo) startDate?.plusDays(1) else startDate,
    ).maxOrNull()
    val dayCount = if (startDate != null && endDate != null) ChronoUnit.DAYS.between(startDate, endDate)
        .plus(1L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt() else 1
    var selectedDate: LocalDate? = null
        private set
    var visiblePlaceCount = 0
        private set

    fun dayLabel(index: Int) = dateAt(index)?.format(dateFormat).orEmpty()
    private fun dateAt(index: Int) = if (index in 1..dayCount) startDate?.plusDays(index - 1L) else null
    private val rows = places.mapIndexed { index, stop ->
        C1SwipeRow(context, stop, placeRow(context, stop, index), { revealed ->
            if (openRow !== revealed) openRow?.close()
            openRow = revealed
        }, remove)
    }

    init {
        tag = "c1.places.timeline"
        showRows()
    }

    fun selectDate(index: Int) {
        val next = dateAt(index)
        if (next == selectedDate) {
            scrollTo(0, 0)
            return
        }
        selectedDate = next
        showRows()
    }

    private fun showRows() {
        openRow?.close()
        openRow = null
        val displayed = rows.indices.filter { selectedDate == null || placeDates[it] == selectedDate }
        visiblePlaceCount = displayed.size
        val naturalHeight = if (displayed.isEmpty()) 80f else displayed.size * ROW_HEIGHT
        val spacing = timelineRowGap(viewportHeight, naturalHeight, displayed.size)
        val contentHeight = naturalHeight + (displayed.size - 1).coerceAtLeast(0) * spacing
        val surface = object : ResultDesignSurface(context, DESIGN_WIDTH, contentHeight) {
            init {
                if (displayed.isEmpty()) {
                    place(label("c1.places.empty", "기록된 장소가 없어요", color = muted), 10f, 15f, 610f, 42f)
                }
                displayed.forEachIndexed { position, index ->
                    val row = rows[index]
                    (row.parent as? ViewGroup)?.removeView(row)
                    row.findViewWithTag<ReferenceDesignLabel>("c1.place.marker.$index").text = (position + 1).toString()
                    row.findViewWithTag<View>("c1.place.connector.top.$index").visibility =
                        if (position == 0) INVISIBLE else VISIBLE
                    row.findViewWithTag<View>("c1.place.connector.bottom.$index").visibility =
                        if (position == displayed.lastIndex) INVISIBLE else VISIBLE
                    val rowTop = position * (ROW_HEIGHT + spacing)
                    place(row, 0f, rowTop, C1SwipeRow.DESIGN_WIDTH, ROW_HEIGHT)
                    if (position < displayed.lastIndex && spacing > 0f) {
                        place(box("c1.place.connector.gap.$index", 0xFF0066FF.toInt()),
                            34f, rowTop + ROW_HEIGHT, 4f, spacing)
                    }
                }
            }
            override fun onDraw(canvas: Canvas) = Unit
        }
        removeAllViews()
        addView(surface, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scrollTo(0, 0)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > abs(event.y - downY)) return false
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    private fun placeRow(context: Context, stop: ResultPlace, index: Int) =
        object : ResultDesignSurface(context, C1SwipeRow.DESIGN_WIDTH, ROW_HEIGHT) {
            init {
                val number = (index + 1).toString()
                place(box("c1.place.connector.top.$index", 0xFF0066FF.toInt()), 34f, 0f, 4f, 4f)
                place(box("c1.place.connector.bottom.$index", 0xFF0066FF.toInt()), 34f, 76f, 4f, ROW_HEIGHT - 76f)
                place(label("c1.place.marker.$index", number, if (number.length > 2) 22f else 28f,
                    color = Color.WHITE, centered = true).apply {
                    background = shape(0xFF0066FF.toInt(), 36f)
                }, 0f, 4f, 72f, 72f)
                place(label("c1.place.name.$index", stop.name, 30f, 600,
                    typeface = AppTypography.font(context, 600)).also { names[stop.id] = it },
                    96f, 1f, 525f, 38f)
                place(label("c1.place.address.$index", C1Places.address(stop, record),
                    22f, 500, 0xFF8F8F8F.toInt()).also { addresses[stop.id] = it }, 96f, 45f, 525f, 30f)
                val time = visitTimes[stop.id]?.let { Instant.ofEpochMilli(it).atZone(zone).format(timeFormat) }
                    ?: stop.time
                if (time.isNotBlank()) {
                    place(asset("v5/dec79.svg", "c1.place.time.icon.$index"), 96f, 89f, 22f, 22f)
                    place(label("c1.place.time.$index", time, 22f, 500), 128f, 84f, 310f, 32f)
                }
            }
            override fun onDraw(canvas: Canvas) = Unit
        }

    fun refreshPlaceDetails(record: TripRecording, updateNames: Boolean) {
        record.visibleStops.forEach { stop ->
            if (updateNames) names[stop.id]?.text = PlaceDisplayNames.display(stop)
            updateAddress(record.tripId, stop)
        }
    }

    fun showAddress(tripId: String, stop: RecordedStop, address: String) {
        if (address.isNotBlank()) coordinateAddresses[stop.id] = address
        updateAddress(tripId, stop)
    }

    private fun updateAddress(tripId: String, stop: RecordedStop) {
        val label = addresses[stop.id] ?: return
        label.text = C1Places.recordedAddress(tripId, stop, coordinateAddresses[stop.id]).orEmpty()
    }

    companion object {
        const val DESIGN_WIDTH = 650f
        private const val ROW_HEIGHT = 156f
    }
}
