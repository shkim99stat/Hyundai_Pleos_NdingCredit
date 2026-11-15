package com.example.pleos_endingcredit

/** Uses the order of plotted stops, while the timeline can also show stops without coordinates. */
internal class RouteStopMarkers(stops: List<RecordedStop>) {
    private val first = stops.firstOrNull { it.point != null }?.id
    private val last = stops.lastOrNull { it.point != null }?.id

    fun isLast(stop: RecordedStop): Boolean = stop.id == last

    fun color(stop: RecordedStop): Int {
        val confirmed = stop.selectedPlaceId != null || stop.userLabel != null
        return if (stop.id == first && !isLast(stop) && confirmed) 0xFF0CC8B7.toInt()
        else 0xFF1074FA.toInt()
    }
}
