package com.example.pleos_endingcredit

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TripSampleStoreTest {
    @Test fun locationRecordingDoesNotRequireOptionalVehicleSpeedPermission() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val context=object:android.content.ContextWrapper(base) {
            override fun checkPermission(permission:String,pid:Int,uid:Int):Int =
                if(permission==android.Manifest.permission.ACCESS_FINE_LOCATION ||
                    permission==TripRecordingService.PLEOS_LOCATION) android.content.pm.PackageManager.PERMISSION_GRANTED
                else android.content.pm.PackageManager.PERMISSION_DENIED
        }
        assertTrue(TripRecordingService.hasRecordingPermissions(context))
    }

    @Test fun originalLocationsAndMissingSpeedSurviveReopenAndPreviewThinning() = withStore { context,name,store ->
        val points=(0..10_050).map { TripPoint(37.0,127.0+it*.000001,1000L+it*1000,
            confidence=if(it%2==0).8f else null) }
        val db=store.writableDatabase
        db.beginTransaction()
        try {
            points.forEach { store.appendLocation("trip",TripLocationSample(it,it.at+25)) }
            store.appendSpeed("trip",TripSpeedSample(2000,0.0))
            store.appendSpeed("trip",TripSpeedSample(3000,null))
            store.appendSpeed("trip",TripSpeedSample(4000,12.5))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        assertTrue(TripRoutePreview.compact(points).size<points.size)
        store.close()
        TripSampleStore(context,name).use { reopened ->
            val originals=reopened.locations("trip")
            assertEquals(points,originals.map { it.point })
            assertEquals(points.last().at+25,originals.last().receivedAt)
            assertTrue(originals.none { it.legacyPreview })
            assertEquals(listOf(0.0,null,12.5),reopened.speeds("trip").map { it.speedMps })
            reopened.deleteTrip("trip")
            assertTrue(reopened.locations("trip").isEmpty());assertTrue(reopened.speeds("trip").isEmpty())
        }
    }

    @Test fun legacyPreviewImportIsIdempotentAndNeverClaimsOriginalCoverage() = withStore { _,_,store ->
        val first=TripPoint(37.0,127.0,1000)
        val second=TripPoint(37.0,127.001,2000)
        store.importLegacy("legacy",listOf(first,second))
        store.importLegacy("legacy",listOf(first,second))
        store.appendLocation("legacy",TripLocationSample(second.copy(at=3000),3025))
        val samples=store.locations("legacy")
        assertEquals(3,samples.size)
        assertTrue(samples.take(2).all { it.legacyPreview })
        assertFalse(samples.last().legacyPreview)
        assertTrue(store.speeds("legacy").isEmpty())
    }

    private fun withStore(test:(android.content.Context,String,TripSampleStore)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="trip-samples-test-${UUID.randomUUID()}.db"
        val store=TripSampleStore(context,name)
        try { test(context,name,store) }
        finally { store.close();context.deleteDatabase(name) }
    }
}
