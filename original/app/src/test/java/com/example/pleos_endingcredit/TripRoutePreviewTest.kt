package com.example.pleos_endingcredit

import org.junit.Assert.*
import org.junit.Test

class TripRoutePreviewTest {
    @Test fun longStraightTripGetsASmallPreviewWithoutChangingOriginalSamples() {
        val originals=(0..12_000).map { TripPoint(37.0,127.0+it*.00001,1_000L+it*1000) }
        val preview=TripRoutePreview.compact(originals)
        assertEquals(12_001,originals.size)
        assertEquals(originals.first(),preview.first())
        assertEquals(originals.last(),preview.last())
        assertTrue(preview.size<=TripRoutePreview.MAX_POINTS)
    }
    @Test fun sharpTurnsAndBothSidesOfMissingIntervalsSurviveSimplification() {
        val corner=TripPoint(37.0,127.001,2000)
        val beforeGap=TripPoint(37.001,127.001,3000)
        val afterGap=TripPoint(37.01,127.01,100000,gapBefore=true)
        val points=listOf(TripPoint(37.0,127.0,1000),corner,beforeGap,afterGap,
            TripPoint(37.01,127.011,101000),TripPoint(37.01,127.012,102000))
        val preview=TripRoutePreview.simplify(points,2.0)
        assertTrue(preview.contains(corner));assertTrue(preview.contains(beforeGap))
        assertTrue(preview.contains(afterGap));assertTrue(preview.first { it.at==afterGap.at }.gapBefore)
    }
    @Test fun extremelyFragmentedPreviewIsBoundedAndCannotJoinOmittedSections() {
        val points=(0..3000).map { TripPoint(37.0,127.0+it*.00001,1000L+it*1000,it>0) }
        val preview=TripRoutePreview.compact(points)
        assertEquals(points.first(),preview.first());assertEquals(points.last(),preview.last())
        assertTrue(preview.size<=TripRoutePreview.MAX_POINTS)
        assertTrue(preview.drop(1).all { it.gapBefore })
    }
}
