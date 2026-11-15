package com.example.pleos_endingcredit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

internal enum class ResultMode(val title: String, val description: String) {
    CREDIT("N딩 크레딧", "여행을 크레딧으로 보고 여행 카드를 남겨요."),
    SPLIT("N빵 정산", "이동비를 인원수대로 나눠요."),
    BOTH("둘 다", "N딩 크레딧과 이동비 정산을 함께 봐요.");

    val showsCredit: Boolean get() = this != SPLIT
    val showsCosts: Boolean get() = this != CREDIT
}

internal data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val people: Int,
    val plannedEnd: String = "",
    val createdOn: String = LocalDate.now().toString(),
    val mode: ResultMode = ResultMode.BOTH,
    val splitPeople: Int? = null,
    val resultPlaces: List<ResultPlace>? = null,
    val creditVideo: CreditVideoChoice? = null,
    val crewName: String? = null,
) {
    fun toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("people", people)
        .put("plannedEnd", plannedEnd)
        .put("createdOn", createdOn)
        .put("mode", mode.name)
        .put("creditVideo", creditVideo?.name)
        .put("crewName", crewName)
        .put("splitPeople", splitPeople)
        .put("resultPlaces", resultPlaces?.let { JSONArray(it.map(ResultPlace::toJson)) })

    companion object {
        fun fromJson(json: JSONObject) = Trip(
            id = json.getString("id"),
            name = json.getString("name"),
            people = json.optInt("people", 4).coerceIn(1, 99),
            plannedEnd = json.optString("plannedEnd"),
            createdOn = json.optString("createdOn"),
            mode = enumOrNull<ResultMode>(json.optString("mode")) ?: ResultMode.BOTH,
            splitPeople = json.optInt("splitPeople").takeIf { it > 0 },
            creditVideo = enumOrNull<CreditVideoChoice>(json.optString("creditVideo")),
            crewName = if (json.isNull("crewName")) null else json.optString("crewName").trim().takeIf { it.isNotEmpty() },
            resultPlaces = json.optJSONArray("resultPlaces")?.let { array ->
                (0 until array.length()).map { ResultPlace.fromJson(array.getJSONObject(it)) }
            },
        )
    }
}

internal data class CreditResume(val tripId: String, val frame: Int, val elapsedMillis: Long = 0)

internal class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("ending_credit_mock", Context.MODE_PRIVATE)

    init {
        if (prefs.getInt("credit_sequence_version", 0) < 1) {
            val old = prefs.getInt("resume_frame", 0)
            val mapped = when (old) { 2 -> 3; 3 -> 2; else -> old }
            prefs.edit().putInt("resume_frame", mapped).putLong("resume_elapsed", 0)
                .putInt("credit_sequence_version", 1).commit()
        }
        val demoVersion = prefs.getInt(DemoContent.VERSION_KEY, 0)
        if (demoVersion < DemoContent.VERSION) {
            val existing = trips
            val seeded = DemoContent.trips.filterNot { demo -> existing.any { it.id == demo.id } }
            val next = if (demoVersion == 0) seeded + existing else existing + seeded
            val checkpoint = resume?.takeIf { saved ->
                val isBundledDefault = demoVersion == 1 && saved == DemoContent.legacyResume &&
                    activeTrip == null && existing == DemoContent.trips.filter { DemoContent.homeIndex(it) == null }
                !isBundledDefault && next.any { it.id == saved.tripId && it.mode.showsCredit }
            } ?: DemoContent.resume
            prefs.edit().putString("trips", encode(next))
                .putString("resume_trip", checkpoint.tripId).putInt("resume_frame", checkpoint.frame)
                .putLong("resume_elapsed", checkpoint.elapsedMillis)
                .putInt(DemoContent.VERSION_KEY, DemoContent.VERSION).commit()
        }
    }

    var hasConsent: Boolean
        get() = prefs.getBoolean("consent", false)
        set(value) { prefs.edit().putBoolean("consent", value).apply() }

    var activeTrip: Trip?
        get() = prefs.getString("active_trip", null)?.let {
            runCatching { Trip.fromJson(JSONObject(it)) }.getOrNull()
        }
        set(value) { prefs.edit().putString("active_trip", value?.toJson()?.toString()).apply() }

    val trips: List<Trip>
        get() {
            val array = runCatching { JSONArray(prefs.getString("trips", "[]")) }
                .getOrElse { JSONArray() }
            return (0 until array.length()).mapNotNull { i ->
                runCatching { Trip.fromJson(array.getJSONObject(i)) }.getOrNull()
            }
        }

    var resume: CreditResume?
        get() = prefs.getString("resume_trip", null)?.let { id ->
            val frame = prefs.getInt("resume_frame", 0).coerceIn(0, CREDIT_FRAME_COUNT - 1)
            CreditResume(id, frame, prefs.getLong("resume_elapsed", 0).coerceIn(0, CreditPlayback.duration(frame)))
        }
        set(value) {
            prefs.edit().putString("resume_trip", value?.tripId)
                .putInt("resume_frame", value?.frame ?: 0)
                .putLong("resume_elapsed", value?.elapsedMillis ?: 0).apply()
        }

    var creditMuted: Boolean
        get() = prefs.getBoolean("credit_muted", false)
        set(value) { prefs.edit().putBoolean("credit_muted", value).apply() }

    fun complete(trip: Trip) {
        val next = listOf(trip) + trips.filterNot { it.id == trip.id }
        val checkpoint = if (trip.mode.showsCredit) CreditResume(trip.id, 0) else resume
        prefs.edit().putString("trips", encode(next)).remove("active_trip")
            .putString("resume_trip", checkpoint?.tripId)
            .putInt("resume_frame", checkpoint?.frame ?: 0)
            .putLong("resume_elapsed", checkpoint?.elapsedMillis ?: 0)
            .apply()
    }

    fun update(trip: Trip) {
        prefs.edit().putString("trips", encode(trips.map { if (it.id == trip.id) trip else it }))
            .apply()
    }

    fun deleteCompleted(id: String) {
        val editor = prefs.edit().putString("trips", encode(trips.filterNot { it.id == id }))
        if (resume?.tripId == id) editor.remove("resume_trip").remove("resume_frame").remove("resume_elapsed")
        editor.apply()
    }

    private fun encode(trips: List<Trip>): String =
        JSONArray().apply { trips.forEach { put(it.toJson()) } }.toString()
}

internal inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }

internal val CREDIT_FRAME_COUNT = CreditScene.entries.size
internal const val YEAR_FRAME_COUNT = 3

internal object TripSample {
    const val distanceKm = 312
    const val duration = "6시간 40분"
    const val fuel = 18_720
    const val toll = 8_600
    const val total = fuel + toll
    val stops = listOf("강릉휴게소", "안목해변", "경포호", "속초시장")

    fun perPerson(people: Int): Int = total / people.coerceAtLeast(1)

    fun won(amount: Int): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원"
}
