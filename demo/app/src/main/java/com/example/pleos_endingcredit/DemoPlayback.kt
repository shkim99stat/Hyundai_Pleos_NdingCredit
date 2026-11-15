package com.example.pleos_endingcredit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

internal data class DemoMapMotion(
    val scene: Int,
    val elapsed: Long,
    val jump: Int,
    val playing: Boolean,
    val arrived: Boolean,
) {
    fun durationSince(previous: DemoMapMotion?): Long {
        if (previous == null || previous.scene != scene || previous.jump != jump || !playing) return 0
        return (elapsed - previous.elapsed).coerceIn(0, 550)
    }
}

/** A foreground-only virtual clock. Saving a fixture cannot read or change the vehicle. */
internal class DemoPlayback(context: Context) {
    private val prefs = context.getSharedPreferences("showcase_playback", Context.MODE_PRIVATE)
    private val repository = TripRecordingRepository.get(context)
    private val handler = Handler(Looper.getMainLooper())
    var tripId: String? = prefs.getString("trip", null); private set
    val legs get() = DemoScenario.legsFor(tripId)
    var scene = prefs.getInt("scene", 0).coerceIn(legs.indices); private set
    var elapsed = prefs.getLong("elapsed", 0).coerceIn(0, legs[scene].playbackMillis); private set
    var paused = prefs.getBoolean("paused", false); private set
    var listener: (() -> Unit)? = null
    private var visible = false
    private var permitted = false
    private var lastTick = 0L
    private var mapJump = 0
    val leg get() = legs[scene]
    val progress get() = elapsed.toDouble() / leg.playbackMillis
    val arrived get() = elapsed >= leg.playbackMillis
    val mapMotion get() = DemoMapMotion(scene, elapsed, mapJump, visible && permitted && !paused, arrived)
    val finalArrival get() = arrived && scene == legs.lastIndex
    val currentStop get() = tripId?.let { id -> repository.load(id)?.stops?.find { it.id == DemoScenario.stopId(id, scene) } }
    val confirmed get() = currentStop?.let { it.excluded || it.userLabel != null || it.selectedPlaceId != null } == true
    val virtualAt get() = leg.departureAt + ((leg.arrivalAt - leg.departureAt) * progress).toLong()
    val speedKmh get() = if (arrived) 0 else (68 + 14 * kotlin.math.sin(progress * Math.PI)).toInt()

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (visible && permitted && !paused && !arrived && tripId != null) {
                elapsed = (elapsed + (now - lastTick).coerceIn(0, 1000)).coerceAtMost(leg.playbackMillis)
                publish()
            } else listener?.invoke()
            lastTick = now
            if (visible) handler.postDelayed(this, 250)
        }
    }
    fun begin(id: String) { tripId = id; scene = 0; elapsed = 0; paused = false; mapJump++; publish() }
    fun setHost(active: Boolean, allowed: Boolean) {
        visible = active; permitted = allowed; lastTick = SystemClock.uptimeMillis()
        handler.removeCallbacks(tick)
        if (active) handler.post(tick) else save()
    }
    fun togglePause() { paused = !paused; lastTick = SystemClock.uptimeMillis(); save(); listener?.invoke() }
    fun advance() {
        if (!arrived) { elapsed = leg.playbackMillis; mapJump++; publish(); return }
        if (!confirmed && !finalArrival) return
        if (scene < legs.lastIndex) { scene++; elapsed = 0; paused = false; publish() }
    }
    fun seek(index: Int) {
        scene = index.coerceIn(legs.indices)
        elapsed = leg.playbackMillis
        paused = false
        mapJump++
        publish()
    }
    fun finish(): Long { setHost(false, false); return virtualAt }

    fun finishImmediately(): TripRecording? {
        val id = tripId ?: return null
        val recording = repository.finishDemoScenario(id) ?: return null
        scene = legs.lastIndex
        elapsed = leg.playbackMillis
        paused = false
        setHost(false, false)
        return recording
    }

    fun release() { handler.removeCallbacks(tick); listener = null; save() }
    private fun publish() {
        tripId?.let { repository.sensor(DemoScenario.snapshot(it, scene, progress), arrived) }
        save(); listener?.invoke()
    }
    private fun save() { prefs.edit().putString("trip", tripId).putInt("scene", scene)
        .putLong("elapsed", elapsed).putBoolean("paused", paused).apply() }
}
