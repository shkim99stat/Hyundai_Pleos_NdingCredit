package com.example.pleos_endingcredit

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** One read of every supported individual seat. Unknown/partial data never becomes a count. */
internal class TripSeatOccupancy(context: Context, private val result: (Int?) -> Unit) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var client: Vehicle? = null
    private var active = false

    fun start() {
        if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            result(null); return
        }
        active = true
        handler.postDelayed({ finish(null) }, 4_000)
        try {
            val vehicle = Vehicle(context).also { client = it; it.initialize() }
            val seats = vehicle.getSeat()
            seats.checkSeatOccupancyCapabilities(
                onComplete = { config: CapabilityConfig, areas: List<SeatArea> -> handler.post {
                    if (!active) return@post
                    val individual = areas.filter { it.values.size == 1 }.distinctBy { it.values.single() }
                    if (config.accessType !in listOf(AccessType.READ, AccessType.READ_WRITE) || individual.isEmpty() ||
                        individual.size != areas.size) { finish(null); return@post }
                    val readings = mutableMapOf<SeatArea, Boolean>()
                    try { individual.forEach { area ->
                        seats.getCurrentSeatOccupancy(area,
                            onComplete = { occupied -> handler.post reading@{
                                if (active) {
                                    if (occupied == null) { finish(null); return@reading }
                                    readings[area] = occupied
                                    if (readings.size == individual.size) finish(readings.values.count { it }.takeIf { it > 0 })
                                }
                            } }, onFailed = { handler.post { finish(null) } })
                    } } catch (_: Exception) { finish(null) }
                } }, onFailed = { handler.post { finish(null) } })
        } catch (_: Exception) { finish(null) }
    }

    private fun finish(count: Int?) {
        if (!active) return
        close()
        result(count)
    }

    fun close() {
        active = false
        handler.removeCallbacksAndMessages(null)
        client?.let { runCatching { it.release() } }
        client = null
    }

    companion object { const val PERMISSION = "pleos.car.permission.CAR_SEATS" }
}
