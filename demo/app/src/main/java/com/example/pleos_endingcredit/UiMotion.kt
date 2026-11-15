package com.example.pleos_endingcredit

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import kotlin.math.roundToInt

internal const val PAGE_AND_POPUP_TRANSITION_MS = 450L

internal val EnterEase = PathInterpolator(.2f, 0f, 0f, 1f)
internal val SceneEase = PathInterpolator(.4f, 0f, .2f, 1f)
private val ExitEase = PathInterpolator(.4f, 0f, 1f, 1f)

/** Keeps a closing popup drawable until its fade ends and removes it from input immediately. */
internal class PopupMotion(private val host: ViewGroup) {
    private var current: View? = null
    private var currentPanel: View? = null
    private var exiting: View? = null

    fun show(
        overlay: View,
        params: ViewGroup.LayoutParams? = null,
        panel: View? = null,
        animate: Boolean = true,
    ) {
        val replacing = current != null
        dismiss(animate = false)
        current = overlay
        currentPanel = panel
        val animated = !replacing && animate && canAnimate()
        if (animated) {
            overlay.alpha = 0f
            panel?.translationY = panelOffset()
        }
        if (params == null) host.addView(overlay) else host.addView(overlay, params)
        if (animated) {
            overlay.popupAnimator().alpha(1f).setInterpolator(EnterEase).setDuration(PAGE_AND_POPUP_TRANSITION_MS).start()
            panel?.animate()?.translationY(0f)?.setInterpolator(EnterEase)?.setDuration(PAGE_AND_POPUP_TRANSITION_MS)?.start()
        }
    }

    fun dismiss(animate: Boolean = true) {
        finishExit()
        val overlay = current ?: return
        val panel = currentPanel
        current = null
        currentPanel = null
        overlay.animate().cancel()
        panel?.animate()?.cancel()
        if (!animate || !canAnimate() || overlay.alpha == 0f) {
            host.removeView(overlay)
            return
        }

        overlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        host.startViewTransition(overlay)
        exiting = overlay
        host.removeView(overlay)
        overlay.popupAnimator().alpha(0f).setInterpolator(ExitEase).setDuration(PAGE_AND_POPUP_TRANSITION_MS)
            .withEndAction { if (exiting === overlay) finishExit() }.start()
    }

    fun settle() {
        finishExit()
        current?.let {
            it.animate().cancel()
            it.alpha = 1f
            (it as? PopupFadeListener)?.onPopupAlpha(1f)
        }
        currentPanel?.let {
            it.animate().cancel()
            it.translationY = 0f
        }
    }

    private fun finishExit() {
        val overlay = exiting ?: return
        exiting = null
        overlay.animate().cancel()
        host.endViewTransition(overlay)
    }

    private fun canAnimate() = host.isAttachedToWindow && host.isLaidOut && ValueAnimator.areAnimatorsEnabled()

    private fun panelOffset() = 10f * host.resources.displayMetrics.density
}

internal interface PopupFadeListener {
    fun onPopupAlpha(alpha: Float)
}

private fun View.popupAnimator() = animate().apply {
    val listener = this@popupAnimator as? PopupFadeListener
    if (listener != null) setUpdateListener { listener.onPopupAlpha(this@popupAnimator.alpha) }
}

/** Updates blur in a few steps so radius changes do not allocate an effect every frame. */
internal class PopupBackdropBlur(private val backdrop: View?) {
    private var appliedRadius = 0f

    fun update(radius: Float, alpha: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || backdrop == null) return
        val visibleRadius = radius * (alpha * 5f).roundToInt() / 5f
        if (visibleRadius == appliedRadius) return
        appliedRadius = visibleRadius
        backdrop.setRenderEffect(if (visibleRadius > 0f)
            RenderEffect.createBlurEffect(visibleRadius, visibleRadius, Shader.TileMode.CLAMP)
        else null)
    }
}

internal fun View.addPressMotion() {
    if (!ValueAnimator.areAnimatorsEnabled()) return
    fun scaleTo(scale: Float, durationMillis: Long) = ObjectAnimator.ofPropertyValuesHolder(
        this,
        PropertyValuesHolder.ofFloat(View.SCALE_X, scale),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, scale),
    ).apply {
        duration = durationMillis
        interpolator = EnterEase
    }
    stateListAnimator = StateListAnimator().apply {
        addState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed), scaleTo(.98f, 90L))
        addState(intArrayOf(), scaleTo(1f, 150L))
    }
}

internal fun View.addIconRipple(color: Int) {
    val mask = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = 16f * resources.displayMetrics.density
    }
    foreground = RippleDrawable(ColorStateList.valueOf(color), null, mask)
}
