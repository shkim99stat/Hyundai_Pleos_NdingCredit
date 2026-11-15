package com.example.pleos_endingcredit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.graphics.withClip
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Figma 406:14978, rendered inside the Pleos landscape window. */
@SuppressLint("ViewConstructor", "SetTextI18n")
internal class A2LegacyTravelHomeView(
    context: Context,
    trips: List<Trip>,
    onStart: () -> Unit = {},
    onArchive: () -> Unit = {},
    onReplay: (Trip) -> Unit = {},
) : ViewGroup(context) {
    private data class Placement(val view: View, val x: Float, val y: Float, val width: Float, val height: Float)
    private data class Typography(val view: TextView, val size: Float, val height: Float, val baseline: Float)
    private data class Shape(val drawable: GradientDrawable, val radius: Float, val stroke: Int?)

    private val roots = mutableListOf<Placement>()
    private val children = mutableListOf<Placement>()
    private val typography = mutableListOf<Typography>()
    private val shapes = mutableListOf<Shape>()
    private val surface = RectF()
    private val edge = RectF()
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD4D4D4.toInt()
        style = Paint.Style.STROKE
    }
    private val distanceShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DISTANCE_SURFACE
        setShadowLayer(20f, 0f, 12f, 0x1A000000)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
    private val regular = AppTypography.font(context, 400)
    private val bold = AppTypography.font(context, 700)
    private val extraBold = AppTypography.font(context, 800)
    private val notoBold = requireNotNull(ResourcesCompat.getFont(context, R.font.noto_sans_kr_bold))
    private var scale = 1f
    private val recordings = TripRecordingRepository.get(context).let { repository ->
        trips.associateWith { repository.load(it.id) }
    }
    // Prefer the current sample collection when both generations of bundled history are present.
    private val hasCurrentSamples = trips.any { DemoContent.homeIndex(it) != null }
    private val recent = trips.filter { trip ->
        !hasCurrentSamples || DemoContent.coverIndex(trip) == null || DemoContent.homeIndex(trip) != null
    }.sortedWith(compareByDescending<Trip> {
        recordings[it]?.endedAt?.let { at -> travelLocalDate(at, tripDisplayZone(it)) } ?: parseTripDate(it.createdOn) ?: LocalDate.MIN
    }.thenByDescending { recordings[it]?.endedAt ?: 0L }).take(4)
    private lateinit var featuredCard: FrameLayout
    private lateinit var featuredPhoto: ImageView
    private val firstPoster by lazy { travelImage(context, "a2-sokcho.png", 1100).drawable }
    private lateinit var featuredTitle: TextView
    private lateinit var featuredDate: TextView
    private lateinit var featuredAction: FrameLayout
    private lateinit var latestBadge: TextView
    private val progress = mutableListOf<View>()
    private var currentStory = 0
    private var remainingMillis = STORY_MILLIS
    private var storyDeadline = 0L
    private var storyPaused = false
    private val storyTick = object : Runnable {
        override fun run() {
            val left = (storyDeadline - SystemClock.uptimeMillis()).coerceAtLeast(0)
            if (left == 0L) {
                storyDeadline = 0L
                moveStory(1)
            } else {
                drawProgress(1f - left.toFloat() / STORY_MILLIS)
                postDelayed(this, minOf(50L, left))
            }
        }
    }

    init {
        tag = "home.a2"
        setWillNotDraw(false)
        val metrics = TravelHomeSummary.from(trips) { recordings[it] }

        add(label("home.title", "N딩 크레딧", 48f, 64f, TITLE_INK, extraBold).apply {
            isAccessibilityHeading = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }, 56f, 54f, 500f, 64f)
        addArchive(trips.size, onArchive)
        if (recent.isNotEmpty()) addFeatured(onReplay)
        addDistance(metrics)
        addDestination(metrics)
        addDuration(metrics)
        add(button("home.start", "여행 기록 시작하기", 0xFF0066FF.toInt(), Color.WHITE, action = onStart),
            665f, 1040f, 975f, 100f)
    }

    private fun addArchive(count: Int, onArchive: () -> Unit) {
        val countText = "${count}편"
        val control = actionPanel("home.archive", "전체 상영관·$countText",
            0xFFF1F1F1.toInt(), 0xFF484848.toInt(), onArchive)
        add(control, 1047f, 32.5f, 593f, 100f)
        val parts = listOf("전체 상영관", "·", countText)
        val widths = parts.map { widthOf(it, 30f, notoBold) }
        var x = (593f - widths.sum()) / 2f
        parts.forEachIndexed { index, text ->
            place(control, label("home.archive.label.$index", text, 30f, 36f, MUTED, notoBold),
                x, 30f, widths[index] + 2f, 36f)
            x += widths[index]
        }
    }

    private fun addFeatured(onReplay: (Trip) -> Unit) {
        featuredCard = panel("home.featured", 0xFF737373.toInt(), 24f)
        add(featuredCard, 56f, PANEL_TOP, 593f, 950f)
        featuredPhoto = ImageView(context).apply {
            tag = "home.featured.photo"
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        place(featuredCard, featuredPhoto, 0f, 0f, 593f, 937f)
        place(featuredCard, gradientView(GradientDrawable.Orientation.TOP_BOTTOM, Color.TRANSPARENT, Color.BLACK),
            0f, 601.135f, 593f, 347.206f)
        if (recent.size > 1) {
            val trackWidth = (561f - (recent.size - 1) * 6f) / recent.size
            recent.indices.forEach { index ->
                val x = 16f + index * (trackWidth + 6f)
                place(featuredCard, View(context).apply { background = shape(0x80FFFFFF.toInt(), 3f) },
                    x, 31f, trackWidth, 6f)
                progress += View(context).apply { background = shape(Color.WHITE, 3f); pivotX = 0f }.also {
                    place(featuredCard, it, x, 31f, trackWidth, 6f)
                }
            }
            val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onFling(start: MotionEvent?, end: MotionEvent, vx: Float, vy: Float): Boolean {
                    if (start == null) return false
                    val dx = end.x - start.x
                    if (abs(dx) < 36f * scale || abs(dx) < abs(end.y - start.y)) return false
                    moveStory(if (dx < 0) 1 else -1)
                    return true
                }
            })
            featuredCard.setOnTouchListener { _, event -> gestures.onTouchEvent(event) }
            ViewCompat.addAccessibilityAction(featuredCard, "이전 여행") { _, _ -> moveStory(-1); true }
            ViewCompat.addAccessibilityAction(featuredCard, "다음 여행") { _, _ -> moveStory(1); true }
        }

        latestBadge = label("home.featured.badge", "최신 상영작", 24f, 45f, 0xFFEDEEF2.toInt(), bold).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = shape(0x99000000.toInt(), 20f)
        }
        add(latestBadge, 56f, 131.48f, 141f, 45f)
        featuredTitle = label("home.featured.title", "", 40f, 48f, Color.WHITE, bold).apply {
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        place(featuredCard, featuredTitle, 39.5f, 674f, 521f, 48f)
        featuredDate = label("home.featured.date", "", 24f, 29f, 0xD9FFFFFF.toInt(), regular)
        place(featuredCard, featuredDate, 39.5f, 726f, 521f, 29f)
        featuredAction = actionPanel("home.replay", "", Color.WHITE) { onReplay(recent[currentStory]) }
        place(featuredCard, featuredAction, 39.5f, 789.603f, 521f, 100f)
        place(featuredAction, label("home.replay.label", "여행 상세 보기", 30f, 36f, Color.BLACK, notoBold),
            153f, 35f, 175f, 36f)
        place(featuredAction, travelSvg(context, "a2-detail.svg").apply { tag = "home.replay.icon" }, 331f, 35f, 28f, 36f)
        showStory()
    }

    private fun moveStory(offset: Int) {
        currentStory = Math.floorMod(currentStory + offset, recent.size)
        remainingMillis = STORY_MILLIS
        showStory()
        updateStoryTimer()
    }

    private fun showStory() {
        val trip = recent[currentStory]
        val poster = CatalogPhotos.customPoster(context, trip)
        when {
            poster != null -> featuredPhoto.setImageBitmap(poster)
            trip.id == DemoContent.homeTrips.first().id -> featuredPhoto.setImageDrawable(firstPoster)
            else -> featuredPhoto.setImageBitmap(CatalogPhotos.poster(context, trip))
        }
        featuredPhoto.alpha = 0.9f
        latestBadge.visibility = if (currentStory == 0) VISIBLE else INVISIBLE
        featuredTitle.text = trip.name
        val recording = recordings[trip]
        val zone = tripDisplayZone(trip)
        val start = recording?.startedAt?.let { travelLocalDate(it, zone) } ?: parseTripDate(trip.createdOn)
        val end = recording?.endedAt?.let { travelLocalDate(it, zone) }
            ?: start?.plusDays(if (trip.id == DemoContent.homeTrips.first().id) 1 else 0)
        val nights = if (start != null && end != null) ChronoUnit.DAYS.between(start, end).coerceAtLeast(0) else 0L
        val length = if (nights == 0L) "당일치기" else "${nights}박 ${nights + 1}일"
        featuredDate.text = "${start?.format(TRIP_DATE) ?: trip.createdOn} · $length"
        featuredAction.contentDescription = "여행 상세 보기, ${trip.name}"
        featuredCard.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        featuredCard.contentDescription = "${trip.name}, ${featuredDate.text}, ${currentStory + 1}/${recent.size}"
        drawProgress(0f)
    }

    private fun drawProgress(fraction: Float) {
        progress.forEachIndexed { index, view ->
            view.scaleX = when {
                index < currentStory -> 1f
                index == currentStory -> fraction
                else -> 0f
            }
        }
    }

    fun setStoryPaused(paused: Boolean) {
        storyPaused = paused
        updateStoryTimer()
    }

    private fun updateStoryTimer() {
        val run = isAttachedToWindow && windowVisibility == VISIBLE && hasWindowFocus() && !storyPaused && recent.size > 1
        val now = SystemClock.uptimeMillis()
        removeCallbacks(storyTick)
        if (!run) {
            if (storyDeadline != 0L) remainingMillis = (storyDeadline - now).coerceIn(0, STORY_MILLIS)
            storyDeadline = 0L
            return
        }
        if (storyDeadline == 0L) storyDeadline = now + remainingMillis
        post(storyTick)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateStoryTimer()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateStoryTimer()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateStoryTimer()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(storyTick)
        storyDeadline = 0L
        super.onDetachedFromWindow()
    }

    private fun addDistance(metrics: TravelHomeSummary) {
        val panel = panel("home.distance", DISTANCE_SURFACE, 36f).apply {
            summarize("달린 거리 " + metrics.distance, metrics.note)
        }
        add(panel, 664f, PANEL_TOP, 976f, 360f)
        place(panel, image("home.distance.map", R.drawable.home_406_distance_map, ImageView.ScaleType.CENTER_CROP),
            350.417f, -67.615f, 660f, 454.706f)
        place(panel, gradientView(GradientDrawable.Orientation.LEFT_RIGHT, DISTANCE_SURFACE, 0x00FFFFFF),
            351.042f, -40.217f, 373.339f, 399.954f)
        place(panel, image("home.distance.icon", R.drawable.home_406_road),
            40.584f, 37.229f, 31.219f, 31.509f)
        place(panel, label("home.distance.label", "달린 거리", 30f, 38f, 0xFF0064FF.toInt(), bold),
            88f, 32.257f, 210f, 38f)

        val value = metrics.distance.removeSuffix("km")
        val size = if (widthOf(value, 84f, bold) > 250f) 60f else 84f
        place(panel, label("home.distance.value", value, size, 108f, INK, bold),
            40f, 225.681f, 285f, 108f)
        if (metrics.distance.endsWith("km")) {
            val unitX = if (metrics.isReference) 263f else 44f + widthOf(value, size, bold)
            place(panel, label("home.distance.unit", "km", 36f, 44f, MUTED, bold),
                unitX, 277.681f, 58f, 44f)
        }
    }

    private fun addDestination(metrics: TravelHomeSummary) {
        val detail = metrics.regionVisits?.let { "${it}번 다녀왔어요" }
        val panel = panel("home.destination", 0xFFECF8F4.toInt(), 36f).apply {
            summarize(listOfNotNull("단골 여행지", metrics.region, detail).joinToString(" "), metrics.note)
        }
        add(panel, 665f, 566f, 481f, 448f)
        place(panel, image("home.destination.icon", R.drawable.home_406_pin),
            36f, 37.355f, 21.9835f, 35.2902f)
        place(panel, label("home.destination.label", "단골 여행지", 30f, 38f, 0xFF008B70.toInt(), bold),
            73.9835f, 36f, 220f, 38f)
        place(panel, image("home.destination.line", R.drawable.home_406_destination_line),
            309.9f, 42.825f, 128.732f, 85.7389f)
        place(panel, image("home.destination.dot", R.drawable.home_406_destination_dot),
            429.686f, 32f, 18f, 18f)
        place(panel, label("home.destination.value", metrics.region, 56f, 72f, INK, extraBold).apply {
            ellipsize = TextUtils.TruncateAt.END
        }, 36f, 301.006f, 409f, 72f)
        if (detail != null) place(panel, label("home.destination.detail", detail, 28f, 36f, MUTED, regular),
            36f, 377.006f, 409f, 36f)
    }

    private fun addDuration(metrics: TravelHomeSummary) {
        val minutes = metrics.drivingMinutes
        val duration = minutes?.let { "${it / 60}시간 ${it % 60}분" } ?: "기록 없음"
        val panel = panel("home.duration", 0xFFF4EFFF.toInt(), 36f).apply {
            summarize("달린 시간 $duration", metrics.note)
        }
        add(panel, 1160f, 566f, 480f, 448f)
        place(panel, image("home.duration.icon", R.drawable.home_406_clock_icon), 36.42f, 37.5f, 35f, 35f)
        place(panel, label("home.duration.label", "달린 시간", 30f, 38f, 0xFF6448DB.toInt(), bold),
            85.42f, 36f, 220f, 38f)
        place(panel, image("home.duration.art", R.drawable.home_406_clock_art), 334f, 16f, 110f, 110f)
        if (minutes == null) {
            place(panel, label("home.duration.value", duration, 56f, 72f, INK, extraBold),
                40f, 290.736f, 404f, 72f)
            return
        }
        val hours = (minutes / 60).toString()
        val remainder = (minutes % 60).toString()
        val baseline = baselineOf(56f, 72f, extraBold)
        var x = 40f
        fun part(tag: String, text: String, size: Float, color: Int, font: Typeface) {
            val width = widthOf(text, size, font)
            place(panel, label(tag, text, size, 72f, color, font, baseline), x, 290.736f, width + 2f, 72f)
            x += width
        }
        part("home.duration.hours", hours, 56f, INK, extraBold)
        x -= 1f
        part("home.duration.hourUnit", "시간", 36f, MUTED, bold)
        x += widthOf(" ", 36f, extraBold)
        part("home.duration.minutes", remainder, 56f, INK, extraBold)
        part("home.duration.minuteUnit", "분", 36f, MUTED, bold)
    }

    private fun View.summarize(copy: String, note: String) {
        contentDescription = copy
        if (note.isNotEmpty()) tooltipText = note
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isScreenReaderFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = resolveSize((width * 1184f / 1700f).roundToInt(), heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(width, height)
        scale = min(width / 1700f, height / 1184f)
        surface.set((width - 1700f * scale) / 2, (height - 1184f * scale) / 2,
            (width + 1700f * scale) / 2, (height + 1184f * scale) / 2)
        typography.forEach { it.view.setReferenceTypography(it.size, it.height, it.baseline, scale) }
        shapes.forEach {
            it.drawable.cornerRadius = it.radius * scale
            it.stroke?.let { color -> it.drawable.setStroke(px(1f).coerceAtLeast(1), color) }
        }
        children.forEach { item ->
            val params = item.view.layoutParams as FrameLayout.LayoutParams
            params.width = px(item.width)
            params.height = px(item.height)
            params.leftMargin = px(item.x)
            params.topMargin = px(item.y)
        }
        roots.forEach { it.view.measure(exact(px(it.width)), exact(px(it.height))) }
        border.strokeWidth = 2f * scale
        edge.set(surface)
        edge.inset(scale, scale)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        roots.forEach {
            val x = (surface.left + (it.x + 2f) * scale).roundToInt()
            val y = (surface.top + (it.y + 2f) * scale).roundToInt()
            it.view.layout(x, y, x + it.view.measuredWidth, y + it.view.measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(surface, 24f * scale, 24f * scale, paper)
        canvas.drawRoundRect(edge, 23f * scale, 23f * scale, border)
        val saved = canvas.save()
        canvas.translate(surface.left + 2f * scale, surface.top + 2f * scale)
        canvas.scale(scale, scale)
        canvas.drawRoundRect(664f, PANEL_TOP, 1640f, PANEL_TOP + 360f, 36f, 36f, distanceShadow)
        canvas.restoreToCount(saved)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withClip(surface) { super.dispatchDraw(this) }
    }

    private fun panel(tag: String, color: Int, radius: Float) = object : FrameLayout(context) {
        private val clip = Path()

        override fun dispatchDraw(canvas: Canvas) {
            clip.rewind()
            val corner = radius * scale
            clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), corner, corner, Path.Direction.CW)
            canvas.withClip(clip) { super.dispatchDraw(this) }
        }
    }.apply {
        this.tag = tag
        background = shape(color, radius)
    }

    private fun label(tag: String, copy: String, size: Float, height: Float, color: Int, font: Typeface,
        baseline: Float = baselineOf(size, height, font)) = ReferenceDesignLabel(context).apply {
        this.tag = tag
        text = copy
        maxLines = 1
        includeFontPadding = false
        letterSpacing = 0f
        typeface = font
        setTextColor(color)
        setPadding(0, 0, 0, 0)
        gravity = Gravity.TOP or Gravity.START
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        typography += Typography(this, size, height, baseline)
    }

    private fun actionPanel(tag: String, description: String, fill: Int, stroke: Int? = null,
        action: () -> Unit) = object : FrameLayout(context) {
        override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name
    }.apply {
        this.tag = tag
        contentDescription = description
        isFocusable = true
        background = shape(fill, 16f, stroke)
        val selectable = android.util.TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)) {
            foreground = AppCompatResources.getDrawable(context, selectable.resourceId)
        }
        setOnClickListener { action() }
    }

    private fun button(tag: String, copy: String, fill: Int, color: Int,
        action: () -> Unit) = ReferenceDesignButton(context).apply {
        this.tag = tag
        text = copy
        isAllCaps = false
        typeface = notoBold
        maxLines = 1
        includeFontPadding = false
        letterSpacing = 0f
        gravity = Gravity.CENTER
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        insetTop = 0
        insetBottom = 0
        setPadding(0, 0, 0, 0)
        elevation = 0f
        stateListAnimator = null
        backgroundTintList = null
        background = shape(fill, 16f)
        setTextColor(color)
        addPressMotion()
        setOnClickListener { action() }
        typography += Typography(this, 30f, 100f, 32f + baselineOf(30f, 36f, notoBold))
    }

    private fun image(tag: String, resource: Int, mode: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER) =
        ImageView(context).apply {
            this.tag = tag
            setImageResource(resource)
            scaleType = mode
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun gradientView(direction: GradientDrawable.Orientation, first: Int, last: Int) = View(context).apply {
        background = GradientDrawable(direction, intArrayOf(first, last))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun shape(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        shapes += Shape(this, radius, stroke)
    }

    private fun baselineOf(size: Float, height: Float, font: Typeface): Float {
        textPaint.textSize = size
        textPaint.typeface = font
        val metrics = textPaint.fontMetrics
        return (height - metrics.ascent - metrics.descent) / 2f
    }

    private fun widthOf(text: String, size: Float, font: Typeface): Float {
        textPaint.textSize = size
        textPaint.typeface = font
        return textPaint.measureText(text)
    }

    private fun add(view: View, x: Float, y: Float, width: Float, height: Float) {
        roots += Placement(view, x, y, width, height)
        addView(view)
    }

    private fun place(parent: FrameLayout, view: View, x: Float, y: Float, width: Float, height: Float) {
        children += Placement(view, x, y, width, height)
        parent.addView(view, FrameLayout.LayoutParams(0, 0))
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    private fun px(value: Float) = (value * scale).roundToInt()
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)

    private companion object {
        const val TITLE_INK = 0xFF212121.toInt()
        const val INK = 0xFF131417.toInt()
        const val MUTED = 0xFF44464E.toInt()
        const val DISTANCE_SURFACE = 0xFFF1F6FF.toInt()
        const val PANEL_TOP = 190.48f
        const val STORY_MILLIS = 5_000L
        val TRIP_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN)

    }
}

internal data class TravelHomeSummary(
    val distance: String,
    val region: String,
    val regionVisits: Int?,
    val drivingMinutes: Long?,
    val note: String,
    val isReference: Boolean = false,
) {
    companion object {
        fun from(trips: List<Trip>, repository: (Trip) -> TripRecording?): TravelHomeSummary {
            val ids = trips.map { it.id }.toSet()
            if (ids.size == DemoContent.trips.size && DemoContent.trips.all { it.id in ids }) {
                // The A2 artwork and metrics describe the complete Figma demo catalog.
                return TravelHomeSummary("2,140km", "강릉", 4, 36 * 60L + 30, "데모 여행 9편의 요약", true)
            }

            val records = trips.associateWith(repository)
            val distances = records.mapNotNull { (trip, record) ->
                val meters = record?.distanceMeters?.takeIf { it.isFinite() && it >= 0 }
                when {
                    meters != null -> meters / 1000
                    DemoContent.coverIndex(trip) != null -> TripSample.distanceKm.toDouble()
                    else -> null
                }
            }
            val number = NumberFormat.getNumberInstance(Locale.KOREA).apply {
                maximumFractionDigits = 0
                roundingMode = java.math.RoundingMode.HALF_UP
            }
            val distance = if (distances.isEmpty()) "기록 없음" else number.format(distances.sum()) + "km"
            val destination = trips.takeIf { it.size >= 3 }.orEmpty().flatMap { trip ->
                val recording = records[trip]
                if (recording == null) listOfNotNull(DemoContent.destination(trip))
                else {
                    val endpoints = listOfNotNull(recording.points.firstOrNull(), recording.latest)
                    recording.visibleStops.mapNotNull { stop ->
                        val point = stop.point ?: return@mapNotNull null
                        if (endpoints.any { metersBetween(it, point) <= 1_000.0 }) return@mapNotNull null
                        val address = PlaceDisplayNames.getAddress(stop.selectedPlaceId)
                            ?: PlaceDisplayNames.getRoadAddress(stop.selectedPlaceId)
                        address?.split(Regex("\\s+"))?.drop(1)
                            ?.firstOrNull { it.endsWith("시") || it.endsWith("군") }
                    }.toSet()
                }
            }.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()
            val minutes = records.values.mapNotNull { it?.movingMillis?.takeIf { millis -> millis >= 0 } }
            val note = if (distances.size < trips.size) "거리 기록 ${distances.size} / ${trips.size}개" else ""
            return TravelHomeSummary(distance, destination?.key ?: "지역 기록 없음", destination?.value,
                minutes.takeIf { it.isNotEmpty() }?.sum()?.div(60_000), note)
        }
    }
}
