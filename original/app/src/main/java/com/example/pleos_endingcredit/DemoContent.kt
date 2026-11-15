package com.example.pleos_endingcredit

/** Bundled demo history, installed once without replacing the user's saved trips. */
internal object DemoContent {
    const val VERSION_KEY = "demo_content_version"
    const val VERSION = 2

    private val legacyTrips = listOf(
        Trip(id = "demo-gangneung", name = "강릉 여행", people = 4, createdOn = "2026-11-08"),
        Trip(id = "demo-gyeongju", name = "경주 여행", people = 4, createdOn = "2026-10-12"),
        Trip(id = "demo-busan", name = "부산 여행", people = 4, createdOn = "2026-09-20"),
        Trip(id = "demo-sokcho", name = "속초 여행", people = 4, createdOn = "2026-08-15"),
    )
    val homeTrips = listOf(
        Trip(id = "demo-home-sokcho", name = "속초 1박 2일", people = 4, createdOn = "2026-10-25"),
        Trip(id = "demo-home-busan", name = "부산 여행", people = 4, createdOn = "2026-08-15"),
        Trip(id = "demo-home-gapyeong", name = "가평 당일치기", people = 4, createdOn = "2026-04-12"),
        Trip(id = "demo-home-jeju", name = "우리 가족 제주 여행", people = 4, createdOn = "2026-02-14"),
        Trip(id = "demo-home-solo", name = "혼자 드라이브", people = 1, createdOn = "2025-12-20"),
    )
    val trips = legacyTrips + homeTrips
    val legacyResume = CreditResume(legacyTrips.first().id, CreditScene.STOPS.ordinal)
    val resume = CreditResume(homeTrips[1].id, CreditScene.STOPS.ordinal)

    fun homeIndex(trip: Trip): Int? = homeTrips.indexOfFirst { it.id == trip.id }.takeIf { it >= 0 }

    fun coverIndex(trip: Trip): Int? {
        val legacyIndex = legacyTrips.indexOfFirst { it.id == trip.id }
        if (legacyIndex >= 0) return legacyIndex
        return when (homeIndex(trip)) {
            0 -> 3
            1 -> 2
            2, 3, 4 -> 0
            else -> null
        }
    }

    fun destination(trip: Trip): String? = when (homeIndex(trip)) {
        0 -> "속초"
        1 -> "부산"
        2 -> "가평"
        3 -> "제주"
        4 -> null
        else -> coverIndex(trip)?.let { listOf("강릉", "경주", "부산", "속초")[it] }
    }

    fun coverResource(trip: Trip): Int = when (coverIndex(trip)) {
        1 -> R.drawable.archive_gyeongju
        2 -> R.drawable.archive_busan
        3 -> R.drawable.archive_sokcho
        else -> R.drawable.archive_gangneung
    }
}

internal enum class CreditScene(val title: String) {
    TITLE("여행의 시작"),
    ROUTE("다녀온 길"),
    HIGHLIGHTS("그날의 장면"),
    STOPS("우리가 남긴 발자취"),
    CAST("같은 창밖을 바라본 우리"),
    FINALE("여행을 마치며"),
}
