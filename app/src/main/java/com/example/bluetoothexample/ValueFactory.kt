package com.example.bluetoothexample

import android.bluetooth.BluetoothGattCharacteristic

interface ValueFactory<T> {
    fun create(value: BluetoothGattCharacteristic): T
}