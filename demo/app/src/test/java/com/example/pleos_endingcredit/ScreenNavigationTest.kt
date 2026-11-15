package com.example.pleos_endingcredit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenNavigationTest {
    @Test fun oldActivityStateRestoresTheNumberedPage() {
        assertEquals(Page.A1_PAYOUT_SETUP, Page.fromSavedName("PAYOUT_SETUP"))
        assertEquals(Page.A2_HOME, Page.fromSavedName("HOME"))
        assertEquals(Page.B1_CREDIT_VIDEO, Page.fromSavedName("CREDIT_VIDEO"))
        assertEquals(Page.F1_CATALOG, Page.fromSavedName("ARCHIVE"))
        assertNull(Page.fromSavedName(null))
        assertNull(Page.fromSavedName("unknown"))
        assertEquals(Page.entries.size, Page.entries.map { it.savedName }.toSet().size)
        for (page in Page.entries) assertEquals(page, Page.fromSavedName(page.savedName))
    }

    @Test fun oldActivityStateRestoresTheNumberedSheet() {
        assertEquals(Sheet.A3_TRIP_START, Sheet.fromSavedName("TRIP_START"))
        assertEquals(Sheet.D1_1_COSTS, Sheet.fromSavedName("CATALOG_COSTS"))
        assertEquals("D1-1", Sheet.D1_1_COSTS.screenId)
        assertEquals(Sheet.C1_EDIT_RESULT_PLACES, Sheet.fromSavedName("EDIT_RESULT_PLACES"))
        assertNull(Sheet.fromSavedName(null))
        assertEquals(Sheet.entries.size, Sheet.entries.map { it.savedName }.toSet().size)
        for (sheet in Sheet.entries) assertEquals(sheet, Sheet.fromSavedName(sheet.savedName))
    }

    @Test fun savedCreditFramesMapToTheirCurrentFigmaScenes() {
        assertEquals(listOf("B3-1", "B3-2", "B3-2-1", "B3-3"),
            CreditPlayback.frames.map(CreditPlayback::screenId))
        assertEquals("B3-3", CreditPlayback.screenId(3))
        assertEquals("B3-3", CreditPlayback.screenId(4))
    }
}
