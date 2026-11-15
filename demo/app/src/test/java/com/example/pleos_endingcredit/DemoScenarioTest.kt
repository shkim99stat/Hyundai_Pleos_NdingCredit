package com.example.pleos_endingcredit

import org.junit.Assert.*
import org.junit.Test

class DemoScenarioTest {
    @Test fun twoDaysEndAtSeoulAndExcludeTheDestinationFromVisitedPlaces() {
        val final = DemoScenario.snapshot("showcase-test", DemoScenario.legs.lastIndex, 1.0)
        assertEquals(538_000.0, final.distanceMeters, .01)
        assertEquals(530L * 60_000, final.movingMillis)
        assertEquals(8, final.stops.size)
        assertEquals(8, final.stops.map { it.id }.distinct().size)
        assertEquals(37.567005, final.latest!!.latitude, .000001)
        assertEquals(126.977993, final.latest!!.longitude, .000001)
        assertEquals(DemoScenario.at("15T19:50"), final.latest!!.at)
        assertTrue(final.points.all { it.valid() })
        assertTrue(final.points.zipWithNext().all { (a,b) -> a.at <= b.at })
        assertEquals(4, final.stops.count { it.at < DemoScenario.at("15T00:00") })
        for ((leg, stop) in DemoScenario.legsFor(final.tripId).zip(final.stops)) {
            assertEquals(TripPoint(leg.latitude, leg.longitude, leg.arrivalAt), stop.point)
        }
    }
    @Test fun playbackUsesRoadVerticesAndAdvancesByDistance() {
        val half = DemoScenario.snapshot("showcase-test", 0, .5)
        val full = DemoScenario.snapshot("showcase-test", 0, 1.0)
        fun distance(points: List<TripPoint>) = points.zipWithNext(::metersBetween).sum()
        assertTrue(full.points.size > 1_000)
        assertEquals(distance(full.points) / 2, distance(half.points), 1.0)
        assertEquals(half.points.dropLast(1), full.points.take(half.points.size - 1))
        assertEquals(half.latest, DemoScenario.mapPoints(half).last())
    }
    @Test fun stopsKeepTheirPinsAndResumeOnTheSameRoad() {
        val parked = DemoScenario.snapshot("showcase-test", 7, 1.0)
        val departure = DemoScenario.snapshot("showcase-test", 8, 0.0)
        val pin = parked.stops.last().point!!
        val vehicle = parked.latest!!
        assertEquals(37.4700, pin.latitude, 0.0)
        assertEquals(128.2800, pin.longitude, 0.0)
        assertTrue(metersBetween(pin, vehicle) in 800.0..850.0)
        assertEquals(vehicle.copy(at = DemoScenario.legsFor(parked.tripId)[8].departureAt), departure.latest)

        val oldStart = TripPoint(DemoScenario.origin.first, DemoScenario.origin.second, parked.startedAt)
        val legacy = parked.copy(points = listOf(oldStart, pin), latest = pin)
        assertEquals(parked, DemoScenario.restoreRoadGeometry(legacy))
    }
    @Test fun partialPlaybackDoesNotRevealFutureStopsOrTravel() {
        val first = DemoScenario.snapshot("showcase-test", 0, .5)
        assertTrue(first.stops.isEmpty())
        assertEquals(109_000.0, first.distanceMeters, .01)
        val second = DemoScenario.snapshot("showcase-test", 1, .5)
        assertEquals(1, second.stops.size)
        assertEquals(220_000.0, second.distanceMeters, .01)
        assertEquals(DemoScenario.at("14T13:07:30"), second.latest!!.at)
    }
    @Test fun eachDayHasFourCheckpointsThreeUniqueVenuesAndOneManualChoice() {
        val id="showcase-test"
        val final=DemoScenario.snapshot(id,DemoScenario.legs.lastIndex,1.0)
        final.stops.groupBy { java.time.Instant.ofEpochMilli(it.at).atZone(DemoScenario.zone).toLocalDate() }
            .values.forEach { stops ->
                assertEquals(4,stops.size)
                assertEquals(3,stops.count { DemoScenario.automaticallyResolved(id,it) })
                assertEquals(1,stops.count { it.userLabel==null })
                stops.forEach { stop ->
                    val choices=DemoScenario.candidates(id,stop.id)
                    if(stop.userLabel!=null) {
                        assertEquals(1,choices.size)
                        assertEquals(choices.single().name,stop.userLabel)
                    } else assertEquals(3,choices.size)
                }
            }
        assertEquals(DemoScenario.at("15T09:00"),DemoScenario.legs[4].departureAt)
        assertEquals("market",DemoScenario.category("해변 앞 마트 [가상]"))
        assertFalse(DemoScenario.isDemo("real-trip"))
    }
    @Test fun nightDrivingAccumulatesOnlyInReplayedNightIntervals() {
        assertFalse(DemoScenario.isNight(DemoScenario.at("15T17:59")))
        assertTrue(DemoScenario.isNight(DemoScenario.at("15T18:00")))
        assertEquals(.5,DemoScenario.nightFraction(DemoScenario.at("15T17:00"),DemoScenario.at("15T19:00")),.00001)
        assertEquals(.5,DemoScenario.nightFraction(DemoScenario.at("15T05:00"),DemoScenario.at("15T07:00")),.00001)
        val beforeNight=DemoScenario.snapshot("showcase-test",2,1.0)
        assertEquals(0.0,beforeNight.nightDistanceMeters!!,.01)
        val halfway=DemoScenario.snapshot("showcase-test",8,.5)
        assertEquals(66_000.0,halfway.nightDistanceMeters!!,.01)
        assertEquals(60L*60_000,halfway.nightMovingMillis)
        val final=DemoScenario.snapshot("showcase-test",8,1.0)
        assertEquals(131_000.0,final.nightDistanceMeters!!,.01)
        assertEquals(110L*60_000,final.nightMovingMillis)
        assertTrue(final.nightMovingMillis!!<final.movingMillis)
    }

    @Test fun currentScenarioDistributesEightStopsAcrossTwoDays() {
        val id = DemoScenario.CURRENT_PREFIX + "test"
        val final = DemoScenario.snapshot(id, DemoScenario.legs.lastIndex, 1.0)
        val stops = final.stops
        val points = stops.map { requireNotNull(it.point) }
        assertEquals(8, stops.size)
        assertTrue(points.indices.all { a ->
            (a + 1 until points.size).all { b -> metersBetween(points[a], points[b]) >= 15_000 }
        })
        assertEquals(listOf(1, 6), stops.indices.filter { stops[it].userLabel == null })
        assertTrue(stops.all { DemoScenario.candidates(id, it.id).first().address.isNotBlank() })
        assertEquals(504_400.0, final.distanceMeters, .01)
        assertEquals(436L * 60_000, final.movingMillis)
        assertEquals(120_600.0, final.nightDistanceMeters!!, .01)
        assertEquals(104L * 60_000, final.nightMovingMillis)
        assertEquals(DemoScenario.at("15T19:07"), final.latest!!.at)
    }

    @Test fun energyAndEfficiencyChangeDuringPlaybackAndHoldAtAStop() {
        val id = DemoScenario.CURRENT_PREFIX + "energy-test"
        val early = DemoScenario.snapshot(id, 0, .25)
        val later = DemoScenario.snapshot(id, 0, .75)
        val earlyKwh = demoEnergyKwh(early)
        val laterKwh = demoEnergyKwh(later)
        assertTrue(earlyKwh > 0.0 && laterKwh > earlyKwh)
        assertTrue(early.distanceMeters / earlyKwh > later.distanceMeters / laterKwh)
        val arrived = DemoScenario.snapshot(id, 0, 1.0)
        val departure = DemoScenario.snapshot(id, 1, 0.0)
        assertEquals(demoEnergyKwh(arrived), demoEnergyKwh(departure), 1e-9)
    }
}
