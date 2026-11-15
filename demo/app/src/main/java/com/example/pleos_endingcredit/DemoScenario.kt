package com.example.pleos_endingcredit

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Authored presentation fixtures, never device measurements or Kakao POI responses. */
internal data class DemoVenue(val name: String, val category: String)
internal data class DemoLeg(
    val title: String, val departure: String, val arrival: String,
    val latitude: Double, val longitude: Double,
    val kilometers: Double, val drivingMinutes: Int, val playbackMillis: Long,
    val venues: List<DemoVenue>, val hint: String,
    val address: String = "",
) {
    val departureAt = DemoScenario.at(departure)
    val arrivalAt = DemoScenario.at(arrival)
}

internal object DemoScenario {
    const val TITLE = "동해안 1박 2일"
    const val DATE = "2026-11-14"
    const val CURRENT_PREFIX = "showcase-spread-"
    val zone: ZoneId = ZoneId.of("Asia/Seoul")
    val origin = 37.5665 to 126.9780
    fun at(value: String): Long = LocalDateTime.parse("2026-11-$value").atZone(zone).toInstant().toEpochMilli()
    val legs = listOf(
        DemoLeg("서울에서 가평으로", "14T14:10", "14T15:05", 37.701940, 127.546805, 60.4, 55, 8_000,
            listOf(DemoVenue("가평휴게소(춘천 방향)", "other")),
            "가평에서 잠깐 쉬어 가요. 휴게소를 방문 장소로 자동 기록했어요.",
            "경기도 가평군 설악면 미사리로540번길 51"),
        DemoLeg("홍천에서 간식", "14T15:30", "14T16:05", 37.757300, 128.004200, 43.2, 35, 6_500,
            listOf(DemoVenue("홍천휴게소(양양 방향)", "other"), DemoVenue("홍천휴게소 푸드코트", "restaurant"), DemoVenue("홍천휴게소 편의점", "market")),
            "홍천휴게소에서 방문한 장소를 직접 확인해 주세요.",
            "강원특별자치도 홍천군 화촌면 서울양양고속도로 84"),
        DemoLeg("인제 내린천을 지나", "14T16:35", "14T17:00", 37.909631, 128.281475, 33.0, 25, 5_500,
            listOf(DemoVenue("내린천휴게소", "other")),
            "내린천휴게소를 자동으로 기록했어요.",
            "강원특별자치도 인제군 상남면 서울양양고속도로 117"),
        DemoLeg("속초의 밤 · 시장으로", "14T18:20", "14T19:12", 38.204770, 128.589800, 57.2, 52, 8_000,
            listOf(DemoVenue("속초관광수산시장", "market")),
            "시장에서 첫날 일정을 마쳐요. ‘2일차 시작’을 눌러 주세요.",
            "강원특별자치도 속초시 중앙로147번길 12"),
        DemoLeg("2일차 · 양양 해안으로", "15T09:00", "15T09:34", 38.023251, 128.723325, 28.3, 34, 6_500,
            listOf(DemoVenue("하조대해수욕장", "attraction")),
            "양양에서 바다를 보고 가요. 하조대해수욕장을 자동으로 기록했어요.",
            "강원특별자치도 양양군 현북면 하조대해안길 35"),
        DemoLeg("강릉 경포해변으로", "15T10:45", "15T11:19", 37.805435, 128.906919, 34.7, 34, 6_500,
            listOf(DemoVenue("경포해변", "attraction")),
            "경포해변을 자동으로 기록했어요. 다음 출발부터 귀경길이에요.",
            "강원특별자치도 강릉시 창해로 514"),
        DemoLeg("횡성에서 잠깐 쉬어 가기", "15T14:00", "15T15:23", 37.465273, 128.135381, 101.2, 83, 13_000,
            listOf(DemoVenue("횡성휴게소(인천 방향)", "other"), DemoVenue("횡성휴게소 푸드코트", "restaurant"), DemoVenue("횡성휴게소 카페", "cafe")),
            "횡성휴게소에서 방문한 장소를 직접 확인해 주세요.",
            "강원특별자치도 횡성군 안흥면 영동고속도로 153"),
        DemoLeg("이천 덕평에서 마지막 휴식", "15T16:20", "15T17:26", 37.241506, 127.389470, 83.0, 66, 11_000,
            listOf(DemoVenue("덕평자연휴게소", "other")),
            "덕평자연휴게소를 자동으로 기록했어요. 이제 밤길로 서울에 돌아가요.",
            "경기도 이천시 마장면 덕이로154번길 287-76"),
        DemoLeg("밤길을 달려 서울로", "15T18:15", "15T19:07", origin.first, origin.second, 63.4, 52, 11_000,
            emptyList(), "서울에 도착했어요. 여행 끝내기를 눌러 크레딧을 재생해 보세요."),
    )

    // Trips started before the stops were redistributed keep their original stops and playback.
    private val legacyLegs = listOf(
        DemoLeg("서울 → 속초", "14T08:00", "14T12:00", 38.2045, 128.5905, 218.0, 180, 18_000,
            listOf(DemoVenue("속초관광수산시장", "market")),
            "주변 후보가 시장 한 곳뿐이라 자동으로 방문 장소를 선택했어요."),
        DemoLeg("속초에서 점심", "14T13:00", "14T13:15", 38.1900, 128.5915, 4.0, 15, 4_000,
            listOf(DemoVenue("청초호 물회 식당 [가상]", "restaurant"), DemoVenue("호수 앞 카페 [가상]", "cafe"), DemoVenue("청초호 산책 지점", "attraction")),
            "1일차에 직접 확인할 곳이에요. 주변 후보 중 다녀온 식당을 골라 주세요."),
        DemoLeg("숙소에 가기 전 장보기", "14T16:45", "14T17:10", 38.1882, 128.5985, 4.0, 25, 4_000,
            listOf(DemoVenue("해변 앞 마트 [가상]", "market")),
            "주변 후보가 마트 한 곳뿐이라 자동으로 선택했어요."),
        DemoLeg("속초의 밤 · 숙소로", "14T19:10", "14T19:20", 38.1848, 128.6000, 1.0, 10, 4_000,
            listOf(DemoVenue("속초 바다 숙소 [가상]", "other")),
            "숙소를 자동으로 선택했어요. 1일차 4곳을 기록했으니 ‘2일차 시작’을 눌러 주세요."),
        DemoLeg("2일차 · 속초 → 강릉", "15T09:00", "15T10:30", 37.8055, 128.9070, 65.0, 75, 12_000,
            listOf(DemoVenue("경포해변", "attraction")),
            "2일차예요. 주변 후보가 경포해변 한 곳뿐이라 자동으로 선택했어요."),
        DemoLeg("초당에서 점심", "15T11:40", "15T12:00", 37.7900, 128.9140, 4.0, 20, 4_000,
            listOf(DemoVenue("초당 순두부 식당 [가상]", "restaurant")),
            "주변 후보가 식당 한 곳뿐이라 자동으로 선택했어요."),
        DemoLeg("안목해변에서 커피", "15T13:20", "15T13:40", 37.7730, 128.9480, 7.0, 20, 4_000,
            listOf(DemoVenue("안목 바다 카페 [가상]", "cafe"), DemoVenue("안목해변", "attraction"), DemoVenue("안목 마트 [가상]", "market")),
            "2일차에 직접 확인할 곳이에요. 다녀온 카페를 선택한 뒤 귀경길에 올라요."),
        DemoLeg("귀경길 · 잠깐 쉬어 가기", "15T16:10", "15T17:40", 37.4700, 128.2800, 105.0, 85, 10_000,
            listOf(DemoVenue("귀경길 휴게소 [가상]", "other")),
            "휴게소를 자동으로 선택했어요. 2일차 4곳을 마치고 밤길을 달려 서울로 돌아가요."),
        DemoLeg("밤길을 달려 서울로", "15T18:00", "15T19:50", origin.first, origin.second, 130.0, 100, 12_000,
            emptyList(), "서울에 도착했어요. 여행 끝내기를 누르면 밤길과 방문 장소를 크레딧으로 돌아봐요."),
    )
    private fun isLegacy(id: String?) = isDemo(id) && id?.startsWith(CURRENT_PREFIX) != true
    fun legsFor(id: String?): List<DemoLeg> = if (isLegacy(id)) legacyLegs else legs

    private val currentRoutes by lazy { loadRoutes(legs, "/demo-road-routes.polyline6") }
    private val legacyRoutes by lazy { loadRoutes(legacyLegs, "/demo-legacy-road-routes.polyline6") }
    private fun routesFor(id: String) = if (isLegacy(id)) legacyRoutes else currentRoutes
    private fun loadRoutes(legs: List<DemoLeg>, resource: String): List<DemoRoute> {
        val paths = DemoRoute.loadPaths(resource)
        check(paths.size == legs.size)
        return legs.zip(paths) { leg, path ->
            DemoRoute(path, leg.departureAt, leg.arrivalAt)
        }
    }
    fun routeBounds(id: String, scene: Int): List<Double> = routesFor(id)[scene].bounds

    private fun sceneAt(id: String, at: Long) = legsFor(id).indexOfFirst { at in it.departureAt..it.arrivalAt }

    fun mapPoints(record: TripRecording): List<TripPoint> {
        val latest = record.latest
        val scene = if (latest != null && isDemo(record.tripId) && record.points.isNotEmpty()) {
            sceneAt(record.tripId, latest.at)
        } else -1
        if (scene < 0 || latest == null) return TripRoutePreview.compact(record.points)
        val routes = routesFor(record.tripId)
        return buildList {
            for (i in 0 until scene) addAll(routes[i].preview)
            addAll(routes[scene].preview.takeWhile { it.at < latest.at })
            add(latest)
        }
    }

    /** Refresh older demo records while keeping the saved time, statistics and selected places. */
    fun restoreRoadGeometry(record: TripRecording): TripRecording {
        if (!isDemo(record.tripId)) return record
        val first = record.points.firstOrNull() ?: return record
        if (first.latitude != origin.first || first.longitude != origin.second) return record
        val latest = record.latest ?: return record
        val scene = sceneAt(record.tripId, latest.at)
        if (scene < 0) return record
        val legs = legsFor(record.tripId)
        val routes = routesFor(record.tripId)
        val points = buildList {
            for (i in 0 until scene) routes[i].appendUntil(this, legs[i].arrivalAt)
            routes[scene].appendUntil(this, latest.at)
        }
        return record.copy(points = points, latest = points.last())
    }

    fun animationTrack(record: TripRecording, motion: DemoMapMotion): List<TripPoint> {
        val leg = legsFor(record.tripId)[motion.scene]
        // Keep a few render intervals of exact vertices so a delayed frame can still follow the road.
        val since = (motion.elapsed - 2_000L).coerceAtLeast(0)
        val at = leg.departureAt + (leg.arrivalAt - leg.departureAt) * since / leg.playbackMillis
        return record.points.asReversed().takeWhile { it.at >= at }.asReversed()
    }
    // An authored lighting schedule for the demo, not an astronomical sunset calculation.
    fun isNight(at: Long): Boolean = Instant.ofEpochMilli(at).atZone(zone).hour.let { it < 6 || it >= 18 }
    fun nightFraction(from: Long, until: Long): Double {
        if (until <= from) return 0.0
        var cursor = from
        var night = 0L
        while (cursor < until) {
            val local = Instant.ofEpochMilli(cursor).atZone(zone)
            val day = local.toLocalDate()
            val boundary = when {
                local.hour < 6 -> day.atTime(6, 0)
                local.hour < 18 -> day.atTime(18, 0)
                else -> day.plusDays(1).atTime(6, 0)
            }.atZone(zone).toInstant().toEpochMilli().coerceAtMost(until)
            if (isNight(cursor)) night += boundary - cursor
            cursor = boundary
        }
        return night.toDouble() / (until - from)
    }
    fun automaticVenue(tripId: String, stopId: String): PlaceCandidate? {
        val choices = candidates(tripId, stopId)
        val chosenId = decidePlaces(choices, complete = true).automaticId
        return choices.firstOrNull { it.id == chosenId }
    }
    fun automaticallyResolved(tripId: String, stop: RecordedStop): Boolean =
        stop.userLabel != null && stop.userLabel == automaticVenue(tripId, stop.id)?.name
    fun isDemo(id: String?) = id?.startsWith("showcase-") == true
    fun stopId(tripId: String, index: Int) = "$tripId-stop-$index"
    fun indexOfStop(tripId: String, id: String) = legsFor(tripId).indices.firstOrNull { stopId(tripId, it) == id }
    fun candidates(tripId: String, stopId: String): List<PlaceCandidate> =
        indexOfStop(tripId, stopId)?.let { index ->
            val leg = legsFor(tripId)[index]
            leg.venues.mapIndexed { i, venue ->
            PlaceCandidate("showcase-venue-$index-$i", venue.name, venue.category, 40.0 + i * 45,
                address = leg.address.ifBlank { "시연용 주변 후보 · 실제 검색 결과 아님" })
        } }.orEmpty()
    fun category(name: String) = (legs + legacyLegs).asSequence().flatMap { it.venues.asSequence() }
        .firstOrNull { it.name == name }?.category ?: "other"

    fun resolveSkippedStop(tripId: String, stop: RecordedStop): RecordedStop {
        if (stop.excluded || stop.userLabel != null || stop.selectedPlaceId != null) return stop
        val candidate = candidates(tripId, stop.id).firstOrNull() ?: return stop
        return stop.copy(userLabel = candidate.name, resolutionAttempted = true)
    }

    fun snapshot(tripId: String, index: Int, fraction: Double): TripRecording {
        val legs = legsFor(tripId)
        val routes = routesFor(tripId)
        val scene = index.coerceIn(legs.indices)
        val progress = fraction.coerceIn(0.0, 1.0)
        val points = mutableListOf<TripPoint>()
        val stops = mutableListOf<RecordedStop>()
        var nightMeters = 0.0
        var nightMillis = 0L
        for (i in 0..scene) {
            val leg = legs[i]
            val limit = if (i == scene) progress else 1.0
            val until = leg.departureAt + ((leg.arrivalAt - leg.departureAt) * limit).toLong()
            val nightPart = nightFraction(leg.departureAt, until)
            nightMeters += leg.kilometers * 1000 * limit * nightPart
            nightMillis += (leg.drivingMinutes * 60_000 * limit * nightPart).toLong()
            routes[i].appendUntil(points, until)
            if (limit >= 1.0 && leg.venues.isNotEmpty()) {
                val automatic = automaticVenue(tripId, stopId(tripId, i))
                val checkpoint = TripPoint(leg.latitude, leg.longitude, leg.arrivalAt)
                stops += RecordedStop(stopId(tripId, i), leg.arrivalAt, checkpoint,
                    userLabel = automatic?.name, resolutionAttempted = automatic != null,
                    departedAt = legs.getOrNull(i + 1)?.departureAt)
            }
        }
        val leg = legs[scene]
        return TripRecording(tripId, legs.first().departureAt, points = points, latest = points.last(), stops = stops,
            departed = true,
            distanceMeters = (legs.take(scene).sumOf { it.kilometers } + leg.kilometers * progress) * 1000,
            movingMillis = ((legs.take(scene).sumOf { it.drivingMinutes } + leg.drivingMinutes * progress) * 60_000).toLong(),
            nightDistanceMeters = nightMeters, nightMovingMillis = nightMillis)
    }
}
