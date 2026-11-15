package com.example.pleos_endingcredit

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Speed observations use app receipt time; a missing value is never zero speed. */
internal data class TripSpeedSample(val observedAt: Long, val speedMps: Double?)
internal data class TripLocationSample(val point: TripPoint, val receivedAt: Long,
    val legacyPreview: Boolean = false)

/** Incremental on-device originals, independent of JSON/map preview thinning.
 * No Kakao response data is stored. Only the repository writer calls mutations.
 */
internal class TripSampleStore(context: Context, name: String = "trip-samples.db") :
    SQLiteOpenHelper(context, name, null, 1) {
    init { setWriteAheadLoggingEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE locations (
            trip_id TEXT NOT NULL, at INTEGER NOT NULL, latitude REAL NOT NULL,
            longitude REAL NOT NULL, received_at INTEGER NOT NULL, confidence REAL,
            gap_before INTEGER NOT NULL, legacy_preview INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(trip_id, at))""")
        db.execSQL("""CREATE TABLE speeds (
            sample_id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id TEXT NOT NULL,
            observed_at INTEGER NOT NULL, speed_mps REAL)""")
        db.execSQL("CREATE INDEX speeds_trip_time ON speeds(trip_id, observed_at, sample_id)")
        db.execSQL("CREATE TABLE imports (trip_id TEXT PRIMARY KEY NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported trip sample schema upgrade: $oldVersion -> $newVersion")
    }

    fun appendLocation(id: String, sample: TripLocationSample) {
        require(sample.point.valid() && sample.receivedAt > 0)
        val point = sample.point
        val values = ContentValues().apply {
            put("trip_id", id); put("at", point.at)
            put("latitude", point.latitude); put("longitude", point.longitude)
            put("received_at", sample.receivedAt)
            point.confidence?.takeIf { it.isFinite() }?.let { put("confidence", it) } ?: putNull("confidence")
            put("gap_before", if (point.gapBefore) 1 else 0)
            put("legacy_preview", if (sample.legacyPreview) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("locations", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun appendSpeed(id: String, sample: TripSpeedSample) {
        require(sample.observedAt > 0 && (sample.speedMps == null || sample.speedMps.isFinite()))
        val values = ContentValues().apply {
            put("trip_id", id); put("observed_at", sample.observedAt)
            sample.speedMps?.let { put("speed_mps", it) } ?: putNull("speed_mps")
        }
        writableDatabase.insertOrThrow("speeds", null, values)
    }

    fun importLegacy(id: String, points: List<TripPoint>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val inserted = db.insertWithOnConflict("imports", null,
                ContentValues().apply { put("trip_id", id) }, SQLiteDatabase.CONFLICT_IGNORE)
            if (inserted != -1L) points.filter { it.valid() }.forEach {
                appendLocation(id, TripLocationSample(it, it.at, legacyPreview = true))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun locations(id: String): List<TripLocationSample> = readableDatabase.query("locations",
        arrayOf("latitude", "longitude", "at", "gap_before", "confidence", "received_at", "legacy_preview"),
        "trip_id = ?", arrayOf(id), null, null, "at ASC").use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(TripLocationSample(TripPoint(cursor.getDouble(0), cursor.getDouble(1),
                cursor.getLong(2), cursor.getInt(3) != 0, if (cursor.isNull(4)) null else cursor.getFloat(4)),
                cursor.getLong(5), cursor.getInt(6) != 0))
        }
    }

    fun speeds(id: String): List<TripSpeedSample> = readableDatabase.query("speeds",
        arrayOf("observed_at", "speed_mps"), "trip_id = ?", arrayOf(id), null, null,
        "observed_at ASC, sample_id ASC").use { cursor ->
        buildList { while (cursor.moveToNext()) add(TripSpeedSample(cursor.getLong(0),
            if (cursor.isNull(1)) null else cursor.getDouble(1))) }
    }

    fun deleteTrip(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("locations", "trip_id = ?", arrayOf(id))
            db.delete("speeds", "trip_id = ?", arrayOf(id))
            db.delete("imports", "trip_id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
