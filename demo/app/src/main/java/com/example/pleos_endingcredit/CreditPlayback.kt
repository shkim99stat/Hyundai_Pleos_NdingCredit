package com.example.pleos_endingcredit

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Four visible chapters. Frames 3 and 4 from older saved trips open the film. */
internal object CreditPlayback {
    const val OPENING = 0
    const val ROUTE = 1
    const val SUMMARY = 2
    const val FILM = 3
    const val SCREENING_FRAME = 5
    const val ROUTE_DRAW_MILLIS = 4_000L
    const val TOTAL_MILLIS = 30_000L
    val durations = longArrayOf(3_000L, ROUTE_DRAW_MILLIS, 2_000L, 21_000L)
    val frames = intArrayOf(0, 1, 2, SCREENING_FRAME)
    val titles = listOf("오프닝", "다닌 길", "주행거리", "우리의 장면")

    fun index(frame: Int) = frame.coerceIn(OPENING, FILM)
    fun screenId(frame: Int) = when (index(frame)) {
        OPENING -> "B3-1"
        ROUTE -> "B3-2"
        SUMMARY -> "B3-2-1"
        else -> "B3-3"
    }

    fun duration(frame: Int) = durations[index(frame)]
    fun nextFrame(frame: Int): Int? = frames.getOrNull(index(frame) + 1)

    fun phase(elapsed: Long, start: Long, end: Long) =
        ((elapsed - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
}

internal data class CreditStop(val time: String, val name: String)
internal data class CreditTimelineDay(
    val number: Long, val date: String, val departure: String?, val departureLabel: String, val stops: List<CreditStop>,
)

/** Sample measurements are restricted to bundled demo trips. Missing vehicle data stays missing. */
internal class CreditContent(val trip: Trip, val recording: TripRecording?, tripNumber: Int? = null) {
    val demo = recording == null && DemoContent.coverIndex(trip) != null
    val distanceKm: Double? = recording?.distanceMeters?.takeIf { it.isFinite() && it >= 0 }?.div(1000)
        ?: if (demo) 312.0 else null
    val distanceText = distanceKm?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else String.format(Locale.KOREA, "%.1f", it)
    } ?: "—"
    val title = trip.name.let { if (it.startsWith("우리")) it else "우리의 $it" }
    val showcase = DemoScenario.isDemo(trip.id)
    private val zone = if (showcase) DemoScenario.zone else ZoneId.systemDefault()
    private val start = recording?.startedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: runCatching { LocalDate.parse(trip.createdOn) }.getOrNull()
    private val end = recording?.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: if (demo) start?.plusDays(1) else start
    val dateText: String = start?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))?.let { first ->
        if (end != null && end != start) {
            val format = if (end.year == start.year) "MM.dd" else "yyyy.MM.dd"
            "$first – ${end.format(DateTimeFormatter.ofPattern(format))}"
        } else first
    } ?: trip.createdOn
    val stops: List<CreditStop> = if (demo) listOf(
        CreditStop("10:20", "횡성휴게소"), CreditStop("12:40", "강릉 중앙시장"),
        CreditStop("18:05", "강릉커피거리"), CreditStop("다음날 09:30", "초당순두부마을"),
        CreditStop("다음날 11:10", "경포해변"),
    ) else recording?.visibleStops.orEmpty().sortedBy { it.at }.map { stop ->
        val at = Instant.ofEpochMilli(stop.at).atZone(zone)
        val day = if (start == at.toLocalDate()) "" else "${at.monthValue}.${at.dayOfMonth} "
        CreditStop(day + at.format(DateTimeFormatter.ofPattern("HH:mm")),
            PlaceDisplayNames.display(stop))
    }
    val runtimeMinutes: Long? = recording?.movingMillis?.takeIf { it >= 0 }?.div(60_000)
        ?: if (demo) 400L else null
    val runtimeText = runtimeMinutes?.let { minutes ->
        listOfNotNull((minutes / 60).takeIf { it > 0 }?.let { "${it}시간" },
            (minutes % 60).takeIf { it > 0 || minutes == 0L }?.let { "${it}분" }).joinToString(" ")
    }
    val companionsText = trip.crewName?.takeIf { it.isNotBlank() } ?: "${trip.people}명"
    val castNote = runtimeText?.let { "웃음소리로 가득했던 $it" } ?: "함께한 시간을 남겼어요"
    val ticketNumber = tripNumber?.takeIf { it > 0 }?.toString()?.padStart(3, '0') ?: "—"
    val ticketDate = start?.format(DateTimeFormatter.ofPattern("EEE MM.dd", Locale.ENGLISH))?.let { first ->
        if (end != null && end != start) "$first — ${end.format(DateTimeFormatter.ofPattern("EEE MM.dd", Locale.ENGLISH))}" else first
    }?.uppercase(Locale.ENGLISH) ?: "—"
    val ticketRuntime = runtimeMinutes?.let { "${it / 60}H ${it % 60}M" } ?: "—"
    val ticketEndDate = end?.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) ?: "—"
    val ticketTheater = if (demo && trip.id == "demo-gangneung") "강릉 · 주문진"
        else stops.filter { it.name != "확인 필요" }.take(2).joinToString(" · ") { it.name }.ifEmpty { "—" }
    val ticketSubtitle = if (demo && trip.id == "demo-gangneung") "A ROAD MOVIE  ·  GANGNEUNG" else "A ROAD MOVIE"
    val ticketSeats = if (trip.people == 1) "A1" else "A1 — A${trip.people}"
    val barcodeText = listOf(distanceKm?.roundToInt()?.coerceIn(0, 999999)?.toString()?.padStart(4, '0') ?: "----",
        start?.format(DateTimeFormatter.ofPattern("MMdd")) ?: "----", trip.people.toString().padStart(4, '0')).joinToString(" ")
    val moments = if (demo) listOf("빗길 23km", "충전 1번 · 40분", "밤길 58km") else listOfNotNull(
        runtimeText?.let { "주행 시간 $it" },
        recording?.nightDistanceMeters?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { "밤길 ${(it / 1000).roundToInt()}km" },
    )

    val timeline: List<CreditTimelineDay> by lazy {
        val record = recording ?: return@lazy emptyList()
        val first = start ?: return@lazy emptyList()
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val date = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREA)
        val arrivals = record.visibleStops.sortedBy { it.at }.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
        val departures = timelineDepartures(record)
            .map { Instant.ofEpochMilli(it).atZone(zone) }
            .sorted()
            .groupBy { it.toLocalDate() }
        (arrivals.keys + departures.keys + first).distinct().sorted().map { day ->
            val dayNumber = java.time.temporal.ChronoUnit.DAYS.between(first, day) + 1
            CreditTimelineDay(
                dayNumber, day.format(date),
                departures[day]?.firstOrNull()?.format(time),
                if (showcase) "출발" else "기록 시작",
                arrivals[day].orEmpty().map {
                    CreditStop(Instant.ofEpochMilli(it.at).atZone(zone).format(time), PlaceDisplayNames.display(it))
                },
            )
        }
    }

    private fun timelineDepartures(record: TripRecording): List<Long> {
        if (showcase) {
            val endAt = record.endedAt ?: record.latest?.at ?: record.startedAt
            return DemoScenario.legsFor(trip.id).map { it.departureAt }.filter { it <= endAt }
        }
        return listOf(record.startedAt)
    }

    fun odometer(frame: Int, elapsed: Long): String {
        val distance = distanceKm ?: return "------"
        val current = when (CreditPlayback.index(frame)) {
            CreditPlayback.OPENING -> 0.0
            CreditPlayback.ROUTE -> distance * (elapsed.toDouble() / CreditPlayback.ROUTE_DRAW_MILLIS).coerceIn(0.0, 1.0)
            CreditPlayback.FILM -> if (trip.creditVideo == CreditVideoChoice.NONE || trip.creditVideo == CreditVideoChoice.UPLOADED) {
                distance * CreditPlayback.phase(elapsed, 0L, CreditPlayback.duration(frame))
            } else distance
            else -> distance
        }
        return current.roundToInt().coerceIn(0, 999999).toString().padStart(6, '0')
    }
}
