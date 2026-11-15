package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaPlayer
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

internal class B3_3ScreeningView(
    context: Context,
    content: CreditContent,
    regular: Typeface,
    bold: Typeface,
    sampleVideo: Boolean,
    private val togglePlayback: () -> Unit,
) : ViewGroup(context) {
    private val roll = ScreeningTimeline(context, content, regular, bold)
    private val background = Paint().apply {
        shader = RadialGradient(362f, 239f, 415f,
            intArrayOf(0xFF16171C.toInt(), 0xFF121317.toInt(), 0xFF0E0F13.toInt()),
            null, Shader.TileMode.CLAMP)
    }
    private val videoMask = Path()
    private val uploaded = CreditVideoFiles(context).committed(content.trip.id).takeIf(File::isFile)
    private val hasVideo = content.trip.creditVideo == CreditVideoChoice.UPLOADED && (uploaded != null || sampleVideo)
    private val video = if (hasVideo) ScreeningVideo(context, uploaded, ::hideVideo).apply { tag = if (uploaded == null) "b3.video.cat" else "b3.video.uploaded" } else null
    private var videoFault = false
    private val play = View(context).apply {
        tag = "b3.video.play"
        contentDescription = "영상과 크레딧 일시 정지"
        isFocusable = true
        setOnClickListener { togglePlayback() }
    }
    private var paused: Boolean? = null

    init {
        tag = "b3.film"
        setTag(R.id.ending_credit_screen_id, CreditPlayback.screenId(CreditPlayback.SCREENING_FRAME))
        setWillNotDraw(false)
        video?.let(::addView)
        addView(roll)
        if (video != null) addView(play)
        roll.setVideoVisible(hasVideo)
    }

    private fun hideVideo() {
        post {
            videoFault = true
            video?.visibility = INVISIBLE
            play.visibility = GONE
            roll.setVideoVisible(false)
        }
    }

    fun update(elapsed: Long, playing: Boolean, isPaused: Boolean, seek: Boolean) {
        roll.update(elapsed, isPaused)
        video?.sync(elapsed, playing && !videoFault, seek)
        if (paused != isPaused) {
            paused = isPaused
            play.contentDescription = if (isPaused) "영상과 크레딧 재생" else "영상과 크레딧 일시 정지"
        }
    }

    override fun onDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.scale(width / WIDTH, height / HEIGHT)
        canvas.drawRect(0f, 0f, WIDTH, HEIGHT, background)
        canvas.restoreToCount(saved)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child !== video) return super.drawChild(canvas, child, drawingTime)
        val saved = canvas.save()
        canvas.clipPath(videoMask)
        val drawn = super.drawChild(canvas, child, drawingTime)
        canvas.restoreToCount(saved)
        return drawn
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        roll.measure(exact(w), exact(h))
        video?.let {
            val vw = exact((w * VIDEO_WIDTH / WIDTH).roundToInt())
            val vh = exact((h * VIDEO_HEIGHT / HEIGHT).roundToInt())
            it.measure(vw, vh)
            play.measure(vw, vh)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        roll.layout(0, 0, width, height)
        video?.let {
            val left = (width * 25.5f / WIDTH).roundToInt()
            val top = (height * 44.7f / HEIGHT).roundToInt()
            val right = left + it.measuredWidth
            val bottom = top + it.measuredHeight
            it.layout(left, top, right, bottom)
            play.layout(left, top, right, bottom)
            it.fit()
            videoMask.rewind()
            val radius = width * 13f / WIDTH
            videoMask.addRoundRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(),
                radius, radius, Path.Direction.CW)
        }
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)
}

private const val WIDTH = 680f
private const val HEIGHT = 474f
private const val VIDEO_WIDTH = 216f
private const val VIDEO_HEIGHT = 384f

private class ScreeningTimeline(
    context: Context, content: CreditContent, private val regular: Typeface, private val bold: Typeface,
) : View(context) {
    private enum class Kind { TITLE, DAY, STOP, NIGHT, RUNTIME, CLOSING }
    private val copy = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    // Positions and fonts are in design coordinates, so fit each line once before playback.
    private inner class TextLine(value: String, private val x: Float, private val size: Float, private val face: Typeface) {
        private val fitted: CharSequence

        init {
            copy.typeface = face
            copy.textSize = size
            fitted = TextUtils.ellipsize(value, copy, TEXT_RIGHT - x, TextUtils.TruncateAt.END)
        }

        fun draw(canvas: Canvas, baseline: Float, color: Int) {
            copy.typeface = face
            copy.textSize = size
            copy.color = color
            canvas.drawText(fitted, 0, fitted.length, x, baseline, copy)
        }
    }

    private inner class Row(val y: Float, val title: String, val detail: String?, val kind: Kind) {
        val heading = when (kind) {
            Kind.TITLE -> TextLine(title, TEXT_X, 23f, bold)
            Kind.DAY -> TextLine(title, TEXT_X, 11.5f, bold)
            Kind.STOP -> TextLine(title, TEXT_X, 16f, bold)
            Kind.NIGHT, Kind.RUNTIME -> TextLine(title, TEXT_X + 26f, 12f, bold)
            Kind.CLOSING -> TextLine("함께한 사람", TEXT_X, 10.5f, regular)
        }
        val caption = detail?.let { value ->
            when (kind) {
                Kind.TITLE -> TextLine(value, TEXT_X, 11.5f, regular)
                Kind.DAY -> TextLine(value, TEXT_X + 77f, 10.5f, regular)
                Kind.STOP -> TextLine(value, TEXT_X, 11f, regular)
                Kind.CLOSING -> TextLine(value, TEXT_X, 10.5f, regular)
                Kind.NIGHT, Kind.RUNTIME -> null
            }
        }
    }

    private val videoCaption = TextLine("함께 탄 사람이 올린 영상 · 소리 없음", 40f, 8.4f, regular)
    private val ending = TextLine("T H E   E N D", TEXT_X, 14f, bold)
    private val rows = buildList {
        add(Row(195f, content.title, "${content.dateText}  ·  A ROAD MOVIE", Kind.TITLE))
        var y = 321f
        content.timeline.forEach { day ->
            add(Row(y, "DAY ${day.number}", day.date, Kind.DAY))
            y += 68f
            day.departure?.let {
                add(Row(y, day.departureLabel, it, Kind.STOP))
                y += 105f
            }
            day.stops.forEach {
                add(Row(y, it.name, it.time, Kind.STOP))
                y += 105f
            }
        }
        if (last().kind == Kind.STOP) y -= 60f
        if (content.recording != null) {
            content.moments.firstOrNull { it.startsWith("밤길 ") }?.let { nightDriving ->
                add(Row(y - 8f, nightDriving, null, Kind.NIGHT))
                y += 40f
            }
        }
        content.runtimeText?.takeIf { content.recording != null }?.let {
            add(Row(y - 8f, "주행 시간 $it", null, Kind.RUNTIME))
            y += 40f
        }
        val distance = content.distanceText.takeIf { content.distanceKm != null }?.let { "${it}km를 함께 달렸어요" }
        add(Row(y - 12f + CLOSING_SPACE, content.companionsText, distance, Kind.CLOSING))
    }
    private val companionsLayout = content.companionsText.let { name ->
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = bold
            textSize = 23f
        }
        StaticLayout.Builder.obtain(name, 0, name.length, paint, (TEXT_RIGHT - TEXT_X).toInt())
            .setIncludePad(false)
            .build()
    }
    private val companionsExtraHeight = companionsLayout.getLineBaseline(companionsLayout.lineCount - 1) -
        companionsLayout.getLineBaseline(0)
    private val stops = rows.filter { it.kind == Kind.STOP }
    private val firstStop = stops.firstOrNull()?.y ?: MARKER_CENTER
    private val lastStop = stops.lastOrNull()?.y ?: MARKER_CENTER
    private val endOffset = if (stops.isEmpty()) (rows.last().y - 183f).coerceAtLeast(0f) else
        maxOf(lastStop - 156f, rows.last().y - 256f)
    private val duration = CreditPlayback.duration(CreditPlayback.SCREENING_FRAME)
    private val travelEasing = PathInterpolator(.12f, 0f, .24f, .24f)
    private val markerTravel = (lastStop - firstStop).coerceIn(0f, 70f)
    private val entryEasing = run {
        // Match the entry curve's final speed to the marker's first speed during scrolling.
        val entryDuration = FIRST_STOP_MILLIS - START_DROP_MILLIS
        val entryDistance = MARKER_CENTER - MARKER_START
        val followSpeed = 2f * markerTravel / MARKER_DRIFT_MILLIS
        val endSlope = followSpeed * entryDuration / entryDistance
        val lastControlX = .68f
        PathInterpolator(.20f, .06f, lastControlX, 1f - (1f - lastControlX) * endSlope)
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 15f,
            intArrayOf(0xB0006DFF.toInt(), 0x55006DFF, 0x00006DFF), null, Shader.TileMode.CLAMP)
    }
    private val icon = Path()
    private var videoVisible = false
    private var paused = false
    private var elapsed = 0L

    init {
        tag = "b3.roll"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = rows.joinToString(", ") { listOfNotNull(it.detail, it.title).joinToString(" ") } + ", THE END"
    }

    fun setVideoVisible(visible: Boolean) {
        videoVisible = visible
        invalidate()
    }

    fun update(elapsed: Long, paused: Boolean) {
        if (this.elapsed == elapsed && this.paused == paused) return
        this.elapsed = elapsed
        this.paused = paused
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.scale(width / WIDTH, height / HEIGHT)
        val entering = stops.isNotEmpty() && elapsed < FIRST_STOP_MILLIS
        val progression = travelEasing.getInterpolation(
            CreditPlayback.phase(elapsed, FIRST_STOP_MILLIS, duration - 2_000L),
        )
        val drift = CreditPlayback.phase(elapsed, FIRST_STOP_MILLIS, FIRST_STOP_MILLIS + MARKER_DRIFT_MILLIS)
        val focusY = MARKER_CENTER + markerTravel * drift * (2f - drift)
        val firstStopOffset = firstStop - MARKER_CENTER
        val scrollDistance = lastStop - firstStop - markerTravel
        val follow = firstStopOffset + scrollDistance * progression
        val close = CreditPlayback.phase(elapsed, duration - 4_800L, duration - 1_100L)
        val offset = if (entering) {
            firstStopOffset * smoothPhase(850L, FIRST_STOP_MILLIS)
        } else {
            follow * (1f - close) + endOffset * close
        }
        val marker = if (entering) {
            val descent = entryEasing.getInterpolation(
                CreditPlayback.phase(elapsed, START_DROP_MILLIS, FIRST_STOP_MILLIS),
            )
            MARKER_START + (MARKER_CENTER - MARKER_START) * descent
        } else {
            focusY + (follow - offset)
        }

        drawRail(canvas, offset, marker)
        rows.forEach { row ->
            val y = row.y - offset
            if (y in 28f..461f) drawRow(canvas, row, y, marker)
        }
        if (videoVisible) {
            drawMuteIcon(canvas)
            videoCaption.draw(canvas, 444f, 0xFF83848A.toInt())
            if (paused) drawPausedIcon(canvas)
        }
        canvas.restoreToCount(saved)
    }

    private fun smoothPhase(start: Long, end: Long): Float {
        val t = CreditPlayback.phase(elapsed, start, end)
        return t * t * (3f - 2f * t)
    }

    private fun drawRail(canvas: Canvas, offset: Float, marker: Float) {
        if (stops.isEmpty()) return
        val end = lastStop - offset
        val begin = maxOf(RAIL_TOP, firstStop - offset, marker)
        if (end >= begin) {
            ink.color = 0xFF44484F.toInt()
            ink.strokeWidth = 1f
            // Unvisited dots stay in step with the scrolling stops.
            var y = begin + (4f - (begin + offset) % 4f) % 4f
            while (y <= minOf(end, 470f)) {
                ink.alpha = (175 * edgeAlpha(y)).roundToInt()
                canvas.drawPoint(RAIL_X, y, ink)
                y += 4f
            }
        }
        if (marker > RAIL_TOP) {
            val clip = canvas.save()
            canvas.clipRect(RAIL_X - 18f, RAIL_TOP, RAIL_X + 18f, 470f)
            val markerOpacity = ((marker - RAIL_TOP) / 36f).coerceIn(0f, 1f)
            ink.strokeWidth = 2f
            val trailStart = maxOf(RAIL_TOP, marker - 224f)
            val solidStart = maxOf(trailStart, marker - 61f)
            val dashPhase = ((elapsed % 360L) / 360f) * 11f
            var y = trailStart - dashPhase
            while (y < solidStart) {
                val start = maxOf(y, trailStart)
                val end = minOf(y + 7f, solidStart)
                if (end > start) {
                    ink.color = tint(BLUE, edgeAlpha(start) * .92f)
                    canvas.drawLine(RAIL_X, start, RAIL_X, end, ink)
                }
                y += 11f
            }
            ink.color = tint(BLUE, markerOpacity)
            canvas.drawLine(RAIL_X, solidStart, RAIL_X, marker, ink)
            canvas.translate(RAIL_X, marker)
            val pulse = (1f - kotlin.math.cos(elapsed * (2.0 * Math.PI / 1_150L)).toFloat()) / 2f
            glow.alpha = ((195f + 60f * pulse) * markerOpacity).roundToInt()
            canvas.drawCircle(0f, 0f, 15f, glow)
            ink.color = tint(0xFF006AFF.toInt(), markerOpacity)
            canvas.drawCircle(0f, 0f, 5.5f, ink)
            ink.color = tint(Color.WHITE, markerOpacity)
            canvas.drawCircle(0f, 0f, 2.8f, ink)
            canvas.restoreToCount(clip)
        }
        for (row in stops) {
            val y = row.y - offset
            if (y in 25f..469f && abs(y - marker) > 7f) {
                drawNode(canvas, y, 3.5f, y < marker, edgeAlpha(y))
            }
        }
    }

    private fun drawNode(canvas: Canvas, y: Float, radius: Float, passed: Boolean, opacity: Float) {
        ink.color = tint(if (passed) BLUE else 0xFF8A8D91.toInt(), opacity)
        canvas.drawCircle(RAIL_X, y, radius, ink)
        ink.color = tint(if (passed) Color.WHITE else 0xFF121317.toInt(), opacity)
        canvas.drawCircle(RAIL_X, y, radius - 1f, ink)
    }

    private fun drawRow(canvas: Canvas, row: Row, y: Float, marker: Float) {
        val edge = edgeAlpha(y)
        val focus = (1f - abs(y - marker) / 158f).coerceIn(0f, 1f)
        val primary = tint(0xFFF2F2F4.toInt(), edge * (.40f + .60f * focus))
        val secondary = tint(0xFFACADB3.toInt(), edge * (.43f + .57f * focus))
        when (row.kind) {
            Kind.TITLE -> {
                row.heading.draw(canvas, y + 3f, tint(0xFFD6D6D9.toInt(), edge))
                row.caption?.draw(canvas, y + 28f, secondary)
            }
            Kind.DAY -> {
                row.heading.draw(canvas, y + 4f, primary)
                ink.color = tint(0xFF747780.toInt(), edge * .5f)
                ink.strokeWidth = .75f
                canvas.drawLine(TEXT_X + 47f, y, TEXT_X + 69f, y, ink)
                row.caption?.draw(canvas, y + 4f, secondary)
            }
            Kind.STOP -> {
                row.heading.draw(canvas, y + 6f, primary)
                row.caption?.draw(canvas, y + 24f, secondary)
            }
            Kind.NIGHT, Kind.RUNTIME -> {
                drawMomentIcon(canvas, row.kind, TEXT_X + 10f, y, edge)
                row.heading.draw(canvas, y + 4.5f, primary)
            }
            Kind.CLOSING -> {
                row.heading.draw(canvas, y - 17f, secondary)
                companionsLayout.paint.color = primary
                val saved = canvas.save()
                canvas.translate(TEXT_X, y + 10f - companionsLayout.getLineBaseline(0))
                companionsLayout.draw(canvas)
                canvas.restoreToCount(saved)
                val bottom = y + companionsExtraHeight
                row.caption?.draw(canvas, bottom + 33f, secondary)
                ending.draw(canvas, bottom + 84f, primary)
            }
        }
    }

    private fun drawMomentIcon(canvas: Canvas, kind: Kind, x: Float, y: Float, opacity: Float) {
        ink.color = tint(0xFF292B30.toInt(), opacity)
        canvas.drawCircle(x, y, 9.5f, ink)
        ink.color = tint(0xFFC6C7CB.toInt(), opacity)
        ink.strokeWidth = .85f
        ink.style = Paint.Style.STROKE
        if (kind == Kind.NIGHT) {
            canvas.drawArc(x - 4f, y - 4f, x + 4f, y + 4f, 8f, 260f, false, ink)
            canvas.drawArc(x - .8f, y - 6f, x + 6f, y + .8f, 95f, 144f, false, ink)
        } else {
            canvas.drawCircle(x, y, 4f, ink)
            canvas.drawLine(x, y, x, y - 2.5f, ink)
            canvas.drawLine(x, y, x + 2f, y + 1f, ink)
        }
        ink.style = Paint.Style.FILL
    }

    private fun drawMuteIcon(canvas: Canvas) {
        ink.color = 0xFF83848A.toInt()
        ink.strokeWidth = .75f
        ink.style = Paint.Style.STROKE
        icon.rewind()
        icon.moveTo(29f, 439f)
        icon.lineTo(31f, 439f)
        icon.lineTo(33f, 437f)
        icon.lineTo(33f, 443f)
        icon.lineTo(31f, 441f)
        icon.lineTo(29f, 441f)
        icon.close()
        canvas.drawPath(icon, ink)
        canvas.drawLine(35f, 439f, 37f, 441f, ink)
        canvas.drawLine(35f, 441f, 37f, 439f, ink)
        ink.style = Paint.Style.FILL
    }

    private fun drawPausedIcon(canvas: Canvas) {
        val x = 25.5f + VIDEO_WIDTH / 2f
        val y = 44.7f + VIDEO_HEIGHT / 2f
        ink.color = 0x8806090C.toInt()
        canvas.drawCircle(x, y, 18f, ink)
        ink.color = Color.WHITE
        icon.rewind()
        icon.moveTo(x - 4.5f, y - 7f)
        icon.lineTo(x + 7f, y)
        icon.lineTo(x - 4.5f, y + 7f)
        icon.close()
        canvas.drawPath(icon, ink)
    }

    private fun edgeAlpha(y: Float) =
        minOf(((y - 19f) / 56f).coerceIn(0f, 1f), ((474f - y) / 91f).coerceIn(0f, 1f))

    private fun tint(color: Int, opacity: Float) = (color and 0xFFFFFF) or ((255 * opacity).roundToInt() shl 24)

    private companion object {
        const val RAIL_X = 279.8f
        const val TEXT_X = 308.5f
        const val TEXT_RIGHT = 587f
        const val CLOSING_SPACE = 32f
        const val RAIL_TOP = 22f
        const val MARKER_START = 8f
        const val MARKER_CENTER = 236.8f
        const val START_DROP_MILLIS = 160L
        const val FIRST_STOP_MILLIS = 4_900L
        const val MARKER_DRIFT_MILLIS = 7_200L
        const val BLUE = 0xFF1876FF.toInt()
    }
}

@Suppress("ViewConstructor")
private class ScreeningVideo(
    context: Context, private val file: File?, private val onFailure: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var position = 0L
    private var shouldPlay = false
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        surfaceTextureListener = this
        isOpaque = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun sync(target: Long, play: Boolean, force: Boolean) {
        val jump = abs(target - position) > 240L
        val changed = shouldPlay != play
        position = target
        shouldPlay = play
        if (prepared && (force || jump || changed)) applyPlayback(seek = force || jump || play)
    }

    private fun applyPlayback(seek: Boolean) {
        val media = player ?: return
        if (!prepared) return
        runCatching {
            if (!shouldPlay && media.isPlaying) media.pause()
            if (seek) media.seekTo(position.coerceIn(0L, (media.duration - 1).coerceAtLeast(0).toLong()), MediaPlayer.SEEK_CLOSEST)
            if (shouldPlay && position < media.duration && !media.isPlaying) media.start()
        }.onFailure { Log.w("CreditScreening", "Unable to synchronize video", it) }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val output = Surface(texture)
        surface = output
        val media = MediaPlayer()
        player = media
        runCatching {
            if (file != null) media.setDataSource(file.absolutePath)
            else context.assets.openFd("b3/v2/cat.mp4").use {
                media.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            media.setSurface(output)
            media.setVolume(0f, 0f)
            media.setOnVideoSizeChangedListener { _, w, h ->
                videoWidth = w
                videoHeight = h
                fit()
            }
            media.setOnPreparedListener {
                if (player === it) {
                    prepared = true
                    applyPlayback(seek = true)
                }
            }
            media.setOnErrorListener { failed, what, extra ->
                Log.w("CreditScreening", "Video decoder error $what/$extra")
                if (player === failed) { release(); onFailure() }
                true
            }
            media.prepareAsync()
        }.onFailure {
            Log.w("CreditScreening", "Unable to open video", it)
            release()
            onFailure()
        }
    }

    fun fit() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val scale = maxOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        setTransform(Matrix().apply {
            setScale(scale * videoWidth / width, scale * videoHeight / height, width / 2f, height / 2f)
        })
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = fit()
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    private fun release() {
        prepared = false
        player?.release()
        player = null
        surface?.release()
        surface = null
    }
}