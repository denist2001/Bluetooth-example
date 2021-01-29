package com.example.bluetoothexample.ui.main

import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.bluetoothexample.R
import com.example.bluetoothexample.ThisDeviseParameters
import com.example.bluetoothexample.databinding.MainFragmentBinding


class MainFragment : Fragment(R.layout.main_fragment) {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val REQUEST_ENABLE_BT = 0
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainFragmentBinding

    override fun onResume() {
        super.onResume()
        registerBroadcastReceiver()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MainFragmentBinding.bind(view)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        viewModel.state.observe(viewLifecycleOwner) { state ->
            manageState(state)
        }
        viewModel.onAction(MainViewModelAction.CheckIfBluetoothAdapterExist)
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
        val receiver = object : BroadcastReceiver() {
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

}