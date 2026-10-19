package net.freifunk.darmstadt.nodewhisperer.services

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.freifunk.darmstadt.nodewhisperer.models.WifiScanResult

class WifiScanService(context: Context, private val scope: CoroutineScope) {
    companion object {
        const val THROTTLED_SCAN_INTERVAL_SEC = 30
    }

    private val context: Context = context
    var receiverRegistered: Boolean = false
    var scanning: Boolean = false
    var wifiScanServiceResultReceiver: WifiScanServiceResultReceiver? = null

    var scanningEnabled: MutableState<Boolean> = mutableStateOf(false)
    var scanningPaused: MutableState<Boolean> = mutableStateOf(false)
    var lastScanTimestamp: MutableState<Long?> = mutableStateOf(null)
    var scanThrottleEnabled: MutableState<Boolean> = mutableStateOf(false)
    private var scanJob: Job? = null
    val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure()
            }
        }
    }

    private fun getManager(): WifiManager {
        return context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private fun scanSuccess() {
        Log.d("WifiScanService", "Scan successful")

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (getManager().scanResults == null) {
            Log.d("WifiScanService", "No scan results")
            return
        }

        var wifiScanResults = mutableListOf<WifiScanResult>()
        for (result in getManager().scanResults) {
            wifiScanResults.add(WifiScanResult(result))
        }

        if (wifiScanServiceResultReceiver == null) {
            return
        }

        if (this.scanningPaused.value || !this.scanningEnabled.value)
            return

        lastScanTimestamp.value = System.currentTimeMillis()
        wifiScanServiceResultReceiver?.onScanResultUpdate(wifiScanResults)
        startScanIteration()
    }

    private fun scanFailure() {
        Log.d("WifiScanService", "Scan failed")
    }

    fun registerReceiver(wifiScanServiceResultReceiver: WifiScanServiceResultReceiver) {
        this.wifiScanServiceResultReceiver = wifiScanServiceResultReceiver
    }

    fun unregisterReceiver(wifiScanServiceResultReceiver: WifiScanServiceResultReceiver) {
        context.unregisterReceiver(wifiScanReceiver)
        this.wifiScanServiceResultReceiver = null
    }

    private fun checkThrottleEnabled() {
        scanThrottleEnabled.value = when {
            /* Android 12+: queryable via isScanThrottleEnabled() */
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> getManager().isScanThrottleEnabled
            /* Android 11: isScanThrottleEnabled() always returns true ignoring developer options */
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> true
            /* Android 10: settable via developer options, but not queryable */
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> true
            /* Android 9: always enabled */
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> true
            /* Before: no throttling */
            else -> false
        }
    }

    private fun startScanIteration() {
        scanJob?.cancel()
        if (scanThrottleEnabled.value) {
            scanJob = scope.launch {
                delay(THROTTLED_SCAN_INTERVAL_SEC * 1000L)
                getManager().startScan()
            }
        } else {
            getManager().startScan()
        }
    }

    fun startScanning() {
        Log.d("WifiScanService", "Start scanning")
        if (!receiverRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            context.registerReceiver(wifiScanReceiver, intentFilter)

            receiverRegistered = true
        }

        scanningEnabled.value = true
        scanningPaused.value = false
        lastScanTimestamp.value = null
        checkThrottleEnabled()
        getManager().startScan()
    }

    fun stopScanning() {
        Log.d("WifiScanService", "Stop scanning")
        scanningEnabled.value = false
        scanningPaused.value = false
        lastScanTimestamp.value = null
        scanJob?.cancel()
        if (receiverRegistered) {
            context.unregisterReceiver(wifiScanReceiver)
            receiverRegistered = false
        }
    }

    fun pauseScanning() {
        Log.d("WifiScanService", "Pause scanning")
        scanningPaused.value = true
        scanJob?.cancel()
    }

    fun resumeScanning() {
        Log.d("WifiScanService", "Resume scanning")
        scanningPaused.value = false
        checkThrottleEnabled()
        startScanIteration()
    }
}
