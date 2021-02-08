package com.example.bluetoothexample

import android.bluetooth.BluetoothDevice
import android.graphics.Color.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class ConnectionsAdapter(var connectionsList: List<BluetoothDevice>) :
    RecyclerView.Adapter<ConnectionsAdapter.ConnectionViewHolder>() {
    private var servicesConnectionsCallback: ServicesConnectionsCallback? = null
    var selectedDevice: String? = null

    inner class ConnectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var tvName = itemView.findViewById(R.id.tv_device_name) as TextView
        private var btnConnect = itemView.findViewById(R.id.btn_connect) as Button
        private var progressBar =
            itemView.findViewById(R.id.progress_bar_connections) as ProgressBar
        var layout = itemView.findViewById(R.id.connected_device_item) as LinearLayout

        fun bind(device: BluetoothDevice) {
            progressBar.visibility = View.GONE
            btnConnect.visibility = View.VISIBLE

            var name = device.name
            val address = device.address
            if (name == null || name == "") {
                name = "Not available"
            }

            if (selectedDevice != null) {
                if (address.toLowerCase(Locale.getDefault()) == selectedDevice?.toLowerCase(Locale.getDefault())) {
                    tvName.setTextColor(BLUE)
                } else {
                    tvName.setTextColor(BLACK)
                }
            } else {
                tvName.setTextColor(RED)
            }

            tvName.text = name
            btnConnect.setOnClickListener {
                val bluetoothDeviceInfo = BluetoothDeviceInfo()
                bluetoothDeviceInfo.device = device
                servicesConnectionsCallback?.onDeviceClicked(bluetoothDeviceInfo)
            }

            /*layout.setOnClickListener {
                progressBar.visibility = View.VISIBLE
                btnConnect.visibility = View.GONE
                val bluetoothDeviceInfo = BluetoothDeviceInfo()
                bluetoothDeviceInfo.device = device
                servicesConnectionsCallback?.onDeviceClicked(bluetoothDeviceInfo)
            }*/

        }
    }

    override fun onBindViewHolder(holder: ConnectionViewHolder, position: Int) {
        val device = connectionsList[position]
        holder.bind(device)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectionViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.adapter_connection, parent, false)
        return ConnectionViewHolder(view)
    }

    override fun getItemCount(): Int {
        return connectionsList.size
    }

    fun setServicesConnectionsCallback(servicesConnectionsCallback: ServicesConnectionsCallback?) {
        this.servicesConnectionsCallback = servicesConnectionsCallback
    }

}