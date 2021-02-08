package com.example.bluetoothexample.ui.main

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.bluetoothexample.BLEScanCallbackLollipop
import com.example.bluetoothexample.ScanResultCompat
import com.example.bluetoothexample.ThisDeviseParameters


class MainViewModel : ViewModel() {

    private val _state = MutableLiveData<MainViewModelState>()
    val state: LiveData<MainViewModelState>
        get() = _state
    private val _foundedDevice = MutableLiveData<ScanResultCompat>()
    val foundedDevice: LiveData<ScanResultCompat>
        get() = _foundedDevice

    private var bluetooth: BluetoothAdapter? = null

    fun onAction(action: MainViewModelAction) {
        manage(action)
    }

    private fun manage(action: MainViewModelAction) {
        when (action) {
            is MainViewModelAction.CheckIfBluetoothAdapterExist -> checkIfBluetoothAdapterExist(
                action.context
            )
            MainViewModelAction.CheckIfBluetoothAdapterEnabled -> checkIfBluetoothAdapterEnabled()
            MainViewModelAction.GetBluetoothAdapterParameters -> getBluetoothAdapterParameters()
        }
    }

    private fun checkIfBluetoothAdapterExist(context: Context) {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetooth = bluetoothManager.adapter
        val listConnectedGattDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
        _state.postValue(MainViewModelState.IsBluetoothAdapterExist(bluetooth != null))
    }

    private fun checkIfBluetoothAdapterEnabled() {
        bluetooth?.let { bluetoothAdapter ->
            _state.postValue(MainViewModelState.IsBluetoothAdapterEnabled(bluetoothAdapter.isEnabled))
        }
    }

    private fun getBluetoothAdapterParameters() {
        bluetooth?.let { bluetoothAdapter ->
            val pairedDevices = bluetoothAdapter.bondedDevices
            pairedDevices?.forEach {
                Log.e(MainViewModel::class.java.name, "Found paired device with MAC: " + it.address)
            }
            val settings = ScanSettings.Builder()
                .setReportDelay(0)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                settings.setLegacy(false)
            }
            val scannerCallback = BLEScanCallbackLollipop(bluetoothAdapter.bluetoothLeScanner)
            scannerCallback.channelDevices.observeForever {
                _foundedDevice.postValue(it)
            }
            bluetoothAdapter.bluetoothLeScanner.startScan(
//                emptyList(),
//                settings.build(),
                scannerCallback
            )
            if (!bluetoothAdapter.isDiscovering) {
                Log.e(MainViewModel::class.java.name, "Discovery canceled.")
            }
            bluetoothAdapter.startDiscovery()
            _state.postValue(
                MainViewModelState.BluetoothAdapterParameters(
                    ThisDeviseParameters(
                        bluetoothAdapter.address,
                        bluetoothAdapter.name
                    )
                )
            )
        }
    }
}

sealed class MainViewModelAction {
    class CheckIfBluetoothAdapterExist(val context: Context) : MainViewModelAction()
    object CheckIfBluetoothAdapterEnabled : MainViewModelAction()
    object GetBluetoothAdapterParameters : MainViewModelAction()
}

sealed class MainViewModelState {
    data class IsBluetoothAdapterExist(val isExist: Boolean) : MainViewModelState()
    data class IsBluetoothAdapterEnabled(val isEnabled: Boolean) : MainViewModelState()
    data class BluetoothAdapterParameters(val parameters: ThisDeviseParameters) :
        MainViewModelState()
}