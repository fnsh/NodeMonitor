package net.freifunk.darmstadt.nodewhisperer.services

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import net.freifunk.darmstadt.nodewhisperer.models.WifiScanResult

class WifiScanService(context: Context) {
    private val context: Context = context
    var receiverRegistered: Boolean = false
    var scanning: Boolean = false
    var wifiScanServiceResultReceiver: WifiScanServiceResultReceiver? = null

    var scanningEnabled: MutableState<Boolean> = mutableStateOf(false)
    var scanningPaused: MutableState<Boolean> = mutableStateOf(false)
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

    private fun startScanIteration() {
        getManager().startScan()
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
        startScanIteration()
    }

    fun stopScanning() {
        Log.d("WifiScanService", "Stop scanning")
        scanningEnabled.value = false
        scanningPaused.value = false
        if (receiverRegistered) {
            context.unregisterReceiver(wifiScanReceiver)
            receiverRegistered = false
        }
    }

    fun pauseScanning() {
        Log.d("WifiScanService", "Pause scanning")
        scanningPaused.value = true
    }

    fun resumeScanning() {
        Log.d("WifiScanService", "Resume scanning")
        scanningPaused.value = false
        startScanIteration()
    }
}