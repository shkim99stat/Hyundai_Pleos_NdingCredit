package com.example.pleos_endingcredit

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** The single Activity is DO only with this app-wide gate: A4 stays read-only,
 * while every non-recording interactive surface is replaced by drivingPreview.
 */
internal class TripUxGuard(private val context:Context,private val changed:(Boolean)->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private var car:Car?=null
    private var manager:CarUxRestrictionsManager?=null
    private var generation=0
    @Suppress("DEPRECATION") fun start() {
        stop();val token=++generation;changed(false)
        if(!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)){changed(true);return}
        try {
            car=Car.createCar(context,object:ServiceConnection {
                override fun onServiceConnected(name:ComponentName?,service:IBinder?) {
                    if(token!=generation)return
                    try {
                        manager=(car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager).also { api ->
                            api.registerListener { state -> handler.post {if(token==generation)changed(!state.isRequiresDistractionOptimization)} }
                            changed(!api.currentCarUxRestrictions.isRequiresDistractionOptimization)
                        }
                    } catch(_:Exception){changed(false)}
                }
                override fun onServiceDisconnected(name:ComponentName?){if(token==generation)changed(false)}
            },handler)
            car?.connect()
        }catch(_:Exception){changed(false)}
    }
    fun stop(){generation++;runCatching{manager?.unregisterListener()};manager=null;runCatching{car?.disconnect()};car=null}
}
