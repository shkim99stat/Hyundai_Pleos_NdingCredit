package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton

/** Bundled Asta Sans; no network font request or system-family substitution. */
internal object AppTypography {
    private val faces = mutableMapOf<Int, Typeface>()

    @Synchronized
    fun font(context: Context, weight: Int = 400): Typeface = faces.getOrPut(weight) {
        val family = requireNotNull(ResourcesCompat.getFont(context, R.font.asta_sans))
        Typeface.create(family, weight, false)
    }

    fun applyTo(view: View) {
        // These surfaces own the source JSON's per-node families and weights.
        if (view is ResultDesignSurface || view is B3CreditView || view is A4RecordingView || view is A0ConsentView || view is A1PayoutSetupView ||
            view is A2LegacyTravelHomeView ||
            view is A3TripStartView || view is C1TripResultView ||
            view is B2CreditPreparingView || view is ReferenceArchiveView) return
        if (view is TextView) {
            val currentWeight = view.typeface?.weight ?: 400
            val weight = when {
                view is MaterialButton -> 600
                currentWeight >= 700 -> currentWeight
                view.typeface?.isBold == true -> 700
                else -> 400
            }
            view.typeface = font(view.context, weight)
            view.textScaleX = 1f
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyTo(view.getChildAt(index))
        }
    }
}
