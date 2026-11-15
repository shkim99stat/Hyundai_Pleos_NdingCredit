package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.roundToInt

internal class CatalogState {
    var query = ""
    var dates: ArchiveDateRange? = null
    var firstVisible = 0
    var firstOffset = 0
}

internal open class CatalogWindow(
    context: Context,
    width: Float = 1696f,
    height: Float = 1180f,
    border: Int = 0xFFD4D4D4.toInt(),
) : ResultDesignSurface(context, width, height) {
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = border
    }
    private val edge = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        outline.strokeWidth = 2f * drawingScale
        edge.set(surface)
        edge.inset(drawingScale, drawingScale)
        canvas.drawRoundRect(edge, 23f * drawingScale, 23f * drawingScale, outline)
    }
}

/** F1 app window, Figma 834:62400. */
internal class F1CatalogView(
    context: Context,
    trips: List<Trip>,
    private val state: CatalogState,
    onBack: () -> Unit,
    private val onStart: () -> Unit,
    private val onOpen: (Trip) -> Unit,
    private val onPhoto: (Trip) -> Unit,
    private val onReview: () -> Unit,
    private val onResume: ((Int?) -> Unit)?,
    private val onPlay: (Trip) -> Unit = onOpen,
) : CatalogWindow(context, 1700f, 1184f, 0xFFE0E1E6.toInt()) {
    private val samples = if (BuildConfig.APPLICATION_ID.endsWith(".demo")) F1SamplePosters(context) else null
    private val ordered = trips.sortedWith(
        compareBy<Trip> { samples?.order(it) ?: Int.MAX_VALUE }.thenByDescending { it.createdOn },
    )
    private val favorites = context.getSharedPreferences("travel_archive_favorites", Context.MODE_PRIVATE)
    private val metadata = mutableMapOf<String, CardMetadata>()
    private val startDates by lazy(LazyThreadSafetyMode.NONE) {
        ordered.associate { trip ->
            trip.id to (samples?.get(trip.id)?.let { F1SamplePosters.startDate } ?: parseTripDate(trip.createdOn))
        }
    }
    private val regular = AppTypography.font(context)
    private val bold = AppTypography.font(context, 700)
    private val textColor = 0xFF131417.toInt()
    private val shortDate = DateTimeFormatter.ofPattern("MM.dd")
    private val fullDate = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private var speech: RecordingSpeechInput? = null
    private var calendar: FrameLayout? = null
    private var visibleTrips = emptyList<Trip>()
    private var cardCount = 0
    private var popup: PopupMenu? = null
    private var preview: AlertDialog? = null
    private var previewRequest = 0
    private var previewLoad: Future<*>? = null
    private var restoreScroll = true
    private var cardWidth = 1
    private val input = EditText(context).apply {
        tag = "f1.search"
        hint = "여행 이름이나 지역으로 찾아보세요..."
        contentDescription = "여행 검색"
        setText(state.query)
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setTextColor(ink)
        setHintTextColor(0xFF686A72.toInt())
        setBackgroundColor(Color.TRANSPARENT)
        typeface = regular
        includeFontPadding = false
        gravity = android.view.Gravity.CENTER_VERTICAL
        setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) false else {
                hideKeyboard()
                true
            }
        }
    }
    private val grid = object : GridView(context) {
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        override fun dispatchDraw(canvas: Canvas) {
            shadow.setShadowLayer(24f * drawingScale, 0f, 12f * drawingScale, 0x33000000)
            val radius = 24f * drawingScale
            for (index in 0 until childCount) {
                val card = getChildAt(index)
                canvas.drawRoundRect(card.left.toFloat(), card.top.toFloat(), card.right.toFloat(),
                    card.bottom.toFloat(), radius, radius, shadow)
            }
            super.dispatchDraw(canvas)
        }
    }.apply {
        tag = "f1.grid"
        id = R.id.archive_scroll
        numColumns = 4
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipChildren = false
        clipToPadding = false
    }
    private val empty = label("f1.empty", "검색 결과가 없어요", 32f, color = muted, centered = true)
    private val cards = object : BaseAdapter() {
        override fun getCount() = cardCount
        override fun getItem(position: Int) = visibleTrips.getOrNull(position)
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val poster = convertView as? Poster ?: Poster()
            poster.layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (cardWidth * 420f / 392f).roundToInt(),
            )
            poster.bind(visibleTrips[position])
            return poster
        }
    }

    init {
        id = R.id.archive_root
        tag = "f1.catalog"
        isFocusableInTouchMode = true
        place(button("f1.back", "", { hideKeyboard(); onBack() }).apply {
            contentDescription = "뒤로 가기, 홈으로 돌아가기"
        }, 8f, 36f, 56f, 60f)
        place(f1Asset("back.svg"), 28.5f, 52.5f, 15f, 27f)
        place(label("f1.title", "전체 상영관", 40f, 700, textColor, typeface = bold).apply {
            isAccessibilityHeading = true
            setOnLongClickListener { showArchiveActions(it); true }
        }, 78f, 40f, 450f, 52f)
        place(box("f1.search.border", Color.WHITE, 16f, 3f, 0xFFE0E1E6.toInt()), 30f, 152f, 1100f, 84f)
        place(input, 30f, 152f, 1004f, 84f)
        place(f1Asset("search.svg"), 42f, 170f, 48f, 48f)
        place(f1Asset("microphone.svg"), 1058f, 170f, 48f, 48f)
        place(button("f1.voice", "", ::voiceSearch).apply {
            contentDescription = "여행 이름이나 지역으로 음성 검색"
        }, 1034f, 152f, 96f, 84f)
        place(button("f1.calendar", "", ::openCalendar).apply {
            contentDescription = "캘린더"
            background = shape(Color.WHITE, 16f, 3f, 0xFFE0E1E6.toInt())
        }, 1154f, 152f, 256f, 84f)
        place(label("f1.calendar.label", "캘린더", 28f, 700, textColor, typeface = bold).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 1178f, 176f, 110f, 36f)
        place(f1Asset("calendar-chevron.svg").apply { rotation = -90f },
            1366.25f, 179.25f, 15.5f, 27.5f)
        place(button("f1.add", "", { hideKeyboard(); onStart() }, solid = true).apply {
            contentDescription = "새 여행 추가"
            background = shape(0xFF0064FF.toInt(), 16f)
        }, 1434f, 152f, 236f, 84f)
        place(label("f1.add.label", "추가", 28f, 700, Color.WHITE, centered = true, typeface = bold).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 1434f, 176f, 236f, 36f)
        grid.adapter = cards
        place(grid, 8f, 254f, 1684f, 914f)
        place(empty, 56f, 514f, 1584f, 56f)
        updateGrid(reset = false)
        input.doAfterTextChanged { state.query = it.toString(); updateGrid() }
        requestFocus()
    }

    private fun f1Asset(file: String) = asset(file, "f1.$file", "f1_catalog")

    private fun voiceSearch() {
        hideKeyboard()
        speech?.close()
        speech = RecordingSpeechInput(context, { value ->
            if (isAttachedToWindow && isShown) input.setText(value)
        }, "여행 이름이나 지역을 말해 주세요")
        speech?.start()
    }

    private fun showArchiveActions(anchor: View) {
        popup?.dismiss()
        popup = PopupMenu(context, anchor).apply {
            if (ordered.isNotEmpty()) menu.add("연말 결산 보기").setOnMenuItemClickListener { onReview(); true }
            onResume?.let { resume ->
                menu.add("보던 크레딧 이어 보기").setOnMenuItemClickListener { resume(null); true }
                menu.add("보던 크레딧 처음부터").setOnMenuItemClickListener { resume(0); true }
                menu.add("보던 크레딧 마지막 장면").setOnMenuItemClickListener { resume(CreditPlayback.frames.last()); true }
            }
            show()
        }
    }

    private fun matching(range: ArchiveDateRange?): List<Trip> {
        val query = state.query.trim()
        return ordered.filter { trip ->
            (query.isEmpty() || cardTitle(trip).contains(query, ignoreCase = true) ||
                trip.name.contains(query, ignoreCase = true) || trip.createdOn.contains(query) ||
                cardStart(trip)?.toString()?.contains(query) == true ||
                DemoContent.destination(trip)?.contains(query, ignoreCase = true) == true) &&
                (range == null || range.includes(cardStart(trip)))
        }
    }

    private fun openCalendar() {
        hideKeyboard()
        if (calendar != null) return
        val trips = matching(null)
        val dates = trips.mapNotNull(::cardStart)
        val panel = F1_1CalendarView(context, state.dates, dates.toSet(),
            { range -> if (range == null) trips.size else dates.count(range::includes) },
            { closeCalendar() },
        ) { range ->
            state.dates = range
            closeCalendar()
            updateGrid()
        }
        val overlay = TravelFlowOverlay(context, panel, 1000f, 1080f, true) { closeCalendar() }
        calendar = overlay
        for (index in 0 until childCount) {
            getChildAt(index).importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        place(overlay, 0f, 0f, 1700f, 1184f)
        overlay.isFocusableInTouchMode = true
        overlay.requestFocus()
    }

    fun closeCalendar(): Boolean {
        val overlay = calendar ?: return false
        removeSlot(overlay)
        calendar = null
        for (index in 0 until childCount) {
            getChildAt(index).importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        return true
    }

    private fun updateGrid(reset: Boolean = true) {
        visibleTrips = matching(state.dates)
        cardCount = visibleTrips.size
        empty.text = if (ordered.isEmpty()) "아직 기록이 없어요" else "검색 결과가 없어요"
        empty.visibility = if (cardCount == 0) VISIBLE else GONE
        findViewWithTag<View>("f1.calendar").isSelected = state.dates != null
        cards.notifyDataSetChanged()
        if (reset) {
            state.firstVisible = 0
            state.firstOffset = 0
            grid.setSelection(0)
        }
    }
    fun rememberScroll() {
        if (grid.childCount == 0) return
        state.firstVisible = grid.firstVisiblePosition
        state.firstOffset = grid.getChildAt(0).top
    }

    private fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val next = minOf(MeasureSpec.getSize(widthMeasureSpec) / 1700f, MeasureSpec.getSize(heightMeasureSpec) / 1184f)
        cardWidth = (392f * next).roundToInt().coerceAtLeast(1)
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 26f * next)
        input.setPadding((72f * next).roundToInt(), 0, (20f * next).roundToInt(), 0)
        val inset = (22f * next).roundToInt()
        grid.setPadding(inset, inset, inset, (32f * next).roundToInt())
        grid.horizontalSpacing = (24f * next).roundToInt()
        grid.verticalSpacing = (28f * next).roundToInt()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (restoreScroll && grid.childCount > 0) {
            restoreScroll = false
            if (state.firstVisible > 0 || state.firstOffset != 0) {
                grid.setSelectionFromTop(state.firstVisible.coerceAtMost(cardCount - 1), state.firstOffset)
            }
        }
    }

    override fun onDetachedFromWindow() {
        popup?.dismiss()
        previewRequest++
        previewLoad?.cancel(false)
        previewLoad = null
        preview?.dismiss()
        speech?.close()
        super.onDetachedFromWindow()
    }

    private fun enlarge(trip: Trip) {
        preview?.dismiss()
        previewLoad?.cancel(false)
        val request = ++previewRequest
        previewLoad = photoLoader.submit {
            val bitmap = CatalogPhotos.customPoster(context, trip, enlarged = true)
                ?: samples?.get(trip.id)?.let { sample ->
                    runCatching { samples?.bitmap(sample.file, enlarged = true) }.getOrNull()
                }
            ui.post {
                val outgoing = (parent as? View)?.importantForAccessibility ==
                    IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                if (request == previewRequest && isAttachedToWindow && !outgoing) showPreview(trip, bitmap)
            }
        }
    }

    private fun showPreview(trip: Trip, bitmap: Bitmap?) {
        preview = AlertDialog.Builder(context).setTitle(cardTitle(trip)).apply {
            if (bitmap != null) setView(ImageView(context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                maxHeight = (resources.displayMetrics.heightPixels * .65f).roundToInt()
                setImageBitmap(bitmap)
                contentDescription = "${cardTitle(trip)} 사진"
            }) else setMessage("아직 추가한 사진이 없어요.")
        }.setNeutralButton(if (bitmap == null) "사진 추가" else "사진 변경") { _, _ ->
            rememberScroll()
            onPhoto(trip)
        }.setPositiveButton("닫기", null).show()
    }

    private fun cardTitle(trip: Trip): String = samples?.title(trip) ?: trip.name

    private fun cardStart(trip: Trip): LocalDate? = startDates[trip.id]

    private data class CardMetadata(val date: String, val duration: String)

    private fun cardMetadata(trip: Trip): CardMetadata = metadata.getOrPut(trip.id) {
        samples?.get(trip.id)?.let { return@getOrPut CardMetadata("2026.10.24 – 10.25", it.duration) }
        val start = cardStart(trip)
        val end = TripRecordingRepository.get(context).load(trip.id)?.endedAt
            ?.let { travelLocalDate(it, tripDisplayZone(trip)) }
            ?.takeIf { start != null && it >= start }
        val dates = when {
            start == null -> trip.createdOn
            end == null || end == start -> fullDate.format(start)
            else -> "${fullDate.format(start)} – ${if (end.year == start.year) shortDate.format(end) else fullDate.format(end)}"
        }
        val nights = if (start != null && end != null) ChronoUnit.DAYS.between(start, end) else 0
        CardMetadata(dates, if (nights > 0) "${nights}박 ${nights + 1}일" else "")
    }

    private inner class Poster : ResultDesignSurface(context, 392f, 420f) {
        private val clip = Path()
        private var trip: Trip? = null
        private var photoLoad: Future<*>? = null
        private var photoRequest = 0
        private val photo = F1PosterPhoto(context).apply {
            tag = "f1.poster.image"
            setBackgroundColor(0xFF686A72.toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        private val title = label("f1.poster.title", "", 30f, 700, Color.WHITE, typeface = bold)
        private val date = label("f1.poster.date", "", 20f, color = 0xB3FFFFFF.toInt(), typeface = regular)
        private val duration = label("f1.poster.duration", "", 20f, color = Color.WHITE, typeface = regular)
        private val secondaryTitle = label("f1.poster.secondary.title", "", 30f, 700, Color.WHITE, typeface = bold)
        private val secondaryDate = label("f1.poster.secondary.date", "", 20f, color = 0xD9FFFFFF.toInt(), typeface = regular)
        private val heart = f1Asset("heart.svg")
        private val heartOn = f1Asset("heart-selected.svg")
        private val favorite = button("f1.poster.favorite", "", ::toggleFavorite)
        private val play = button("f1.poster.play", "", {
            trip?.let {
                rememberScroll()
                hideKeyboard()
                onPlay(it)
            }
        })
        private val playIcon = f1Asset("play.svg")

        init {
            background = shape(Color.WHITE, 24f)
            place(photo, 0f, 0f, 392f, 420f)
            place(View(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, Color.BLACK))
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, 0f, 240f, 392f, 180f)
            place(duration, 20f, 276f, 290f, 24f)
            place(title, 20f, 308f, 290f, 38f)
            place(date, 20f, 354f, 290f, 24f)
            place(secondaryTitle, 20f, 308f, 352f, 38f)
            place(secondaryDate, 20f, 354f, 352f, 24f)
            place(heart, 324f, 20f, 48f, 48f)
            place(heartOn, 324f, 20f, 48f, 48f)
            place(favorite, 316f, 12f, 64f, 64f)
            place(playIcon, 324f, 352f, 48f, 48f)
            place(play, 316f, 344f, 64f, 64f)
        }

        fun bind(entry: Trip) {
            val needsPhoto = trip !== entry
            trip = entry
            val name = cardTitle(entry)
            val details = cardMetadata(entry)
            val sample = samples?.get(entry.id)
            tag = "f1.poster.${entry.id}"
            if (needsPhoto) loadPhoto(entry, sample)
            val showPlay = sample?.showPlay ?: entry.mode.showsCredit
            title.text = name
            date.text = details.date
            duration.text = details.duration
            secondaryTitle.text = name
            secondaryDate.text = listOf(details.date, details.duration).filter(String::isNotBlank).joinToString(" · ")
            title.visibility = if (showPlay) VISIBLE else GONE
            date.visibility = title.visibility
            duration.visibility = title.visibility
            playIcon.visibility = title.visibility
            play.visibility = title.visibility
            secondaryTitle.visibility = if (showPlay) GONE else VISIBLE
            secondaryDate.visibility = secondaryTitle.visibility
            photo.contentDescription = "$name 여행 상세"
            photo.setOnClickListener { rememberScroll(); hideKeyboard(); onOpen(entry) }
            photo.setOnLongClickListener { enlarge(entry); true }
            photo.isFocusable = true
            photo.tooltipText = "길게 눌러 사진 보기 또는 변경"
            play.contentDescription = "$name 크레딧 다시보기"
            updateFavorite()
        }

        private fun loadPhoto(entry: Trip, poster: F1SamplePosters.Poster?) {
            photoLoad?.cancel(false)
            val request = ++photoRequest
            photo.show(null)
            photoLoad = photoLoader.submit {
                val custom = CatalogPhotos.customPoster(context, entry)
                val sample = poster.takeIf { custom == null }
                val cover = custom ?: sample?.let { runCatching { samples?.bitmap(it.file) }.getOrNull() }
                val background = if (sample?.file == "flowers.png") {
                    runCatching { samples?.bitmap(F1SamplePosters.LAKE) }.getOrNull()
                } else null
                ui.post {
                    if (request == photoRequest) photo.show(cover, sample, background)
                }
            }
        }

        private fun toggleFavorite() {
            val entry = trip ?: return
            favorites.edit().putBoolean(favoriteKey(entry), !isFavorite(entry)).apply()
            updateFavorite()
        }

        private fun favoriteKey(entry: Trip): String =
            if (samples?.get(entry.id) != null) "figma-f1:${entry.id}" else entry.id

        private fun isFavorite(entry: Trip) =
            favorites.getBoolean(favoriteKey(entry), samples?.get(entry.id)?.favorite == true)

        private fun updateFavorite() {
            val entry = trip ?: return
            val selected = isFavorite(entry)
            favorite.isSelected = selected
            favorite.contentDescription = "${cardTitle(entry)} 즐겨찾기 ${if (selected) "해제" else "추가"}"
            heart.visibility = if (selected) GONE else VISIBLE
            heartOn.visibility = if (selected) VISIBLE else GONE
        }

        override fun dispatchDraw(canvas: Canvas) {
            val radius = 24f * drawingScale
            clip.rewind()
            clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
            val saved = canvas.save()
            canvas.clipPath(clip)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(saved)
        }
    }

    private companion object {
        val photoLoader = Executors.newFixedThreadPool(2)
        val ui = Handler(Looper.getMainLooper())
    }
}
