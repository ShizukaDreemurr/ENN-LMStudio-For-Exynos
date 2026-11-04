package dev.exynoslm.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import dev.exynoslm.runtime.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class DeviceSnapshot(
    val availRamBytes: Long,
    val totalRamBytes: Long,
    val lowMemory: Boolean,
    val appPssKb: Int,
    val thermalStatus: Int,          // PowerManager.THERMAL_STATUS_*
    val thermalHeadroom: Float?,     // 0..1+, forecast 10s ahead (API 30+)
    val batteryPct: Int,
    val batteryTempC: Float,
    val charging: Boolean,
    val powerSave: Boolean,
) {
    val thermalSevere get() = thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    fun toDeviceState() = DeviceState(availRamBytes, totalRamBytes, thermalSevere, powerSave)
}

/** Local-only device telemetry. Nothing here leaves the device. */
class DeviceMonitor(private val context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)
    private val pm = context.getSystemService(PowerManager::class.java)

    fun snapshot(): DeviceSnapshot {
        val mi = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val bat = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pss = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
        return DeviceSnapshot(
            availRamBytes = mi.availMem, totalRamBytes = mi.totalMem, lowMemory = mi.lowMemory, appPssKb = pss,
            thermalStatus = pm.currentThermalStatus,
            thermalHeadroom = pm.getThermalHeadroom(10).takeIf { !it.isNaN() },
            batteryPct = if (level >= 0) level * 100 / scale else -1,
            batteryTempC = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            charging = (bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            powerSave = pm.isPowerSaveMode,
        )
    }

    fun stream(periodMs: Long = 1000): Flow<DeviceSnapshot> = flow { while (true) { emit(snapshot()); delay(periodMs) } }
}
