package com.example.pleos_endingcredit

import org.junit.Assert.*
import org.junit.Test

class CreditPlaybackTest {
    @Test fun latestSequenceAdvancesThroughFourChaptersAndAcceptsOlderFilmCheckpoints() {
        assertArrayEquals(intArrayOf(0, 1, 2, 5), CreditPlayback.frames)
        assertEquals(1, CreditPlayback.nextFrame(0))
        assertEquals(2, CreditPlayback.nextFrame(1))
        assertEquals(5, CreditPlayback.nextFrame(2))
        assertNull(CreditPlayback.nextFrame(5))
        assertEquals(CreditPlayback.FILM, CreditPlayback.index(3))
        assertEquals(CreditPlayback.FILM, CreditPlayback.index(4))
        assertEquals(30_000L, CreditPlayback.TOTAL_MILLIS)
        assertEquals(CreditPlayback.TOTAL_MILLIS, CreditPlayback.durations.sum())
    }

    @Test fun cinemaTicketUsesTheTripOrdinalDatesDistanceAndPeople() {
        val content = CreditContent(DemoContent.trips.first(), null, 9)
        assertEquals("009", content.ticketNumber)
        assertEquals("SUN 11.08 — MON 11.09", content.ticketDate)
        assertEquals("6H 40M", content.ticketRuntime)
        assertEquals("웃음소리로 가득했던 6시간 40분", content.castNote)
        assertEquals("A1 — A4", content.ticketSeats)
        assertEquals("0312 1108 0004", content.barcodeText)
        val actual = CreditContent(Trip(id = "actual", name = "새 여행", people = 1, createdOn = "2026-11-11"), null, 1)
        assertEquals("001", actual.ticketNumber)
        assertEquals("A1", actual.ticketSeats)
        assertEquals("---- 1111 0001", actual.barcodeText)
        assertEquals("—", actual.ticketRuntime)
        assertEquals("—", actual.ticketTheater)
        assertEquals("A ROAD MOVIE", actual.ticketSubtitle)
    }

    @Test fun routeDistanceFinishesBeforeTheCaptionAndDoesNotOvershoot() {
        val content = CreditContent(DemoContent.trips.first(), null)
        assertEquals("000000", content.odometer(0, 3000))
        assertEquals("000156", content.odometer(1, 2000))
        assertEquals("000312", content.odometer(1, 4000))
        assertEquals("000312", content.odometer(1, 6000))
        assertEquals("000312", content.odometer(2, 0))
        assertEquals("000312", content.odometer(5, 4000))
        assertEquals("2026.11.08 – 11.09", content.dateText)
    }

    @Test fun recordedTripsNeverReceiveDemoDistanceWeatherOrPlaces() {
        val trip = Trip(id = "real-trip", name = "실제 여행", people = 1, createdOn = "2026-11-08")
        val recording = TripRecording(trip.id, 1794092400000, endedAt = 1794099600000,
            distanceMeters = 12540.0, stops = listOf(
                RecordedStop("unknown", 1794096000000, null),
                RecordedStop("named", 1794096600000, null, userLabel = "방문 장소"),
                RecordedStop("excluded", 1794096700000, null, userLabel = "제외 장소", excluded = true),
            ))
        val content = CreditContent(trip, recording)
        assertFalse(content.demo)
        assertEquals("12.5", content.distanceText)
        assertEquals("000013", content.odometer(5, 0))
        assertEquals(listOf("확인 필요", "방문 장소"), content.stops.map { it.name })
        assertEquals(listOf("주행 시간 0분"), content.moments)
        assertEquals("------", CreditContent(trip, null).odometer(5, 0))
        assertTrue(CreditContent(trip, null).stops.isEmpty())
    }
}
