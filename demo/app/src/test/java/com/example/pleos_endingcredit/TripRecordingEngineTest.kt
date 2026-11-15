package com.example.pleos_endingcredit

import org.junit.Assert.*
import org.junit.Test

class TripRecordingEngineTest {
    private val t=1_000_000L
    private fun engine()=TripRecordingEngine(TripRecording("test",t))
    private fun TripRecordingEngine.fix(at:Long=t+1000,lng:Double=127.0)=point(TripPoint(37.0,lng,at),at)
    private fun TripRecordingEngine.depart(at:Long=t+2000)=vehicle(false,2.0,false,at)

    @Test fun initialParkAndOpenAreNotStops() {
        val e=engine();e.fix();e.vehicle(true,0.0,true,t+1100)
        assertTrue(e.state.pending.isEmpty());e.depart();assertTrue(e.state.stops.isEmpty())
    }
    @Test fun initialSubscriptionSnapshotAfterProcessRestartIsNotANewDoorOpening() {
        val e=TripRecordingEngine(TripRecording("resume",t,departed=true))
        e.fix();e.vehicle(true,0.0,true,t+2000,t+2000,0L)
        assertTrue(e.state.pending.isEmpty())
        e.vehicle(true,0.0,false,t+3000,t+3000,0L)
        e.vehicle(true,0.0,true,t+4000,t+4000,t+4000)
        assertEquals(1,e.state.pending.size)
    }
    @Test fun noDoorAndNoTimeThreshold() {
        val e=engine();e.depart();e.fix(t+3000);e.vehicle(true,0.0,false,t+3100)
        e.tick(t+100_000);assertTrue(e.state.pending.isEmpty())
        e.fix(t+100_001);e.vehicle(true,0.0,true,t+100_002)
        assertEquals(1,e.state.pending.size)
        assertTrue(e.state.stops.isEmpty())
        e.depart(t+100_003);assertEquals(1,e.state.stops.size)
    }
    @Test fun repeatedDoorsInside200MetersMergeUntilDeparture() {
        val e=engine();e.depart();e.fix(t+3000);e.vehicle(true,0.0,true,t+3100)
        e.vehicle(true,0.0,false,t+3200);e.fix(t+4000,127.0015);e.vehicle(true,0.0,true,t+4100)
        assertEquals(1,e.state.pending.size);e.depart(t+4200);assertEquals(1,e.state.stops.size)
        e.fix(t+5000,127.0);e.vehicle(true,0.0,true,t+5100);e.depart(t+5200)
        assertEquals(2,e.state.stops.size)
    }
    @Test fun openingsOutsideRadiusAreSeparate() {
        val e=engine();e.depart();e.fix(t+3000);e.vehicle(true,0.0,true,t+3100)
        e.vehicle(true,0.0,false,t+3200);e.fix(t+4000,127.003);e.vehicle(true,0.0,true,t+4100)
        assertEquals(2,e.state.pending.size)
    }
    @Test fun finalDoorOpeningIsDroppedOnlyAtFinish() {
        val e=engine();e.depart();e.fix(t+3000);e.vehicle(true,0.0,true,t+3100);e.depart(t+4000)
        e.fix(t+5000);e.vehicle(true,0.0,true,t+5100);assertEquals(1,e.state.pending.size)
        e.finish(t+6000);assertEquals(1,e.state.stops.size);assertTrue(e.state.pending.isEmpty());assertFalse(e.state.active)
    }
    @Test fun staleSpeedFromBeforeParkExitDoesNotCountAsDeparture() {
        val e=engine();e.vehicle(true,0.0,false,t+1000)
        e.vehicle(false,5.0,false,t+2000,t+500);assertFalse(e.state.departed)
        e.vehicle(false,5.0,false,t+3000,t+3000);assertTrue(e.state.departed)
    }
    @Test fun nullGearAndCreepingAreNotDepartures() {
        val e=engine();e.vehicle(null,10.0,true,t+1000);assertFalse(e.state.departed)
        e.vehicle(false,.4,true,t+2000);assertFalse(e.state.departed)
    }
    @Test fun missingLocationNeverBecomesZeroZeroOrLaterDeparturePosition() {
        val e=engine();e.depart();e.vehicle(true,0.0,true,t+3000);e.depart(t+4000)
        e.fix(t+5000);assertNull(e.state.stops.single().point)
    }
    @Test fun aFreshFixCanFillTheSameParkedEpisode() {
        val e=engine();e.depart();e.vehicle(true,0.0,true,t+3000);e.fix(t+4000)
        assertNotNull(e.state.pending.single().point)
    }
    @Test fun invalidOldFutureAndOutOfOrderFixesAreIgnored() {
        val e=engine();e.fix();e.point(TripPoint(91.0,127.0,t+2000),t+2000)
        e.point(TripPoint(0.0,0.0,t+4000),t+100_000);e.fix(t+500)
        e.point(TripPoint(37.0,127.0,t+50000),t+2000)
        assertEquals(t+1000,e.state.latest!!.at)
    }
    @Test fun zeroZeroIsARealCoordinate() {
        val e=engine();e.point(TripPoint(0.0,0.0,t+1000),t+1000);assertEquals(0.0,e.state.latest!!.latitude,0.0)
    }
    @Test fun preDepartureOriginUsesLatestParkedFixWithoutAddingDistance() {
        val e=engine();e.vehicle(true,0.0,false,t+100)
        e.point(TripPoint(37.421997,-122.084,t+1000),t+1000)
        val seoul=TripPoint(37.5665,126.9780,t+2000)
        e.point(seoul,t+2000)
        assertEquals(listOf(seoul),e.state.points)
        assertEquals(0.0,e.state.distanceMeters,0.0)
        e.depart(t+3000)
        e.point(TripPoint(37.56651,126.97801,t+4000),t+4000)
        assertEquals(seoul,e.state.points.first())
        assertTrue(e.state.distanceMeters>0.0)
    }
    @Test fun locationGapAndImpossibleJumpAreNotDistance() {
        val e=engine();e.depart();e.fix(t+3000);e.fix(t+4000,127.1)
        assertEquals(0.0,e.state.distanceMeters,.01);assertTrue(e.state.points.last().gapBefore)
        e.fix(t+100_000,127.1001);assertTrue(e.state.points.last().gapBefore)
    }
    @Test fun stoppedTimeAndMissingHeartbeatAreNotDrivingTime() {
        val e=engine();e.depart();e.tick(t+3000);assertEquals(1000L,e.state.movingMillis)
        e.vehicle(true,0.0,false,t+4000);e.tick(t+20_000);assertEquals(2000L,e.state.movingMillis)
        e.depart(t+21000);e.tick(t+100_000);assertEquals(2000L,e.state.movingMillis)
    }
    @Test fun missingVehicleSignalsDoNotDiscardFreshLocationSamples() {
        val e=engine()
        e.vehicle(null,null,null,t)
        assertTrue(e.point(TripPoint(37.0,127.0,t+1000,confidence=.7f),t+1000))
        assertTrue(e.point(TripPoint(37.0,127.0001,t+2000,confidence=.6f),t+2000))
        assertEquals(2,e.state.points.size)
        assertTrue(e.state.distanceMeters>0)
        assertEquals(.6f,e.state.latest!!.confidence!!,0f)
        assertFalse(e.state.departed)
        assertEquals(0L,e.state.movingMillis)
    }
    @Test fun staleSpeedStopsDrivingTimeButNotLocationCollection() {
        val e=engine();e.depart();e.fix(t+3000)
        e.tick(t+9000)
        assertTrue(e.point(TripPoint(37.0,127.0001,t+10000),t+10000))
        assertEquals(2,e.state.points.size)
        assertTrue(e.state.distanceMeters>0)
        assertEquals(0L,e.state.movingMillis)
    }
    @Test fun stationaryAndHighFrequencyFixesRemainAvailableForOriginalStorage() {
        val e=engine();e.vehicle(true,0.0,false,t)
        assertTrue(e.point(TripPoint(37.0,127.0,t+1000),t+1000))
        assertTrue(e.point(TripPoint(37.0,127.000001,t+1100),t+1100))
        assertEquals(1,e.state.points.size)
        assertEquals(0.0,e.state.distanceMeters,0.0)
        e.vehicle(false,null,null,t+1200)
        assertTrue(e.point(TripPoint(37.0,127.0001,t+1300),t+1300))
        assertEquals(t+1300,e.state.latest!!.at)
    }
    @Test fun processRecoveryLeavesAnExplicitRouteBreak() {
        val old=TripPoint(37.0,127.0,t+1000)
        val e=TripRecordingEngine(TripRecording("recover",t,points=listOf(old),latest=old,interrupted=true))
        assertTrue(e.point(TripPoint(37.0,127.0001,t+2000),t+2000))
        assertTrue(e.state.latest!!.gapBefore)
        assertTrue(e.state.points.last().gapBefore)
        assertEquals(0.0,e.state.distanceMeters,0.0)
    }
    @Test fun endedTripsAndRepeatedFixesAreNotAcceptedForOriginalStorage() {
        val e=engine();val point=TripPoint(37.0,127.0,t+1000)
        assertTrue(e.point(point,t+1000));assertFalse(e.point(point,t+1100))
        e.finish(t+1200)
        assertFalse(e.point(point.copy(at=t+1300),t+1300))
    }
    @Test fun uniqueAndRestAreaRulesRequireCompleteResults() {
        val place=PlaceCandidate("1","하나","카페",20.0)
        assertEquals("1",decidePlaces(listOf(place),true).automaticId)
        assertNull(decidePlaces(listOf(place),false).automaticId)
        val rest=PlaceCandidate("2","휴게소","휴게소",40.0,true)
        assertEquals("2",decidePlaces(listOf(place,rest),true).automaticId)
        assertNull(decidePlaces(listOf(place,rest.copy(id="3"),rest),true).automaticId)
    }
    @Test fun overlappingCandidatesAreLimitedToThreeRealPlaces() {
        val places=(1..7).map {PlaceCandidate("$it","장소 $it","가게",it*10.0)}
        val decision=decidePlaces(places+places.first(),true)
        assertNull(decision.automaticId);assertEquals(3,decision.candidates.size)
        assertEquals(listOf("1","2","3"),decision.candidates.map{it.id})
        assertTrue(decidePlaces(emptyList(),true).candidates.isEmpty())
    }
}
