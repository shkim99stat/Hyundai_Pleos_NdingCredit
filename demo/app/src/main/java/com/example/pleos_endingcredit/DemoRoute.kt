package com.example.pleos_endingcredit

/** Road geometry with virtual timestamps derived from distance along each leg. */
internal class DemoRoute(points: List<TripPoint>, private val departureAt: Long, private val arrivalAt: Long) {
    private val distances = points.zipWithNext(::metersBetween).runningFold(0.0, Double::plus).toDoubleArray()
    private val total = distances.last()
    private val duration = arrivalAt - departureAt
    private val path = points.mapIndexed { index, point ->
        point.copy(at = departureAt + (duration * (distances[index] / total)).toLong())
    }
    // Keep the displayed line within one metre of the captured road geometry.
    val preview = TripRoutePreview.simplify(path, toleranceMeters = 1.0)
    val bounds = listOf(
        path.minOf { it.longitude }, path.minOf { it.latitude },
        path.maxOf { it.longitude }, path.maxOf { it.latitude },
    )

    fun appendUntil(output: MutableList<TripPoint>, until: Long) {
        val traveled = total * ((until - departureAt).toDouble() / duration).coerceIn(0.0, 1.0)
        val match = distances.binarySearch(traveled)
        val next = if (match >= 0) match else -match - 1
        output.addAll(path.subList(0, next))
        val point = if (match >= 0) path[match] else {
            val from = distances[next - 1]
            path[next - 1].toward(path[next], (traveled - from) / (distances[next] - from))
        }
        output.add(point.copy(at = until))
    }

    companion object {
        fun loadPaths(name: String): List<List<TripPoint>> {
            val resource = checkNotNull(DemoRoute::class.java.getResourceAsStream(name))
            return resource.bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith('#') }.map(::decodePolyline6).toList()
            }
        }

        private fun decodePolyline6(encoded: String): List<TripPoint> {
            var cursor = 0
            fun delta(): Int {
                var bits = 0
                var shift = 0
                var chunk: Int
                do {
                    chunk = encoded[cursor++].code - 63
                    bits = bits or ((chunk and 31) shl shift)
                    shift += 5
                } while (chunk >= 32)
                return (bits ushr 1) xor -(bits and 1)
            }
            var latitude = 0
            var longitude = 0
            return buildList {
                while (cursor < encoded.length) {
                    latitude += delta()
                    longitude += delta()
                    add(TripPoint(latitude / 1_000_000.0, longitude / 1_000_000.0, at = 0))
                }
            }
        }
    }
}

internal fun TripPoint.toward(other: TripPoint, fraction: Double): TripPoint = copy(
    latitude = latitude + (other.latitude - latitude) * fraction,
    longitude = longitude + (other.longitude - longitude) * fraction,
)
