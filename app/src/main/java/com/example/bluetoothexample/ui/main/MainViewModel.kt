package com.example.bluetoothexample.ui.main

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.bluetoothexample.ThisDeviseParameters


class MainViewModel : ViewModel() {

    private val _state = MutableLiveData<MainViewModelState>()
    val state: LiveData<MainViewModelState>
        get() = _state

    private var bluetooth: BluetoothAdapter? = null

    fun onAction(action: MainViewModelAction) {
        manage(action)
    }

    private fun manage(action: MainViewModelAction) {
        when (action) {
            MainViewModelAction.CheckIfBluetoothAdapterExist -> checkIfBluetoothAdapterExist()
            MainViewModelAction.CheckIfBluetoothAdapterEnabled -> checkIfBluetoothAdapterEnabled()
            MainViewModelAction.GetBluetoothAdapterParameters -> getBluetoothAdapterParameters()
        }
    }

    private fun checkIfBluetoothAdapterExist() {
        bluetooth = BluetoothAdapter.getDefaultAdapter()
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
            bluetoothAdapter.bluetoothLeScanner.startScan(object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)
                    if (result != null) {
                        val result = result.timestampNanos
                    }
                }
            })
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
    object CheckIfBluetoothAdapterExist : MainViewModelAction()
    object CheckIfBluetoothAdapterEnabled : MainViewModelAction()
    object GetBluetoothAdapterParameters : MainViewModelAction()
}

sealed class MainViewModelState {
    data class IsBluetoothAdapterExist(val isExist: Boolean) : MainViewModelState()
    data class IsBluetoothAdapterEnabled(val isEnabled: Boolean) : MainViewModelState()
    data class BluetoothAdapterParameters(val parameters: ThisDeviseParameters) : MainViewModelState()
}