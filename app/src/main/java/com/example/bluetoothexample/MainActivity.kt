package com.example.bluetoothexample

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.example.bluetoothexample.databinding.MainActivityBinding
import com.example.bluetoothexample.ui.main.MainFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainActivityBinding
    private lateinit var receiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, MainFragment.newInstance())
                    .commitNow()
        }
        registerBroadcastReceiver()
    }

    private fun registerBroadcastReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { nonNullIntent ->
                    val action = nonNullIntent.getAction()
                    // Когда найдено новое устройство
                    if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                        // Получаем объект BluetoothDevice из интента
                        val device: BluetoothDevice? = nonNullIntent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                        Log.d(this::class.java.name, device?.getName()+"\n"+ device?.getAddress())
                        //Добавляем имя и адрес в array adapter, чтобы показвать в ListView
//                    mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                    }
                }
            }
        }
        val intentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, intentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}