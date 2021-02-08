package com.example.bluetoothexample

import android.annotation.TargetApi
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class BLEScanCallbackLollipop(private val scanner: BluetoothLeScanner) : ScanCallback() {
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Job() + Dispatchers.Main)
    private val scanResults = mutableMapOf<String, ScanResultCompat>()
    val channelDevices = MutableLiveData<ScanResultCompat>()

    override fun onScanResult(callbackType: Int, result: ScanResult) {

        Log.d("onScanResult", "Discovered bluetoothLE +$result")
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !result.isConnectable) {
                return
            }*/
        ScanResultCompat.from(result).let { scanResultCompat ->
            if (scanResultCompat.device == null || scanResultCompat.device?.address == null) {
                return@let
            }
            if (scanResults.containsKey(scanResultCompat.device?.address)) {
                scanner.stopScan(this)
                return@let
            }
            scanResults[scanResultCompat.device!!.address] = scanResultCompat
            coroutineScope.launch {
                channelDevices.postValue(scanResultCompat)
            }
        }
//        if (service.addDiscoveredDevice(from(result)!!)) {
//            service.bluetoothAdapter.bluetoothLeScanner.stopScan(this)
//        }
    }

    override fun onBatchScanResults(results: List<ScanResult>) {
        for (result in results) {
            val device = result.device
            Log.d(
                "onBatchScanResults",
                "Discovered bluetoothLE +" + device.address + " with name " + device.name
            )
//            if (service.addDiscoveredDevice(from(result)!!)) {
//                service.bluetoothAdapter.bluetoothLeScanner.stopScan(this)
//                break
//            }
        }
    }

    override fun onScanFailed(errorCode: Int) {
        if (errorCode != SCAN_FAILED_ALREADY_STARTED) {
//            handler.post { service.onDiscoveryCanceled() }
        }
    }

}