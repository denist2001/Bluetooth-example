package com.example.bluetoothexample

import android.bluetooth.BluetoothGattCallback

abstract class TimeoutGattCallback : BluetoothGattCallback() {
    open fun onTimeout() {}
}