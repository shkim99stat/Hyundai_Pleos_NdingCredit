package com.example.pleos_endingcredit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.car.Car
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Continuous recording is an explicit, user-started location foreground
 * service. Its mandatory silent ongoing indicator is not a stop alert.
 */
class TripRecordingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: TripRecordingRepository
    private var engine: TripRecordingEngine? = null
    private var signals: TripVehicleSignals? = null
    private val tick = object : Runnable {
        override fun run() {
            val current = engine ?: return
            val trip = TripStore(this@TripRecordingService).activeTrip
            if (trip?.id != current.state.tripId || repository.load(current.state.tripId)?.active == false) {
                stopSelf(); return
            }
            current.tick(System.currentTimeMillis())
            repository.sensor(current.state)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = TripRecordingRepository.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trip = TripStore(this).activeTrip
        if (trip == null || repository.load(trip.id)?.active == false) { stopSelf(); return START_NOT_STICKY }
        if (!hasRecordingPermissions(this)) {
            status = TripSignalState(errors = listOf("위치·차량 조회 권한이 필요해요"))
            stopSelf(); return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "여행 기록 상태", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false); setShowBadge(false)
            })
            val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_trip_recording).setContentTitle("여행 기록 중")
                .setContentText("여행을 끝내거나 취소하면 기록을 멈춥니다")
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setContentIntent(pending)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION, notification)
            isRunning = true
        } catch (_: Exception) {
            status = TripSignalState(errors = listOf("백그라운드 기록을 시작하지 못했어요. 앱에서 권한을 확인해 주세요"))
            stopSelf(); return START_NOT_STICKY
        }
        if (engine?.state?.tripId == trip.id) {
            signals?.startEnergyMonitoring()
            return START_STICKY
        }
        signals?.close()
        val saved = repository.ensure(trip.id)
        val recorder = TripRecordingEngine(saved.copy(interrupted = saved.points.isNotEmpty()))
        engine = recorder
        signals = TripVehicleSignals(this, { state ->
            status = state
            recorder.vehicle(state.parked, state.speedMps, state.doorsOpen, System.currentTimeMillis(), state.speedAt,state.doorOpenedAt)
            repository.sensor(recorder.state)
        }, { point ->
            val receivedAt = System.currentTimeMillis()
            if (recorder.point(point, receivedAt)) {
                recorder.state.latest?.let { repository.location(trip.id, it, receivedAt) }
            }
            repository.sensor(recorder.state)
        }, { sample -> repository.speed(trip.id, sample) }, batteryObserved = { rate, timestamp ->
            recorder.batteryRate(rate, timestamp)
            repository.sensor(recorder.state)
        }).also { it.start() }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(tick)
        signals?.close(); signals = null
        engine?.let { repository.sensor(it.state, true) }; engine = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "trip-recording"
        private const val NOTIFICATION = 410
        const val PLEOS_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        @Volatile internal var status = TripSignalState(errors = listOf("차량·위치 신호를 연결하고 있어요"))
        @Volatile internal var isRunning = false
        internal fun requiredPermissions() = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, PLEOS_LOCATION, "android.car.permission.CAR_SPEED")
        // A0 requests optional seat and EV energy access without requiring them to record a trip.
        internal fun missingPermissions(context: Context): List<String> =
            (requiredPermissions() + TripSeatOccupancy.PERMISSION + Car.PERMISSION_ENERGY).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        internal fun hasRecordingPermissions(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, PLEOS_LOCATION) == PackageManager.PERMISSION_GRANTED
        internal fun start(context: Context) {
            if (!hasRecordingPermissions(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TripRecordingService::class.java)) }
                .onFailure { status = TripSignalState(errors = listOf("앱을 열어 여행 기록을 다시 연결해 주세요")) }
        }
        internal fun stop(context: Context) { context.stopService(Intent(context, TripRecordingService::class.java)) }
    }
}
