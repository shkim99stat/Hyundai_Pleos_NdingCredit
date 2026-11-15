package com.example.pleos_endingcredit

import kotlin.math.*

internal data class TripPoint(val latitude: Double, val longitude: Double, val at: Long,
    val gapBefore: Boolean = false, val confidence: Float? = null) {
    fun valid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 && at > 0
}

internal data class RecordedStop(
    val id: String, val at: Long, val point: TripPoint?,
    val selectedPlaceId: String? = null, val userLabel: String? = null,
    val excluded: Boolean = false, val resolutionAttempted: Boolean = false,
    val departedAt: Long? = null,
)

internal data class TripRecording(
    val tripId: String, val startedAt: Long, val endedAt: Long? = null,
    // Bounded map preview only. Original fixes and speed events live in TripSampleStore.
    val points: List<TripPoint> = emptyList(), val latest: TripPoint? = null,
    val stops: List<RecordedStop> = emptyList(), val pending: List<RecordedStop> = emptyList(),
    val departed: Boolean = false, val distanceMeters: Double = 0.0,
    val movingMillis: Long = 0, val interrupted: Boolean = false,
    val usedEnergyKwh: Double? = null, val energyDistanceMeters: Double = 0.0,
) {
    val visibleStops get() = stops.filterNot { it.excluded }
    val active get() = endedAt == null
}

/** The stop rule has no dwell-time threshold. Everything before the first
 * actual departure and the final uncommitted parking episode is excluded.
 * Sensor timestamps, missing values and process interruptions stay explicit.
 */
internal class TripRecordingEngine(initial: TripRecording) {
    var state = initial
        private set
    private var parked: Boolean? = null
    private var doors: Boolean? = null
    private var moving = false
    private var qualified = false
    private var lastTick: Long? = null
    private var gapNext = initial.interrupted
    private var serial = (initial.stops + initial.pending).size
    private var leftParkAt = Long.MAX_VALUE
    private var speedAt = 0L
    private var consumedOpeningAt = 0L
    private data class BatterySample(val timestampNs: Long, val dischargeMilliwatts: Double, val distanceMeters: Double)
    private var previousBattery: BatterySample? = null

    fun batteryRate(chargeRateMilliwatts: Double?, timestampNs: Long) {
        if (!state.active) return
        if (chargeRateMilliwatts == null || !chargeRateMilliwatts.isFinite() || timestampNs <= 0) {
            previousBattery = null
            return
        }
        val previous = previousBattery
        if (previous != null && timestampNs <= previous.timestampNs) return
        val sample = BatterySample(timestampNs, (-chargeRateMilliwatts).coerceAtLeast(0.0), state.distanceMeters)
        previousBattery = sample
        if (previous == null) return
        val elapsedNs = timestampNs - previous.timestampNs
        if (elapsedNs > 5_000_000_000L) return
        val meanDischargeMilliwatts = (sample.dischargeMilliwatts + previous.dischargeMilliwatts) / 2
        val usedKwh = meanDischargeMilliwatts * (elapsedNs / MILLIWATT_NANOSECONDS_PER_KWH)
        state = state.copy(
            usedEnergyKwh = (state.usedEnergyKwh ?: 0.0) + usedKwh,
            energyDistanceMeters = state.energyDistanceMeters + (sample.distanceMeters - previous.distanceMeters),
        )
    }

    fun vehicle(parked: Boolean?, speedMps: Double?, doorsOpen: Boolean?, now: Long, speedObservedAt: Long = now,
        doorOpenedAt: Long? = null) {
        if (!state.active) return
        tick(now)
        if (parked == false && this.parked != false) leftParkAt = now
        this.parked = parked
        this.doors = doorsOpen
        speedAt = speedObservedAt
        moving = parked == false && speedObservedAt >= leftParkAt && now - speedObservedAt in 0..5000 &&
            speedMps != null && speedMps.isFinite() && abs(speedMps) >= DEPARTURE_MPS
        if (moving) {
            state = state.copy(departed = true, stops = state.stops + state.pending.map { it.copy(departedAt = now) }, pending = emptyList())
        }
        val next = parked == true && doorsOpen == true
        val opening = doorOpenedAt ?: if (!qualified) now else 0L
        if (next && opening > consumedOpeningAt) {
            if (state.departed) recordDoorOpening(now)
            consumedOpeningAt = opening
        }
        qualified = next
    }

    /** True means this fresh fix must be persisted, even if it adds no map vertex. */
    fun point(point: TripPoint, receivedAt: Long): Boolean {
        if (!state.active || !point.valid() || point.at > receivedAt + 5000 ||
            receivedAt - point.at > FRESH_MS || point.at < state.startedAt) return false
        val previous = state.latest
        if (previous != null && point.at <= previous.at) return false
        val fix = point.copy(gapBefore = point.gapBefore || (previous != null &&
            (gapNext || point.at - previous.at > FRESH_MS)))
        state = state.copy(latest = fix)
        // A door event without a current fix waits for a fix in the same parked
        // episode; it must never inherit a later departure's coordinates.
        if (parked == true && doors == true && state.pending.isNotEmpty() && state.pending.last().point == null) {
            state = state.copy(pending = state.pending.dropLast(1) + state.pending.last().copy(point = fix))
        }
        // While still in P before the first departure, the origin is
        // provisional. Keep the newest real fix rather than an initial
        // emulator/default fix. This never adds a travelled segment.
        if (!state.departed && parked == true && state.points.size <= 1) {
            state = state.copy(points = listOf(point.copy(gapBefore = false)))
            gapNext = false
            return true
        }
        // Known parking suppresses GPS drift in the preview, never in the raw log.
        // Unknown/stale speed or gear must not stop collecting the actual route.
        if (state.points.isNotEmpty() && parked == true) return true
        val last = state.points.lastOrNull()
        if (last != null && point.at - last.at < 1000) return true
        val seconds = last?.let { (point.at - it.at) / 1000.0 }
        val meters = last?.let { metersBetween(it, point) } ?: 0.0
        val continuous = last != null && !gapNext && !fix.gapBefore && seconds != null && seconds in 0.001..30.0 &&
            meters / seconds <= MAX_PLAUSIBLE_MPS
        if (continuous && !moving && meters < MIN_PREVIEW_MOVEMENT_METERS) return true
        val next = point.copy(gapBefore = last != null && !continuous)
        state = state.copy(points = TripRoutePreview.compact(state.points + next),
            distanceMeters = state.distanceMeters + if (continuous) meters else 0.0)
        gapNext = false
        return true
    }

    fun tick(now: Long) {
        if (now - speedAt > 5000) moving = false
        val elapsed = lastTick?.let { now - it }
        if (state.active && moving && elapsed != null && elapsed in 0..5000) {
            state = state.copy(movingMillis = state.movingMillis + elapsed)
        }
        lastTick = now
    }

    fun unavailable() {
        previousBattery = null
        parked = null; doors = null; moving = false; qualified = false
        lastTick = null; gapNext = true
        state = state.copy(interrupted = true)
    }

    fun finish(now: Long) {
        tick(now)
        state = state.copy(endedAt = now, pending = emptyList())
        moving = false
    }

    private fun recordDoorOpening(now: Long) {
        val point = state.latest?.takeIf { now - it.at in 0..FRESH_MS }
        val merged = state.pending.any { old ->
            (old.point == null && point == null) || (old.point != null && point != null && metersBetween(old.point, point) <= MERGE_METERS)
        }
        if (!merged) state = state.copy(pending = state.pending + RecordedStop(
            id = "${state.tripId}-${now}-${serial++}", at = now, point = point))
    }

    companion object {
        const val MERGE_METERS = 200.0
        const val DEPARTURE_MPS = .5
        const val FRESH_MS = 30_000L
        private const val MAX_PLAUSIBLE_MPS = 70.0
        private const val MIN_PREVIEW_MOVEMENT_METERS = 3.0
        private const val MILLIWATT_NANOSECONDS_PER_KWH = 3.6e18
    }
}

internal data class PlaceCandidate(val id: String, val name: String, val category: String,
    val distanceMeters: Double, val restArea: Boolean = false, val address: String = "", val roadAddress: String = "")

internal data class PlaceDecision(val candidates: List<PlaceCandidate>, val automaticId: String?)

/** A rest area must be the venue itself, not a cafe/shop whose category path
 * merely mentions a rest area. Incomplete or failed searches cannot prove a
 * unique venue. At most three real candidates are offered; none are invented.
 */
internal fun decidePlaces(input: List<PlaceCandidate>, complete: Boolean): PlaceDecision {
    val valid = input.filter { it.id.isNotBlank() && it.name.isNotBlank() &&
        it.distanceMeters.isFinite() && it.distanceMeters in 0.0..200.0 }
        .distinctBy { it.id }.sortedWith(compareBy<PlaceCandidate> { !it.restArea }.thenBy { it.distanceMeters })
    val restAreas = valid.filter { it.restArea }
    val automatic = when {
        !complete -> null
        restAreas.size == 1 -> restAreas.single().id
        valid.size == 1 -> valid.single().id
        else -> null
    }
    return PlaceDecision(valid.take(3), automatic)
}

internal fun metersBetween(a: TripPoint, b: TripPoint): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val term = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 6_371_000.0 * 2 * atan2(sqrt(term.coerceIn(0.0, 1.0)), sqrt((1 - term).coerceIn(0.0, 1.0)))
}
