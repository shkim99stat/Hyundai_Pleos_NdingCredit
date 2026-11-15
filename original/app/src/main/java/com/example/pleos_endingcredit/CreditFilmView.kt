package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keeps the opening video playing in the same surface while its visible area narrows. */
internal class CreditFilmView(
    context: Context,
    content: CreditContent,
    medium: Typeface,
    bold: Typeface,
) : ViewGroup(context) {
    private val roll = CreditRollView(context, content, medium, bold)
    private val useLegacyVideo = content.trip.creditVideo == null
    private val uploaded = if (content.trip.creditVideo == CreditVideoChoice.UPLOADED) {
        CreditVideoFiles(context).committed(content.trip.id).takeIf(File::isFile)?.let { file ->
            CreditVideo(context, "uploaded", file).apply { tag = "b3.video.uploaded" }
        }
    } else null
    private val bridge = if (useLegacyVideo) CreditVideo(context, "bridge.mp4").apply { tag = "b3.video.bridge" } else null
    private val cat = if (useLegacyVideo) CreditVideo(context, "cat.mp4").apply { tag = "b3.video.cat"; alpha = 0f } else null
    private val rollOnly = !useLegacyVideo && uploaded == null
    private val crop = Rect()
    private var elapsed = 0L
    private var windowWidth = -1
    private var windowHeight = -1

    init {
        tag = "b3.film"
        setBackgroundColor(PANEL)
        addView(roll)
        bridge?.let(::addView)
        cat?.let(::addView)
        uploaded?.let(::addView)
    }

    fun update(elapsedMillis: Long, playing: Boolean, seek: Boolean) {
        elapsed = elapsedMillis
        updateCrop()
        if (bridge != null && cat != null) {
            val mix = ease(CreditPlayback.phase(elapsed, 4_150L, 4_700L))
            cat.alpha = mix
            bridge.alpha = if (mix >= 1f) 0f else 1f
            bridge.sync(elapsed.coerceAtMost(4_700L), playing && elapsed < 4_700L, seek)
            cat.sync((elapsed - 4_150L).coerceAtLeast(0), playing && elapsed >= 4_150L, seek)
        }
        uploaded?.sync(elapsed, playing, seek)
        roll.update(elapsed, rollOnly)
    }

    private fun updateCrop() {
        val video = uploaded ?: bridge ?: return
        if (width == 0 || height == 0) return
        val progress = ease(CreditPlayback.phase(elapsed, 3_000L, 4_000L))
        val half = width / 2
        val visible = width - ((width - half) * progress).roundToInt()
        if (windowWidth == visible && windowHeight == height && crop.left == (width - visible) / 2) return
        windowWidth = visible
        windowHeight = height
        val start = (width - visible) / 2
        crop.set(start, 0, start + visible, height)
        video.clipBounds = crop
        video.translationX = -start.toFloat()
        uploaded?.setVisibleWidth(visible)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val scale = w / 680f
        roll.measure(exact((290f * scale).roundToInt()), exact((380f * scale).roundToInt()))
        bridge?.measure(exact(w), exact(h))
        cat?.measure(exact(w / 2), exact(h))
        uploaded?.measure(exact(w), exact(h))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val scale = width / 680f
        val x = if (rollOnly) (width - roll.measuredWidth) / 2 else (360f * scale).roundToInt()
        val y = (56f * scale).roundToInt()
        roll.layout(x, y, x + roll.measuredWidth, y + roll.measuredHeight)
        bridge?.layout(0, 0, width, height)
        cat?.layout(0, 0, width / 2, height)
        uploaded?.layout(0, 0, width, height)
        updateCrop()
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)
    private fun ease(t: Float) = t * t * (3f - 2f * t)

    private companion object {
        const val PANEL = 0xFF2B2B2B.toInt()
    }
}

@Suppress("ViewConstructor")
private class CreditVideo(
    context: Context,
    private val asset: String,
    private val file: File? = null,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var position = 0L
    private var shouldPlay = false
    private var videoWidth = 0
    private var videoHeight = 0
    private var visibleWidth = 0

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
            if (seek) {
                val time = position.coerceIn(0L, (media.duration - 1).coerceAtLeast(0).toLong())
                media.seekTo(time, MediaPlayer.SEEK_CLOSEST)
            }
            if (shouldPlay && !media.isPlaying && (file == null || position < media.duration)) media.start()
        }.onFailure { Log.w("CreditVideo", "Unable to synchronize B3 video", it) }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val output = Surface(texture)
        surface = output
        val media = MediaPlayer()
        player = media
        runCatching {
            if (file != null) {
                media.setDataSource(file.absolutePath)
                media.setOnVideoSizeChangedListener { _, w, h ->
                    videoWidth = w
                    videoHeight = h
                    fitUploadedVideo()
                }
            } else {
                context.assets.openFd("b3/v2/$asset").use { assetFile ->
                    media.setDataSource(assetFile.fileDescriptor, assetFile.startOffset, assetFile.length)
                }
            }
            media.setSurface(output)
            media.setVolume(0f, 0f)
            media.setOnPreparedListener {
                if (player === it) {
                    prepared = true
                    applyPlayback(seek = position > 0)
                }
            }
            media.setOnErrorListener { failed, what, extra ->
                Log.w("CreditVideo", "B3 video decoder error $what/$extra")
                if (player === failed) releasePlayer()
                true
            }
            media.prepareAsync()
        }.onFailure {
            Log.w("CreditVideo", "Unable to open B3 video", it)
            releasePlayer()
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        fitUploadedVideo()
    }

    fun setVisibleWidth(value: Int) {
        visibleWidth = value
        fitUploadedVideo()
    }

    private fun fitUploadedVideo() {
        if (file == null || width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val fit = maxOf(visibleWidth.toFloat() / videoWidth, height.toFloat() / videoHeight)
        setTransform(Matrix().apply {
            setScale(fit * videoWidth / width, fit * videoHeight / height, width / 2f, height / 2f)
        })
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    private fun releasePlayer() {
        prepared = false
        player?.release()
        player = null
        surface?.release()
        surface = null
    }
}

private class CreditRollView(context: Context, content: CreditContent, medium: Typeface, bold: Typeface) : View(context) {
    private data class Row(val copy: String, val y: Float, val paint: android.text.TextPaint)

    private val panel = 0xFF2B2B2B.toInt()
    private val topFade = LinearGradient(0f, 0f, 0f, 60f, panel, 0x002B2B2B, Shader.TileMode.CLAMP)
    private val bottomFade = LinearGradient(0f, 320f, 0f, 380f, 0x002B2B2B, panel, Shader.TileMode.CLAMP)
    private val gradient = Paint()
    private val heading = textPaint(10f, 0xFF9AA1AC.toInt(), medium)
    private val entry = textPaint(14f, Color.WHITE, bold)
    private val rows = mutableListOf<Row>()
    private var offset = 330f
    private var finalOffset = -200f

    init {
        tag = "b3.roll"
        var top = 0f
        fun section(title: String, values: List<String>) {
            if (values.isEmpty()) return
            rows += Row(title, top, heading)
            top += 16f
            for (value in values) {
                rows += Row(value, top, entry)
                top += 25f
            }
            top += 16f
        }
        section("다녀온 곳", content.stops.map(CreditStop::name))
        section("이번 여행의 장면", content.moments)
        top += 24f
        section("함께한 사람", listOf(content.companionsText))
        val closing = top + 25f
        rows += Row("편집", closing, textPaint(10f, Color.WHITE, bold))
        rows += Row("N딩 크레딧", closing + 19f, textPaint(19f, Color.WHITE, bold))
        rows += Row("THE END", closing + 57f, textPaint(26f, Color.WHITE, bold))
        finalOffset = 183f - (closing + 57f)
        contentDescription = rows.joinToString(", ") { it.copy }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun update(elapsed: Long, rollOnly: Boolean) {
        val start = if (rollOnly) 0L else 3_000L
        val entry = CreditPlayback.phase(elapsed, start, start + 1_000L)
        val travel = CreditPlayback.phase(elapsed, start + 1_000L, 20_550L)
        offset = 330f - 45f * (1f - (1f - entry) * (1f - entry)) + (finalOffset - 285f) * travel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.scale(width / 290f, height / 380f)
        canvas.clipRect(0f, 0f, 290f, 380f)
        for (row in rows) {
            val y = offset + row.y
            if (y < -32f || y > 380f) continue
            val fit = android.text.TextUtils.ellipsize(row.copy, row.paint, 276f, android.text.TextUtils.TruncateAt.END)
            canvas.drawText(fit, 0, fit.length, 145f, y - row.paint.ascent(), row.paint)
        }
        gradient.shader = topFade
        canvas.drawRect(0f, 0f, 290f, 60f, gradient)
        gradient.shader = bottomFade
        canvas.drawRect(0f, 320f, 290f, 380f, gradient)
        canvas.restoreToCount(saved)
    }

    private fun textPaint(size: Float, color: Int, face: Typeface) = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        textAlign = Paint.Align.CENTER
        typeface = face
        this.color = color
    }
}

