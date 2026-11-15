package com.example.pleos_endingcredit

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

internal data class ResultPlace(
    val id: String, val name: String, val time: String,
    val category: String = "unknown", val needsReview: Boolean = false,
) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("time", time)
        .put("category", category).put("needsReview", needsReview)
    companion object {
        fun fromJson(value: JSONObject) = ResultPlace(value.getString("id"), value.getString("name"),
            value.optString("time"), value.optString("category", "unknown"), value.optBoolean("needsReview"))
    }
}

/** Figma examples belong only to bundled demo history. Vehicle records never inherit them. */
internal class TripResultContent(val trip: Trip, val record: TripRecording?) {
    val demo = record == null && DemoContent.coverIndex(trip) != null
    private val credit = CreditContent(trip, record)
    val title = "${credit.title}을 기록했어요."
    val distance = if (demo) "486" else credit.distanceText
    val duration = if (demo) "9시간 50분" else record?.let {
        val minutes = it.movingMillis / 60_000
        if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
    } ?: "기록 없음"
    val rain = if (demo) "23km" else "기록 없음"
    val night = record?.nightDistanceMeters?.let { kotlin.math.round(it / 1000).toLong().toString() + "km" }
        ?: if (demo) "58km" else "기록 없음"
    val dates = if (demo) "2026.11.14(토) - 2026.11.15(일)" else credit.dateText
    val energy: Int? = if (demo) 29_160 else null
    val toll: Int? = if (demo) 12_400 else null
    val parking: Int? = if (demo) 0 else null
    val total: Int? = if (energy != null && toll != null && parking != null) energy + toll + parking else null
    val maxPeople = trip.people.coerceAtLeast(1)
    val minPeople = minOf(2, maxPeople)
    val splitPeople = (trip.splitPeople ?: maxPeople).coerceIn(minPeople, maxPeople)
    fun perPerson(people: Int): Int? = total?.let { ceil(it.toDouble() / people.coerceAtLeast(1)).toInt() }
    val places: List<ResultPlace> = trip.resultPlaces ?: if (demo) listOf(
        ResultPlace("demo-rest", "○○휴게소", "10:20", "other"),
        ResultPlace("demo-unknown", "주문진 근처", "12:40", needsReview = true),
        ResultPlace("demo-stay", "□□펜션", "18:05", "other"),
        ResultPlace("demo-beach", "◇◇해변", "다음날 09:30", "attraction"),
        ResultPlace("demo-view", "○○전망대", "다음날 11:10", "attraction"),
        ResultPlace("demo-restaurant", "□□식당", "12:10", "restaurant"),
        ResultPlace("demo-noodles", "☆☆막국수", "다음날 12:30", "restaurant"),
        ResultPlace("demo-cafe", "○○카페", "10:00", "cafe"),
        ResultPlace("demo-market", "△△시장", "15:30", "market"),
    ) else record?.visibleStops.orEmpty().sortedBy { it.at }.map { stop ->
        ResultPlace(stop.id, PlaceDisplayNames.display(stop), Instant.ofEpochMilli(stop.at)
            .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM.dd HH:mm", Locale.KOREA)),
            if (stop.userLabel != null || stop.selectedPlaceId != null) "other" else "unknown", stop.userLabel == null && stop.selectedPlaceId == null)
    }
    val representativePlaces: List<ResultPlace> = places.sortedBy {
        listOf("restaurant", "cafe", "attraction", "market", "unknown", "other").indexOf(it.category)
    }.groupBy { if (it.category == "other" || it.category == "unknown") it.id else it.category }
        .values.map { group -> group.first().let { first ->
            if (first.category == "restaurant") first.copy(name = group.joinToString("  ·  ") { it.name }) else first
        } }.take(5)
}
