package com.example.pleos_endingcredit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.caverock.androidsvg.SVG
import kotlin.math.min
import kotlin.math.roundToInt

/** B3 application content on the 680 × 474 Figma artboard. */
@SuppressLint("ViewConstructor")
internal class B3CreditView(
    context: Context,
    private val content: CreditContent,
    val frame: Int,
    initialElapsedMillis: Long = 0,
    private var muted: Boolean = true,
    initialPaused: Boolean = false,
    private val onTime: (Long) -> Unit = {},
    private val onChapter: (Int) -> Unit = {},
    private val onComplete: () -> Unit = {},
    private val onMute: (Boolean) -> Unit = {},
    private val onPause: (Boolean) -> Unit = {},
) : ViewGroup(context) {
    private data class Slot(val view: View, val x: Float, val y: Float, val w: Float, val h: Float,
        val fontSize: Float = 0f)

    private val scene = CreditPlayback.index(frame)
    private val duration = CreditPlayback.duration(frame)
    private val lightMap = scene != CreditPlayback.FILM
    private val mapInk = 0xFF15394E.toInt()
    private val textColor = if (lightMap) mapInk else Color.WHITE
    private val secondaryColor = if (lightMap) 0xFF36566A.toInt() else SECONDARY
    private val borderColor = if (lightMap) 0xFF587684.toInt() else MUTED
    private val speakerTint = Paint().apply {
        colorFilter = PorterDuffColorFilter(mapInk, PorterDuff.Mode.SRC_IN)
    }
    private val slots = mutableListOf<Slot>()
    private val card = RectF()
    private val clip = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val normal = ResourcesCompat.getFont(context, R.font.noto_sans_kr_regular)!!
    private val bold = ResourcesCompat.getFont(context, R.font.noto_sans_kr_bold)!!
    private val medium = android.graphics.Typeface.create(normal, 500, false)
    private val map by lazy(LazyThreadSafetyMode.NONE) { CreditMap(context, content) }
    private val isScreening = scene == CreditPlayback.FILM &&
        (content.trip.creditVideo == CreditVideoChoice.NONE || content.trip.creditVideo == CreditVideoChoice.UPLOADED)
    // The final Figma board labels this B2 “크레딧 상영”. Navigation keeps B3-3 to distinguish it from preparation.
    private val screening = if (isScreening) B3_3ScreeningView(context, content, normal, bold, false) {
        setPaused(!paused)
    } else null
    private val film = if (scene == CreditPlayback.FILM && !isScreening) CreditFilmView(context, content, medium, bold) else null
    private val handler = Handler(Looper.getMainLooper())
    private var scale = 1f
    private var allowed = false
    private var paused = initialPaused
    private var anchor = 0L
    private var running = false
    private var completed = false
    var elapsedMillis = initialElapsedMillis.coerceIn(0, duration)
        private set
    private var baseMillis = elapsedMillis
    private var routeCaption: TextView? = null
    private var filmCaption: TextView? = null
    private val nextButton: View
    private val odometer: TextView
    private val sound: View
    private val pauseLabel: TextView
    private val speaker by lazy(LazyThreadSafetyMode.NONE) {
        SVG.getFromAsset(context.assets, "b3/223c4.svg").renderToPicture()
    }
    private val chrome = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val saved = canvas.save()
            canvas.scale(width / WIDTH, height / HEIGHT)
            drawChrome(canvas)
            canvas.restoreToCount(saved)
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            readClock()
            onTime(elapsedMillis)
            if (elapsedMillis >= duration) {
                running = false
                updateVisuals()
                if (!completed) {
                    completed = true
                    onComplete()
                }
            } else {
                updateVisuals()
                handler.postDelayed(this, 33L)
            }
        }
    }

    init {
        tag = "b3.credit"
        setWillNotDraw(false)
        setBackgroundColor(BACKGROUND)
        if (android.os.Build.VERSION.SDK_INT >= 29) isForceDarkAllowed = false
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "N딩 크레딧 ${scene + 1} / 4 · ${CreditPlayback.titles[scene]}"

        if (isScreening) contentDescription = "N딩 크레딧 상영"
        (screening ?: film)?.let { addSlot(it, 0f, 0f, WIDTH, HEIGHT) }
        addSlot(chrome, 0f, 0f, WIDTH, HEIGHT)
        when (scene) {
            CreditPlayback.OPENING -> {
                openingLabel("b3.eyebrow", "차량에 고스란히 담긴", 16f, secondaryColor, 92.36f, 23f)
                openingLabel("b3.title", content.title, 32f, mapInk, 123.36f, 46f, 700)
                openingLabel("b3.date", content.dateText, 18f, secondaryColor, 177.36f, 23f)
            }
            CreditPlayback.ROUTE -> if (!map.hasRoute) {
                addSlot(label("b3.routeEmpty", "남겨진 경로가 없어요", 20f, secondaryColor), 100f, 212f, 480f, 40f, 20f)
            }
            CreditPlayback.SUMMARY -> {
                val message = if (content.distanceKm != null) "${content.distanceText}km를 달려왔어요" else "주행거리 기록이 없어요"
                routeCaption = label("b3.routeCaption", message, 44f, mapInk, 700).apply {
                    typeface = ResourcesCompat.getFont(context, R.font.gowun_batang_bold)
                }.also { addSlot(it, 36f, 205f, 608f, 64f, 44f) }
            }
            CreditPlayback.FILM -> if (!isScreening) {
                val caption = if (content.demo) "함께 탄 사람이 올린 영상 한 개" else "우리의 장면"
                filmCaption = label("b3.filmCaption", caption, 16f, Color.WHITE, 500).also {
                    addSlot(it, 0f, 289f, WIDTH, 23f, 16f)
                }
            }
        }

        if (!isScreening) CreditPlayback.frames.forEachIndexed { index, target ->
            addSlot(button("b3.chapter.$target", "${index + 1}장 · ${CreditPlayback.titles[index]}") {
                if (index != scene) onChapter(target)
            }.apply { isSelected = index == scene }, BAR_X[index], 0f, BAR_WIDTH[index], 15f)
        }

        val opening = scene == CreditPlayback.OPENING
        val speakerY = if (opening) 420.7f else 18f
        sound = button("b3.mute", if (muted) "소리 켜기" else "소리 끄기", ::toggleMute).apply { isSelected = muted }
        if (!isScreening) addSlot(sound, 622f, speakerY - 10f, 48f, 48f)

        val odoX = if (opening) 286f else 552f
        val odoY = if (opening) 414.86f else 428f
        odometer = label("b3.odometer", "", if (isScreening) 22f else 15f,
            if (isScreening) 0xFFD6D6D9.toInt() else secondaryColor, if (isScreening) 700 else 500).apply {
            if (isScreening) gravity = Gravity.END else {
                letterSpacing = 2f / 15f
                gravity = Gravity.START
            }
        }
        val nextX = if (opening) 566.15f else 530f
        val final = scene == CreditPlayback.FILM
        nextButton = button("b3.next", if (final) "결과 보기" else "다음 장면으로 건너뛰기", ::advanceScene)
        if (isScreening) {
            addSlot(label("b3.distance.heading", "주행 거리", 8.5f, 0xFF9C9DA4.toInt()).apply {
                gravity = Gravity.END
            }, 584f, 412f, 70f, 13f, 8.5f)
            addSlot(odometer, 548f, 425f, 90f, 31f, 22f)
            addSlot(label("b3.unit", "km", 9f, 0xFF9C9DA4.toInt()), 640f, 437f, 16f, 12f, 9f)
            addSlot(nextButton, 582f, 16f, 85f, 37f)
            addSlot(label("b3.next.label", "결과 보기", 10.5f, 0xFFD6D6D9.toInt(), 500)
                .apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }, 595f, 28f, 45f, 14f, 10.5f)
            addSlot(label("b3.next.chevron", "›", 12.5f, 0xFFD6D6D9.toInt())
                .apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }, 644f, 26.5f, 7f, 16f, 12.5f)
        } else {
            addSlot(odometer, odoX + 11f, odoY + 5f, 68f, 20f, 15f)
            addSlot(label("b3.unit", "km", 12f, borderColor), odoX + 79f, odoY + 7f, 18f, 18f, 12f)
            addSlot(nextButton, nextX, 16f, 88f, 40f)
            addSlot(label("b3.next.label", if (final) "결과 보기" else "다음 장면", 13f, textColor, 500)
                .apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }, nextX + 15f, 24f, 51f, 16f, 13f)
            addSlot(label("b3.next.chevron", "›", 15f, textColor, 500)
                .apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }, nextX + 70f, 23f, 6f, 18f, 15f)
        }
        pauseLabel = label("b3.paused", "일시 정지 · 재생 키로 계속", 14f, if (lightMap) mapInk else FOREGROUND).apply {
            visibility = if (paused) VISIBLE else INVISIBLE
        }
        if (!isScreening) addSlot(pauseLabel, 190f, 378f, 300f, 24f, 14f)
        updateVisuals()
    }

    private fun openingLabel(tag: String, copy: String, size: Float, color: Int, y: Float, h: Float, weight: Int = 400) {
        addSlot(label(tag, copy, size, color, weight), 40f, y, 600f, h, size)
    }

    private fun toggleMute() {
        muted = !muted
        sound.contentDescription = if (muted) "소리 켜기" else "소리 끄기"
        sound.isSelected = muted
        onMute(muted)
        chrome.invalidate()
    }

    private fun advanceScene() {
        if (completed) return
        freeze()
        completed = true
        CreditPlayback.nextFrame(frame)?.let(onChapter) ?: onComplete()
    }

    fun setPlaybackAllowed(value: Boolean) {
        if (allowed == value) return
        freeze()
        allowed = value
        updateClock()
    }

    fun setPaused(value: Boolean) {
        freeze()
        paused = value
        pauseLabel.visibility = if (paused) VISIBLE else INVISIBLE
        onPause(paused)
        updateClock()
    }

    fun seekTo(value: Long) {
        freeze()
        elapsedMillis = value.coerceIn(0, duration)
        baseMillis = elapsedMillis
        completed = false
        onTime(elapsedMillis)
        updateVisuals(seekVideo = true)
        updateClock()
    }

    private fun readClock() {
        elapsedMillis = (baseMillis + SystemClock.uptimeMillis() - anchor).coerceAtMost(duration)
    }

    private fun freeze() {
        if (running) readClock()
        running = false
        handler.removeCallbacks(tick)
        baseMillis = elapsedMillis
        onTime(elapsedMillis)
        updateVisuals()
    }

    private fun updateClock() {
        if (!running && !completed && allowed && !paused && isAttachedToWindow &&
            isShown && hasWindowFocus() && windowVisibility == VISIBLE) {
            anchor = SystemClock.uptimeMillis()
            baseMillis = elapsedMillis
            running = true
            handler.post(tick)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateClock() }
    override fun onDetachedFromWindow() { freeze(); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) updateClock() else freeze()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) updateClock() else freeze()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { setPaused(!paused); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> { if (!isScreening) CreditPlayback.frames.getOrNull(scene - 1)?.let(onChapter); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { advanceScene(); true }
        else -> super.onKeyUp(keyCode, event)
    }

    private fun updateVisuals(seekVideo: Boolean = false) {
        val measured = content.odometer(frame, elapsedMillis)
        val value = if (isScreening) measured.toIntOrNull()?.toString() ?: "—" else measured
        if (odometer.text.toString() != value) {
            odometer.text = value
            odometer.contentDescription = if (measured == "------") "주행 거리 기록 없음"
                else "주행 거리 ${value.trimStart('0').ifEmpty { "0" }}킬로미터"
        }
        routeCaption?.apply {
            alpha = CreditPlayback.phase(elapsedMillis, 50L, 590L)
            translationY = (1f - alpha) * 8f * scale
        }
        filmCaption?.alpha = CreditPlayback.phase(elapsedMillis, 0L, 280L) *
            (1f - CreditPlayback.phase(elapsedMillis, 2_620L, 3_030L))
        film?.update(elapsedMillis, running, seekVideo)
        screening?.update(elapsedMillis, running, paused, seekVideo)
        chrome.invalidate()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = resolveSize((width * HEIGHT / WIDTH).roundToInt(), heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(width, height)
        scale = min(width / WIDTH, height / HEIGHT)
        card.set((width - WIDTH * scale) / 2, (height - HEIGHT * scale) / 2,
            (width + WIDTH * scale) / 2, (height + HEIGHT * scale) / 2)
        clip.rewind()
        clip.addRoundRect(card, 16 * scale, 16 * scale, Path.Direction.CW)
        slots.forEach { slot ->
            (slot.view as? ReferenceDesignLabel)?.let { label ->
                val textPaint = TextPaint(label.paint).apply { textSize = slot.fontSize }
                val copy = if (label === odometer && !isScreening) "000000" else label.text.toString()
                val size = slot.fontSize * min(1f, (slot.w - 2f) / textPaint.measureText(copy).coerceAtLeast(1f))
                val baseline = if (label === routeCaption) 48f else size * .97f
                label.setReferenceTypography(size, slot.h, baseline, scale)
                if (scene == CreditPlayback.OPENING && label.tag.toString() in OPENING_LABELS) {
                    label.setShadowLayer(4f * scale, 0f, scale, Color.WHITE)
                }
            }
            slot.view.measure(exact(px(slot.w)), exact(px(slot.h)))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        slots.forEach { slot ->
            val x = (card.left + slot.x * scale).roundToInt()
            val y = (card.top + slot.y * scale).roundToInt()
            slot.view.layout(x, y, x + slot.view.measuredWidth, y + slot.view.measuredHeight)
        }
        routeCaption?.let { it.translationY = (1f - it.alpha) * 8f * scale }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (film != null || screening != null) return
        val saved = canvas.save()
        canvas.clipPath(clip)
        canvas.translate(card.left, card.top)
        canvas.scale(scale, scale)
        map.draw(canvas, scene, elapsedMillis)
        map.drawAttribution(canvas)
        canvas.restoreToCount(saved)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.clipPath(clip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
        paint.style = Paint.Style.STROKE
        val stroke = if (isScreening) 0.8f else 3f
        paint.strokeWidth = stroke * scale
        paint.color = if (lightMap) borderColor else if (isScreening) 0xFF444444.toInt() else 0xFF555555.toInt()
        val inset = stroke * scale / 2f
        val radius = (16f - stroke / 2f) * scale
        canvas.drawRoundRect(card.left + inset, card.top + inset, card.right - inset,
            card.bottom - inset, radius, radius, paint)
    }

    private fun drawChrome(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        if (isScreening) {
            paint.color = 0xFF3D3E44.toInt()
            canvas.drawRoundRect(19f, 11f, 661f, 12.65f, .825f, .825f, paint)
            paint.color = 0xFFCECED2.toInt()
            canvas.drawRoundRect(19f, 11f, 19f + 642f * elapsedMillis / duration, 12.65f, .825f, .825f, paint)
            paint.color = if (nextButton.isPressed) 0xFF35363C.toInt() else 0xFF212226.toInt()
            canvas.drawRoundRect(590f, 22.5f, 661f, 46.5f, 8f, 8f, paint)
            if (nextButton.isFocused) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = 0xFFD6D6D9.toInt()
                canvas.drawRoundRect(590f, 22.5f, 661f, 46.5f, 8f, 8f, paint)
            }
            return
        }
        for (i in BAR_X.indices) {
            val x = BAR_X[i]
            paint.color = if (lightMap) 0x5515394E else 0x55FFFFFF
            canvas.drawRoundRect(x, 8f, x + BAR_WIDTH[i], 12f, 2f, 2f, paint)
            val fill = when {
                i < scene -> 1f
                i == scene -> elapsedMillis.toFloat() / duration
                else -> 0f
            }
            if (fill > 0) {
                paint.color = textColor
                canvas.drawRoundRect(x, 8f, x + BAR_WIDTH[i] * fill, 12f, 2f, 2f, paint)
            }
        }
        val opening = scene == CreditPlayback.OPENING
        val speakerY = if (opening) 420.7f else 18f
        drawSpeaker(canvas, speaker, speakerY)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = borderColor
        val odoX = if (opening) 286f else 552f
        val odoY = if (opening) 414.86f else 428f
        if (lightMap) {
            paint.style = Paint.Style.FILL
            paint.color = 0xDDF4FAFB.toInt()
            canvas.drawRoundRect(odoX, odoY, odoX + 108f, odoY + 28f, 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = borderColor
        }
        canvas.drawRoundRect(odoX, odoY, odoX + 108f, odoY + 28f, 6f, 6f, paint)
        val x = if (opening) 566.15f else 530f
        paint.style = Paint.Style.FILL
                paint.color = if (lightMap) {
            if (nextButton.isPressed) 0xFFCDDFE7.toInt() else 0xE8F4FAFB.toInt()
        } else if (nextButton.isPressed) 0x33FFFFFF else 0x1FFFFFFF
        canvas.drawRoundRect(x, 18f, x + 88f, 46f, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (nextButton.isFocused) 2f else 1f
        paint.color = if (nextButton.isFocused) textColor else if (lightMap) borderColor else 0x59FFFFFF
        canvas.drawRoundRect(x + .5f, 18.5f, x + 87.5f, 45.5f, 16f, 16f, paint)
    }

    private fun drawSpeaker(canvas: Canvas, picture: Picture, y: Float) {
        val saved = canvas.save()
        canvas.translate(632f, y)
        if (lightMap) {
            val layer = canvas.saveLayer(0f, 0f, 28f, 28f, speakerTint)
            canvas.drawPicture(picture)
            canvas.restoreToCount(layer)
        } else canvas.drawPicture(picture)
        if (muted) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = secondaryColor
            canvas.drawLine(3f, 3f, 25f, 25f, paint)
        }
        canvas.restoreToCount(saved)
    }

    private fun label(tagName: String, copy: String, size: Float, color: Int, weight: Int = 400) =
        ReferenceDesignLabel(context).apply {
            tag = tagName
            text = copy
            setTextColor(color)
            typeface = if (weight >= 700) bold else if (weight == 500) medium else normal
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER_HORIZONTAL
            setReferenceTypography(size, size * 1.5f, size * 1.1f, 1f)
        }

    private fun button(tagName: String, description: String, action: () -> Unit) = View(context).apply {
        tag = tagName
        contentDescription = description
        isClickable = true
        isFocusable = true
        addIconRipple(0x26FFFFFF)
        setOnClickListener { action() }
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        }
    }

    private fun addSlot(view: View, x: Float, y: Float, w: Float, h: Float, size: Float = 0f) {
        slots += Slot(view, x, y, w, h, size)
        addView(view)
    }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(1), MeasureSpec.EXACTLY)
    private fun px(value: Float) = (value * scale).roundToInt()

    private companion object {
        const val WIDTH = 680f
        const val HEIGHT = 474f
        const val BACKGROUND = 0xFF2B2B2B.toInt()
        const val FOREGROUND = 0xFFEDEDED.toInt()
        const val SECONDARY = 0xFFBABABA.toInt()
        const val MUTED = 0xFF8C8C8C.toInt()
        val BAR_X = floatArrayOf(24f, 102.4f, 205.6f, 259.2f)
        val BAR_WIDTH = floatArrayOf(74.4f, 99.2f, 49.6f, 396.8f)
        val OPENING_LABELS = setOf("b3.eyebrow", "b3.title", "b3.date")
    }
}

