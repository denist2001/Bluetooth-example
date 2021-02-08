package com.example.bluetoothexample

import android.bluetooth.BluetoothGattCharacteristic

/**
 * The plain byte array of the characteristic.
 */
class ByteArrayValue {
    class Factory : ValueFactory<ByteArray> {
        override fun create(value: BluetoothGattCharacteristic): ByteArray {
            return value.value
        }
    }
}