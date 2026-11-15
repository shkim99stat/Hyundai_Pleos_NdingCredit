package com.example.pleos_endingcredit

/** Estimate for the demo display based on the distance reached along each existing leg. */
internal fun demoEnergyKwh(record: TripRecording): Double {
    val at = record.latest?.at ?: record.startedAt
    return DemoScenario.legsFor(record.tripId).sumOf { leg ->
        val progress = ((at - leg.departureAt).toDouble() / (leg.arrivalAt - leg.departureAt))
            .coerceIn(0.0, 1.0)
        val startKwhPerKm = 0.17 + when {
            leg.drivingMinutes > 80 -> 0.04
            leg.drivingMinutes > 35 -> 0.02
            else -> 0.0
        }
        // Consumption increases through the leg so average efficiency changes as well.
        leg.kilometers * progress * (startKwhPerKm + 0.035 * progress)
    }
}
