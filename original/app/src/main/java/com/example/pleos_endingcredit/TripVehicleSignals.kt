package com.example.pleos_endingcredit

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import kotlin.math.abs

internal data class TripSignalState(val parked: Boolean? = null, val speedMps: Double? = null,
    val doorsOpen: Boolean? = null, val canInteract: Boolean = false,
    val locationAvailable: Boolean = false, val errors: List<String> = emptyList(), val speedAt: Long = 0L,
    val doorOpenedAt: Long = 0L)

/** Read-only AAOS gear/speed plus Pleos door/location subscriptions. No state
 * changes are inferred from a successful registration. Unknown values fail
 * closed, and each original listener is unregistered before releasing clients.
 */
internal class TripVehicleSignals(context: Context,
    private val changed: (TripSignalState) -> Unit, private val located: (TripPoint) -> Unit,
    private val speedObserved: (TripSpeedSample) -> Unit = {},
    private val batteryObserved: (Double?, Long) -> Unit = { _, _ -> },
) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var active = false
    private var generation = 0
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var energySubscriptionStarted = false
    private var ux: CarUxRestrictionsManager? = null
    private var vehicle: Vehicle? = null
    private var door: Door? = null
    private var location: FusedLocation? = null
    private var locationListener: FusedLocationListener? = null
    private var state = TripSignalState()
    private val doorListeners = mutableMapOf<DoorArea, DoorPositionListener>()
    private val doorValues = mutableMapOf<DoorArea, Boolean?>()
    private val revisions = mutableMapOf<Int, Int>()
    private val errors = linkedMapOf<String, String>()

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val token = generation
            deliver(token) { revisions[value.propertyId] = (revisions[value.propertyId] ?: 0) + 1; accept(value) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            val token = generation
            deliver(token) { propertyUnavailable(propertyId) }
        }
    }

    @Suppress("DEPRECATION") fun start() {
        stop()
        active = true
        val token = ++generation
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            error("vehicle", "차량 신호를 사용할 수 없는 기기예요")
        } else try {
            car = Car.createCar(context, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) = deliver(token) {
                    val client = car ?: return@deliver
                    try {
                        val manager = client.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                        properties = manager
                        subscribeProperty(manager, VehiclePropertyIds.GEAR_SELECTION, token)
                        subscribeProperty(manager, VehiclePropertyIds.PERF_VEHICLE_SPEED, token, 1f)
                        startEnergyMonitoring()
                    } catch (_: Exception) { error("vehicle", "기어·속도 신호 연결을 확인해 주세요") }
                    try {
                        ux = (client.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager).also { manager ->
                            manager.registerListener { restriction -> deliver(token) {
                                state = state.copy(canInteract = !restriction.isRequiresDistractionOptimization); publish()
                            } }
                            state = state.copy(canInteract = !manager.currentCarUxRestrictions.isRequiresDistractionOptimization)
                        }
                    } catch (_: Exception) { state = state.copy(canInteract = false) }
                    publish()
                }
                override fun onServiceDisconnected(name: ComponentName?) = deliver(token) {
                    properties = null
                    energySubscriptionStarted = false
                    val now = System.currentTimeMillis()
                    state = state.copy(parked = null, speedMps = null, canInteract = false, speedAt = now)
                    speedObserved(TripSpeedSample(now, null))
                    batteryObserved(null, 0L)
                    error("vehicle", "차량 신호 연결이 끊겼어요")
                }
            }, handler)
            car?.connect()
        } catch (_: Exception) { error("vehicle", "차량 신호를 연결하지 못했어요") }
        startDoors(token)
        startLocation(token)
        handler.postDelayed({ if (active && generation == token) {
            if (state.parked == null) error("gear", "기어 정보를 기다리고 있어요")
            if (!state.locationAvailable) error("location", "위치 신호를 기다리고 있어요")
        } }, 15_000)
    }

    fun startEnergyMonitoring() {
        val manager = properties ?: return
        if (energySubscriptionStarted ||
            context.checkSelfPermission(Car.PERMISSION_ENERGY) != PackageManager.PERMISSION_GRANTED) return
        energySubscriptionStarted = true
        subscribeProperty(manager, VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE, generation, 1f)
    }

    @Suppress("DEPRECATION") private fun subscribeProperty(manager: CarPropertyManager, id: Int, token: Int,
        rate: Float = CarPropertyManager.SENSOR_RATE_ONCHANGE,
    ) {
        try {
            if (manager.getCarPropertyConfig(id) == null || !manager.registerCallback(callback, id, rate)) {
                propertyUnavailable(id); return
            }
            val revision = revisions[id] ?: 0
            worker.execute {
                val result = runCatching { manager.getProperty<Any>(id, 0) }
                deliver(token) { if ((revisions[id] ?: 0) == revision) {
                    result.fold(onSuccess = ::accept, onFailure = { propertyUnavailable(id) })
                } }
            }
        } catch (_: Exception) { propertyUnavailable(id) }
    }

    private fun accept(value: CarPropertyValue<*>) {
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) { propertyUnavailable(value.propertyId); return }
        if (value.propertyId == VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE) {
            val fresh = SystemClock.elapsedRealtimeNanos() - value.timestamp in 0L..5_000_000_000L
            val rate = (value.value as? Float)?.takeIf { fresh && it.isFinite() }
            batteryObserved(rate?.toDouble(), value.timestamp)
            return
        }
        when (value.propertyId) {
            VehiclePropertyIds.GEAR_SELECTION -> {
                val gear = (value.value as? Number)?.toInt()
                state = state.copy(parked = if (gear == null || gear == VehicleGear.GEAR_UNKNOWN) null else gear == VehicleGear.GEAR_PARK)
                errors.remove("gear")
            }
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                val now = System.currentTimeMillis()
                val speed = (value.value as? Number)?.toDouble()?.takeIf { it.isFinite() }
                state = state.copy(speedMps = speed?.let(::abs), speedAt = now)
                speedObserved(TripSpeedSample(now, speed))
                if (speed == null) errors["speed"] = "속도 정보 없이 위치를 기록하고 있어요"
                else errors.remove("speed")
            }
        }
        errors.remove("vehicle")
        publish()
    }

    private fun propertyUnavailable(id: Int) {
        if (id == VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE) {
            batteryObserved(null, 0L)
            return
        }
        if (id == VehiclePropertyIds.GEAR_SELECTION) {
            state = state.copy(parked = null); error("gear", "기어 정보를 확인할 수 없어 정차 기록을 기다립니다")
        } else {
            val now = System.currentTimeMillis()
            state = state.copy(speedMps = null, speedAt = now)
            speedObserved(TripSpeedSample(now, null))
            error("speed", "속도 정보 없이 위치를 기록하고 있어요")
        }
    }

    private fun startDoors(token: Int) {
        try {
            val client = Vehicle(context).also { vehicle = it; it.initialize() }
            val api = client.getDoor().also { door = it }
            api.checkDoorPositionCapabilities(
                onComplete = { config: CapabilityConfig, areas: Map<DoorArea, AreaBounds<Int>> -> deliver(token) {
                    if (config.accessType != AccessType.READ && config.accessType != AccessType.READ_WRITE) {
                        error("doors", "문 열림 조회 권한이 없어요"); return@deliver
                    }
                    // Hood/trunk events do not represent passengers getting out.
                    val cabin = areas.filter { (area, bounds) -> area.values.isNotEmpty() &&
                        area.values.all { it.name.startsWith("ROW_") } && bounds.minValue == 0 && bounds.maxValue > 0 }
                    if (cabin.isEmpty()) { error("doors", "조회 가능한 탑승자 문 정보가 없어요"); return@deliver }
                    cabin.forEach { (area, bounds) ->
                        doorValues[area] = null
                        var events = 0
                        fun accept(pos: Int?, live: Boolean = false) {
                            val previous = doorValues[area]
                            val next = if (pos == null || pos !in bounds.minValue..bounds.maxValue) null else pos > 0
                            doorValues[area] = next
                            if (live && previous == false && next == true) state = state.copy(doorOpenedAt = System.currentTimeMillis())
                            state = state.copy(doorsOpen = when {
                                doorValues.values.any { it == true } -> true
                                doorValues.values.all { it == false } -> false
                                else -> null
                            })
                            if(state.doorsOpen==null)errors["doors"]="문 열림 신호를 기다리고 있어요" else errors.remove("doors")
                            publish()
                        }
                        val listener = object : DoorPositionListener {
                            override fun onPositionUpdated(pos: Int?) = deliver(token) { events++; accept(pos,true) }
                            override fun onFailed(e: Exception) = deliver(token) {
                                events++; accept(null); error("doors", "문 열림 신호를 확인할 수 없어요")
                            }
                        }
                        doorListeners[area] = listener
                        try {
                            api.registerDoorPosition(area, listener)
                            val before = events
                            api.getCurrentDoorPosition(area, { pos -> deliver(token) { if (events == before) accept(pos) } },
                                { _ -> deliver(token) { if (events == before) accept(null) } })
                        } catch (_: Exception) { error("doors", "문 열림 신호를 연결하지 못했어요") }
                    }
                } }, onFailed = { _ -> deliver(token) { error("doors", "문 열림 지원 정보를 확인하지 못했어요") } })
        } catch (_: Exception) { error("doors", "문 열림 신호 연결을 확인해 주세요") }
    }

    private fun startLocation(token: Int) {
        try {
            val client = FusedLocation(context).also { location = it }
            if (!client.isSupported()) { error("location", "이 기기에서 위치 서비스를 사용할 수 없어요"); return }
            val listener = object : FusedLocationListener {
                override fun onChanged(value: LocationData?) = deliver(token) {
                    val point = value?.let { TripPoint(it.latitude, it.longitude, it.timestampMillis,
                        confidence = it.confidence.takeIf { confidence -> confidence.isFinite() }) }
                    if (point == null || !point.valid() || System.currentTimeMillis() - point.at !in -5000L..30_000L) {
                        state = state.copy(locationAvailable = false); error("location", "최근 위치 신호가 없어요")
                    } else {
                        state = state.copy(locationAvailable = true); errors.remove("location")
                        located(point); publish()
                    }
                }
                override fun onError(exception: Exception) = deliver(token) {
                    state = state.copy(locationAvailable = false); error("location", "위치 신호 연결을 확인해 주세요")
                }
            }
            locationListener = listener
            client.initialize()
            client.registerFusedLocationCallback(listener)
            // A cached fix is intentionally not promoted into a fresh trip event.
        } catch (_: Exception) { error("location", "위치 조회 권한 또는 서비스 연결을 확인해 주세요") }
    }

    fun stop() {
        active = false; generation++
        energySubscriptionStarted = false
        runCatching { properties?.unregisterCallback(callback) }; properties = null
        runCatching { ux?.unregisterListener() }; ux = null
        runCatching { car?.disconnect() }; car = null
        doorListeners.forEach { (area, listener) -> runCatching { door?.unregisterDoorPosition(area, listener) } }
        doorListeners.clear(); doorValues.clear()
        runCatching { vehicle?.release() }; vehicle = null; door = null
        locationListener?.let { listener -> runCatching { location?.unregisterFusedLocationCallback(listener) } }
        runCatching { location?.release() }; location = null; locationListener = null
        state = TripSignalState(); errors.clear(); revisions.clear()
    }

    fun close() { stop(); worker.shutdownNow() }
    private fun error(key: String, message: String) { errors[key] = message; publish() }
    private fun publish() { state = state.copy(errors = errors.values.toList()); changed(state) }
    private fun deliver(token: Int, action: () -> Unit) { handler.post { if (active && token == generation) action() } }
}
