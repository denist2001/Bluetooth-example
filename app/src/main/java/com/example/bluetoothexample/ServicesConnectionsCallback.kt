package com.example.bluetoothexample

interface ServicesConnectionsCallback {
    fun onDisconnectClicked(deviceInfo: BluetoothDeviceInfo?)
    fun onDeviceClicked(device: BluetoothDeviceInfo?)
}