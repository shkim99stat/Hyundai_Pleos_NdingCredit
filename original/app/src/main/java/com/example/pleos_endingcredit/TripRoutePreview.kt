package com.example.pleos_endingcredit

import java.util.ArrayDeque
import kotlin.math.*

/** Display-only simplification. Never passed back to the raw sample store. */
internal object TripRoutePreview {
    const val MAX_POINTS = 2_000

    fun compact(points: List<TripPoint>, limit: Int = MAX_POINTS): List<TripPoint> {
        require(limit >= 2)
        if (points.size <= limit) return points
        var tolerance = 2.0
        var result = points
        repeat(20) {
            result = simplify(points, tolerance)
            if (result.size <= limit) return result
            tolerance *= 2
        }
        // Extremely fragmented input may have more segment ends than the budget.
        // Omit old preview vertices explicitly; the original samples remain intact.
        return listOf(result.first()) + result.takeLast(limit - 1).mapIndexed { index, point ->
            if (index == 0) point.copy(gapBefore = true) else point
        }
    }

    fun simplify(points: List<TripPoint>, toleranceMeters: Double): List<TripPoint> {
        require(toleranceMeters >= 0 && toleranceMeters.isFinite())
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        var start = 0
        for (index in 1..points.size) {
            if (index == points.size || points[index].gapBefore) {
                simplifySegment(points, start, index - 1, toleranceMeters, keep)
                start = index
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    private fun simplifySegment(points: List<TripPoint>, first: Int, last: Int,
        tolerance: Double, keep: BooleanArray) {
        keep[first] = true; keep[last] = true
        val work = ArrayDeque<Pair<Int, Int>>()
        work.add(first to last)
        while (work.isNotEmpty()) {
            val (start, end) = work.removeLast()
            var farthest = -1
            var distance = tolerance
            for (index in start + 1 until end) {
                val error = distanceToSegment(points[index], points[start], points[end])
                if (error > distance) { distance = error; farthest = index }
            }
            if (farthest >= 0) {
                keep[farthest] = true
                work.add(start to farthest); work.add(farthest to end)
            }
        }
    }

    private fun distanceToSegment(point: TripPoint, start: TripPoint, end: TripPoint): Double {
        val scale = 6_371_000.0 * Math.PI / 180.0
        val latitudeScale = cos(Math.toRadians(start.latitude))
        fun longitudeDelta(longitude: Double): Double =
            ((longitude - start.longitude + 540.0) % 360.0 - 180.0) * scale * latitudeScale
        val px = longitudeDelta(point.longitude)
        val py = (point.latitude - start.latitude) * scale
        val dx = longitudeDelta(end.longitude)
        val dy = (end.latitude - start.latitude) * scale
        val length = dx * dx + dy * dy
        val fraction = if (length == 0.0) 0.0 else ((px * dx + py * dy) / length).coerceIn(0.0, 1.0)
        return hypot(px - fraction * dx, py - fraction * dy)
    }
}
