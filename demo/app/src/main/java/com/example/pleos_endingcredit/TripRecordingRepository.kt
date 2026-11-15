package com.example.pleos_endingcredit

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Stores only vehicle-generated fixes, times, user-authored labels and place
 * IDs. Kakao response names, addresses, coordinates and candidate lists never
 * enter this file. The live search results belong to the visible map session.
 */
internal class TripRecordingRepository private constructor(context: Context) {
    private val folder = File(context.filesDir, "trip-records").apply { mkdirs() }
    private val cache = mutableMapOf<String, TripRecording>()
    private val io = Executors.newSingleThreadExecutor()
    private val samples = TripSampleStore(context.applicationContext)
    private val queued = mutableSetOf<String>()
    private val lastWrite = mutableMapOf<String, Long>()
    private val deleted = mutableSetOf<String>()
    @Volatile private var snapshotError: String? = null
    @Volatile private var sampleError: String? = null
    val storageError: String? get() = sampleError ?: snapshotError

    @Synchronized fun load(id: String): TripRecording? = cache[id] ?: runCatching {
        val file = file(id)
        if (!file.baseFile.exists()) null else {
            val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            DemoScenario.restoreRoadGeometry(decode(json)).also { record ->
                cache[id] = record
                // Older files contain only a sampled preview, not recovered originals.
                val legacy = if (json.optInt("version", 1) < 2) record.points else emptyList()
                writeSamples(id) { samples.importLegacy(id, legacy) }
            }
        }
    }.getOrElse { snapshotError = "여행 기록을 읽지 못했어요"; null }

    @Synchronized fun ensure(id: String, now: Long = System.currentTimeMillis()): TripRecording =
        load(id) ?: TripRecording(id, now).also { cache[id] = it; queue(id, true) }

    @Synchronized fun location(id: String, point: TripPoint, receivedAt: Long) {
        if (load(id)?.active != true) return
        writeSamples(id) { samples.appendLocation(id, TripLocationSample(point, receivedAt)) }
    }

    @Synchronized fun speed(id: String, sample: TripSpeedSample) {
        if (load(id)?.active != true) return
        writeSamples(id) { samples.appendSpeed(id, sample) }
    }

    private fun writeSamples(id: String, write: () -> Unit) {
        io.execute {
            if (synchronized(this) { id in deleted }) return@execute
            try { write(); sampleError = null }
            catch (_: Exception) { sampleError = "주행 원본 데이터를 저장하지 못했어요" }
        }
    }

    @Synchronized fun sensor(value: TripRecording, force: Boolean = false) {
        if (value.tripId in deleted) return
        val existing = load(value.tripId)
        if (existing?.endedAt != null && value.endedAt == null) return
        cache[value.tripId] = value.copy(stops = mergeStopSelections(value.stops, existing?.stops.orEmpty()))
        queue(value.tripId, force || value.endedAt != null)
    }

    private fun mergeStopSelections(stops: List<RecordedStop>, previous: List<RecordedStop>): List<RecordedStop> {
        val selections = previous.associateBy { it.id }
        return stops.map { stop ->
            selections[stop.id]?.let { saved -> stop.copy(selectedPlaceId = saved.selectedPlaceId,
                userLabel = saved.userLabel, excluded = saved.excluded,
                resolutionAttempted = saved.resolutionAttempted) } ?: stop
        }
    }

    @Synchronized fun editStop(tripId: String, stopId: String, edit: (RecordedStop) -> RecordedStop) {
        val record = load(tripId) ?: return
        cache[tripId] = record.copy(stops = record.stops.map { if (it.id == stopId) edit(it) else it })
        queue(tripId, true)
    }

    @Synchronized fun finish(id: String, now: Long = System.currentTimeMillis()) {
        val record = load(id) ?: return
        cache[id] = record.copy(endedAt = now, pending = emptyList())
        queue(id, true)
    }

    @Synchronized fun finishDemoScenario(id: String): TripRecording? {
        if (!DemoScenario.isDemo(id) || id in deleted) return null
        val previous = load(id)?.takeIf { it.active } ?: return null
        val legs = DemoScenario.legsFor(id)
        val last = legs.lastIndex
        val final = DemoScenario.snapshot(id, last, 1.0)
        val stops = mergeStopSelections(final.stops, previous.stops).map { stop ->
            DemoScenario.resolveSkippedStop(id, stop)
        }
        val completed = final.copy(endedAt = legs[last].arrivalAt, stops = stops)
        cache[id] = completed
        queue(id, true)
        return completed
    }

    @Synchronized fun delete(id: String) {
        deleted += id
        cache.remove(id)
        io.execute { synchronized(this) { if (!cache.containsKey(id)) {
            runCatching { samples.deleteTrip(id); file(id).delete() }
                .onFailure { sampleError = "여행 기록을 삭제하지 못했어요" }
        } } }
    }

    @Synchronized fun pruneRawPaths(now: Long = System.currentTimeMillis()) {
        folder.listFiles { file -> file.extension == "json" }?.forEach { file ->
            val id = file.nameWithoutExtension
            val record = load(id) ?: return@forEach
            if (record.endedAt?.let { now - it > 30L * 24 * 60 * 60 * 1000 } == true) {
                writeSamples(id) { samples.deleteTrip(id) }
                cache[id] = record.copy(points = emptyList(), latest = null,
                    stops = record.stops.map { it.copy(point = null) }, pending = emptyList())
                queue(id, true)
            }
        }
    }

    private fun file(id: String) = AtomicFile(File(folder, "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json"))

    private fun queue(id: String, force: Boolean) {
        val now = System.currentTimeMillis()
        if ((!force && now - (lastWrite[id] ?: 0) < 5000) || !queued.add(id)) return
        lastWrite[id] = now
        io.execute {
            synchronized(this) {
                queued.remove(id)
                val value = cache[id] ?: return@synchronized
                val target = file(id)
                var stream: java.io.FileOutputStream? = null
                try {
                    stream = target.startWrite()
                    stream.write(encode(value).toString().toByteArray(Charsets.UTF_8))
                    target.finishWrite(stream)
                    snapshotError = null
                } catch (_: Exception) {
                    stream?.let(target::failWrite)
                    snapshotError = "여행 기록을 저장하지 못했어요"
                }
            }
        }
    }

    companion object {
        @Volatile private var singleton: TripRecordingRepository? = null
        fun get(context: Context): TripRecordingRepository = singleton ?: synchronized(this) {
            singleton ?: TripRecordingRepository(context.applicationContext).also { singleton = it }
        }

        private fun point(value: TripPoint) = JSONObject().put("lat", value.latitude).put("lng", value.longitude)
            .put("at", value.at).put("gap", value.gapBefore)
            .put("confidence", value.confidence?.takeIf { it.isFinite() })
        private fun readPoint(value: JSONObject) = TripPoint(value.getDouble("lat"), value.getDouble("lng"),
            value.getLong("at"), value.optBoolean("gap"), value.optDouble("confidence", Double.NaN)
                .takeIf { it.isFinite() }?.toFloat()?.takeIf { it.isFinite() })
        private fun stop(value: RecordedStop) = JSONObject().put("id", value.id).put("at", value.at)
            .put("point", value.point?.let(::point)).put("placeId", value.selectedPlaceId)
            .put("userLabel", value.userLabel).put("excluded", value.excluded).put("attempted", value.resolutionAttempted).put("departedAt",value.departedAt)
        private fun readStop(value: JSONObject) = RecordedStop(value.getString("id"), value.getLong("at"),
            value.optJSONObject("point")?.let(::readPoint), value.optString("placeId").takeIf { it.isNotBlank() },
            value.optString("userLabel").takeIf { it.isNotBlank() }, value.optBoolean("excluded"), value.optBoolean("attempted"),
            value.optLong("departedAt").takeIf{it>0})
        private fun <T> list(array: JSONArray?, read: (JSONObject) -> T): List<T> =
            if (array == null) emptyList() else (0 until array.length()).map { read(array.getJSONObject(it)) }
        private fun encode(value: TripRecording) = JSONObject().put("version", 2).put("tripId", value.tripId)
            .put("startedAt", value.startedAt).put("endedAt", value.endedAt)
            .put("points", JSONArray(value.points.map(::point))).put("latest", value.latest?.let(::point))
            .put("stops", JSONArray(value.stops.map(::stop))).put("pending", JSONArray(value.pending.map(::stop)))
            .put("departed", value.departed).put("distance", value.distanceMeters)
            .put("movingMillis", value.movingMillis).put("interrupted", value.interrupted)
            .put("nightDistance", value.nightDistanceMeters).put("nightMillis", value.nightMovingMillis)
        private fun decode(value: JSONObject) = TripRecording(value.getString("tripId"), value.getLong("startedAt"),
            if (value.has("endedAt")) value.getLong("endedAt") else null,
            list(value.optJSONArray("points"), ::readPoint), value.optJSONObject("latest")?.let(::readPoint),
            list(value.optJSONArray("stops"), ::readStop), list(value.optJSONArray("pending"), ::readStop),
            value.optBoolean("departed"), value.optDouble("distance", 0.0), value.optLong("movingMillis"),
            value.optBoolean("interrupted"),
            nightDistanceMeters = if (value.has("nightDistance")) value.getDouble("nightDistance") else null,
            nightMovingMillis = if (value.has("nightMillis")) value.getLong("nightMillis") else null)
    }
}
