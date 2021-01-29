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

}