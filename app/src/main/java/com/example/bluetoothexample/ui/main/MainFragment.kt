package com.example.bluetoothexample.ui.main

import android.app.Activity.RESULT_OK
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothexample.*
import com.example.bluetoothexample.databinding.MainFragmentBinding
import com.example.bluetoothexample.health.BluetoothHDPActivity


class MainFragment : Fragment(R.layout.main_fragment), ServicesConnectionsCallback {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var receiver: BroadcastReceiver

    private lateinit var connectionsAdapter: ConnectionsAdapter
    private val connectionsList: ArrayList<BluetoothDevice> = ArrayList()
    private val REQUEST_ENABLE_BT = 0
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainFragmentBinding
    private lateinit var boundBLEService: BlueToothService

    /*override fun onResume() {
        super.onResume()
    }*/

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MainFragmentBinding.bind(view)
        registerBroadcastReceiver()
        bindBluetoothService()
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        viewModel.state.observe(viewLifecycleOwner) { state ->
            manageState(state)
        }
        viewModel.onAction(MainViewModelAction.CheckIfBluetoothAdapterExist(requireContext()))
        viewModel.foundedDevice.observe(this) { scanResultCompat ->
            scanResultCompat.device?.let { connectionsList.add(it) }
            connectionsAdapter.notifyDataSetChanged()
        }
        initUI()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            binding.isBTEnabledIv.isPressed = true
        }
    }

    private fun initUI() {
        binding.isBTEnabledIv.visibility = INVISIBLE
        binding.isBTEnabledIv.setOnClickListener {
            viewModel.onAction(MainViewModelAction.CheckIfBluetoothAdapterEnabled)
            connectionsList.clear()
            connectionsAdapter.notifyDataSetChanged()
        }
        connectionsAdapter = ConnectionsAdapter(connectionsList)
        connectionsAdapter.setServicesConnectionsCallback(this)
        with(binding.devicesRv) {
            adapter = connectionsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun manageState(state: MainViewModelState) {
        when (state) {
            is MainViewModelState.IsBluetoothAdapterExist -> bluetoothAdapterExist(state.isExist)
            is MainViewModelState.IsBluetoothAdapterEnabled -> bluetoothAdapterEnabled(state.isEnabled)
            is MainViewModelState.BluetoothAdapterParameters -> showBluetoothAdapterParameters(state.parameters)
        }
    }

    private fun bluetoothAdapterExist(exist: Boolean) {
        binding.progressBar.visibility = INVISIBLE
        binding.isBTEnabledIv.visibility = VISIBLE
        binding.isBTEnabledIv.isClickable = exist
    }

    private fun bluetoothAdapterEnabled(enabled: Boolean) {
        if (enabled) {
            binding.isBTEnabledIv.isPressed = true
            viewModel.onAction(MainViewModelAction.GetBluetoothAdapterParameters)
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    private fun showBluetoothAdapterParameters(parameters: ThisDeviseParameters) {
        Toast
            .makeText(
                requireContext(),
                "Address: " + parameters.address + "\n" + "Name: " + parameters.name,
                Toast.LENGTH_LONG
            )
            .show()
    }

    private fun registerBroadcastReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { nonNullIntent ->
                    when (nonNullIntent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device: BluetoothDevice? =
                                nonNullIntent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                            Log.d(
                                this::class.java.name,
                                "Found device " + device?.getName() + "\n" + device?.getAddress()
                            )
                        }
                        BluetoothAdapter.ACTION_STATE_CHANGED -> {
                            val state = nonNullIntent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR
                            )
                            Log.d(this::class.java.name, "BluetoothAdapter state is $state")
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                            Log.d(this::class.java.name, "Discovery started")
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            Log.d(this::class.java.name, "Discovery finished")
                        }
                        else -> {
                            Log.d(this::class.java.name, "Something else happened")
                        }
                    }
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND)
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        requireContext().registerReceiver(receiver, intentFilter)
    }

    override fun onDisconnectClicked(deviceInfo: BluetoothDeviceInfo?) {
    }

    override fun onDeviceClicked(device: BluetoothDeviceInfo?) {
        var connectToDeviceAddress = device?.address
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled) {
            return
        }

        if (device == null) {
            Log.e("deviceInfo", "null")
            return
        }

        val bluetoothDeviceInfo: BluetoothDeviceInfo = device
        if (boundBLEService.isGattConnected(device.address)) {
            connectToDeviceAddress = ""
//            val intent = Intent(requireContext(), BluetoothHDPActivity::class.java)
//            intent.putExtra("DEVICE_SELECTED_ADDRESS", device.address)
//            startActivity(intent)
            return
        }
        boundBLEService.connectGatt(bluetoothDeviceInfo.device, false, object : TimeoutGattCallback() {
            override fun onTimeout() {
                val toast = Toast.makeText(requireContext(), "Connection timeout", Toast.LENGTH_SHORT)
                toast.show()
                connectToDeviceAddress = ""
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                boundBLEService.let { it.gattMap[device.address] = gatt }

                if (newState == BluetoothGatt.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS) {
                    if (status == 133 /*&& retryAttempts < RECONNECTION_RETRIES*/) {
                        Log.d("onConnectionStateChange", "[Browser]: Reconnect due to 0x85 (133) error")
//                        retryAttempts++
//                        handler.postDelayed({
//                            gatt.close()
//                            connectToDevice(device)
//                        }, 1000)
                        return
                    }

                    val deviceName = if (TextUtils.isEmpty(bluetoothDeviceInfo.name)) "Unknown" else bluetoothDeviceInfo.name
                    if (gatt.device.address == connectToDeviceAddress) {
                        connectToDeviceAddress = ""
//                        synchronized(errorMessageQueue) { errorMessageQueue.add(getFailedConnectingToDeviceMessage(deviceName, status)) }
                    } else {
//                        synchronized(errorMessageQueue) { errorMessageQueue.add(getDeviceDisconnectedMessage(deviceName, status)) }
                    }
//                    handler.removeCallbacks(displayQueuedMessages)
//                    handler.postDelayed(displayQueuedMessages, 500)
                } else if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    boundBLEService.let {
                        if (it.isGattConnected) {
                            connectToDeviceAddress = ""
//                            if (btToolbarOpened) {
//                                closeToolbar()
//                                btToolbarOpened = !btToolbarOpened
//                            }
                            val intent = Intent(requireContext(), BluetoothHDPActivity::class.java)
                            intent.putExtra("DEVICE_SELECTED_ADDRESS", device.address)
                            startActivity(intent)
                        }
                    }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d("STATE_DISCONNECTED", "Called")
                    gatt.close()
                    boundBLEService.clearGatt()
                }
//                retryAttempts = 0
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        requireContext().unregisterReceiver(receiver)
    }

    private fun bindBluetoothService() {
        object : BlueToothService.Binding(requireContext()) {
            override fun onBound(service: BlueToothService?) {
                service?.let { boundBLEService = it }
            }
        }.bind()
    }

}