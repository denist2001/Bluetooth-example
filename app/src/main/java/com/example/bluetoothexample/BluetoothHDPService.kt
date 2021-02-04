package com.example.bluetoothexample

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothProfile.ServiceListener
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

val TAG = "BluetoothHDPService"

val RESULT_OK = 0
val RESULT_FAIL = -1

// Status codes sent back to the UI client.
// Application registration complete.
val STATUS_HEALTH_APP_REG = 100

// Application unregistration complete.
val STATUS_HEALTH_APP_UNREG = 101

// Channel creation complete.
val STATUS_CREATE_CHANNEL = 102

// Channel destroy complete.
val STATUS_DESTROY_CHANNEL = 103

// Reading data from Bluetooth HDP device.
val STATUS_READ_DATA = 104

// Done with reading data.
val STATUS_READ_DATA_DONE = 105

// Message codes received from the UI client.
// Register client with this service.
val MSG_REG_CLIENT = 200

// Unregister client from this service.
val MSG_UNREG_CLIENT = 201

// Register health application.
val MSG_REG_HEALTH_APP = 300

// Unregister health application.
val MSG_UNREG_HEALTH_APP = 301

// Connect channel.
val MSG_CONNECT_CHANNEL = 400

// Disconnect channel.
val MSG_DISCONNECT_CHANNEL = 401

val RECEIVED_SYS = 901

val RECEIVED_DIA = 902

val RECEIVED_PUL = 903

var mClient: Messenger? = null

private var mBluetoothHealth: BluetoothHealth? = null

private var count = 0
private val invoke = byteArrayOf(0x00, 0x00)

private var mHealthAppConfig: BluetoothHealthAppConfiguration? = null
private var mBluetoothAdapter: BluetoothAdapter? = null
private var mDevice: BluetoothDevice? = null
private var mChannelId = 0

class BluetoothHDPService : Service() {

    private val mMessenger: Messenger = Messenger(IncomingHandler())

    /**
     * Make sure Bluetooth and health profile are available on the Android device.  Stop service
     * if they are not available.
     */
    @SuppressLint("ShowToast")
    override fun onCreate() {
        super.onCreate()
        Log.e("TEST", "HDPService Created")
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null || !mBluetoothAdapter!!.isEnabled) {
            // Bluetooth adapter isn't available.  The client of the service is supposed to
            // verify that it is available and activate before invoking this service.
            stopSelf()
            return
        }
        if (!mBluetoothAdapter!!.getProfileProxy(
                this, mBluetoothServiceListener,
                BluetoothProfile.HEALTH
            )
        ) {
            Toast.makeText(
                this, "bluetooth_health_profile_not_available",
                Toast.LENGTH_LONG
            )
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BluetoothHDPService is running.")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mMessenger.binder
    }

    // Callbacks to handle connection set up and disconnection clean up.
    private val mBluetoothServiceListener: ServiceListener = object : ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEALTH) {
                mBluetoothHealth = proxy as BluetoothHealth
                if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(
                    TAG,
                    "onServiceConnected to profile: $profile"
                )
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEALTH) {
                mBluetoothHealth = null
            }
        }
    }

    fun byteToUnsignedInt(b: Byte): Int {
        return 0x00 shl 24 or b.toInt() and 0xff
    }

    fun toInt(bytes: ByteArray): Int {
        var ret = 0
        var i = 0
        while (i < 4 && i < bytes.size) {
            ret = ret shl 8
            ret = ret or (bytes[i].toInt() and 0xFF)
            i++
        }
        return ret
    }
}

// Handles events sent by {@link HealthHDPActivity}.
@SuppressLint("HandlerLeak")
private class IncomingHandler : Handler() {
    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MSG_REG_CLIENT -> {
                Log.d(TAG, "Activity client registered")
                mClient = msg.replyTo
            }
            MSG_UNREG_CLIENT -> mClient = null
            MSG_REG_HEALTH_APP -> registerApp(msg.arg1)
            MSG_UNREG_HEALTH_APP -> unregisterApp()
            MSG_CONNECT_CHANNEL -> {
                mDevice = msg.obj as BluetoothDevice
                connectChannel()
            }
            MSG_DISCONNECT_CHANNEL -> {
                mDevice = msg.obj as BluetoothDevice
                disconnectChannel()
            }
            else -> super.handleMessage(msg)
        }
    }

    // Register health application through the Bluetooth Health API.
    fun registerApp(dataType: Int) {
        Log.e(TAG, "registerApp()")
        mBluetoothHealth!!.registerSinkAppConfiguration(TAG, dataType, mHealthCallback)
    }

    // Unregister health application through the Bluetooth Health API.
    private fun unregisterApp() {
        Log.e(TAG, "unregisterApp()")
        mBluetoothHealth!!.unregisterAppConfiguration(mHealthAppConfig)
    }

    private val mHealthCallback: BluetoothHealthCallback = object : BluetoothHealthCallback() {
        // Callback to handle application registration and unregistration events.  The service
        // passes the status back to the UI client.
        override fun onHealthAppConfigurationStatusChange(
            config: BluetoothHealthAppConfiguration,
            status: Int
        ) {
            if (status == BluetoothHealth.APP_CONFIG_REGISTRATION_FAILURE) {
                mHealthAppConfig = null
                sendMessage(STATUS_HEALTH_APP_REG, RESULT_FAIL)
            } else if (status == BluetoothHealth.APP_CONFIG_REGISTRATION_SUCCESS) {
                mHealthAppConfig = config
                sendMessage(STATUS_HEALTH_APP_REG, RESULT_OK)
            } else if (status == BluetoothHealth.APP_CONFIG_UNREGISTRATION_FAILURE ||
                status == BluetoothHealth.APP_CONFIG_UNREGISTRATION_SUCCESS
            ) {
                sendMessage(
                    STATUS_HEALTH_APP_UNREG,
                    if (status == BluetoothHealth.APP_CONFIG_UNREGISTRATION_SUCCESS) RESULT_OK else RESULT_FAIL
                )
            }
        }

        // Callback to handle channel connection state changes.
        // Note that the logic of the state machine may need to be modified based on the HDP device.
        // When the HDP device is connected, the received file descriptor is passed to the
        // ReadThread to read the content.
        override fun onHealthChannelStateChange(
            config: BluetoothHealthAppConfiguration,
            device: BluetoothDevice, prevState: Int, newState: Int, fd: ParcelFileDescriptor,
            channelId: Int
        ) {
            Log.e("Read Thread", "Start If Statements")
            Log.e("Read Thread", "prevState: $prevState")
            Log.e("Read Thread", "newState: $newState")
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(
                TAG, String.format(
                    "prevState\t%d ----------> newState\t%d",
                    prevState, newState
                )
            )

//            if (prevState != BluetoothHealth.STATE_CHANNEL_CONNECTED &&
//                    newState == BluetoothHealth.STATE_CHANNEL_CONNECTED) {
            if (prevState == BluetoothHealth.STATE_CHANNEL_DISCONNECTED &&
                newState == BluetoothHealth.STATE_CHANNEL_CONNECTED
            ) {
                if (config == mHealthAppConfig) {
                    mChannelId = channelId
                    sendMessage(STATUS_CREATE_CHANNEL, RESULT_OK)
                    Log.e("Read Thread", "Read  Start 1")
                    ReadThread(fd).start()
                } else {
                    Log.e("Read Thread", "Status Create Channel Fail 1")
                    sendMessage(STATUS_CREATE_CHANNEL, RESULT_FAIL)
                }
            } else if (prevState == BluetoothHealth.STATE_CHANNEL_CONNECTING &&
                newState == BluetoothHealth.STATE_CHANNEL_DISCONNECTED
            ) {
                sendMessage(STATUS_CREATE_CHANNEL, RESULT_FAIL)
                Log.e("Read Thread", "Status Create Channel Fail 2")
            } else if (newState == BluetoothHealth.STATE_CHANNEL_DISCONNECTED) {
                if (config == mHealthAppConfig) {
                    sendMessage(STATUS_DESTROY_CHANNEL, RESULT_OK)
                    Log.e("Read Thread", "Status Disconnect OK")
                } else {
                    sendMessage(STATUS_DESTROY_CHANNEL, RESULT_FAIL)
                    Log.e("Read Thread", "Status Disconnect FAIL")
                }
            } else if (prevState == BluetoothHealth.STATE_CHANNEL_CONNECTING && newState == BluetoothHealth.STATE_CHANNEL_CONNECTED) {
                if (config == mHealthAppConfig) {
                    mChannelId = channelId
                    sendMessage(STATUS_CREATE_CHANNEL, RESULT_OK)
                    Log.e("Read Thread", "Read  Start 2")
                    ReadThread(fd).start()
                } else {
                    sendMessage(STATUS_CREATE_CHANNEL, RESULT_FAIL)
                    Log.e("Read Thread", "Status Create Channel Fail 3")
                }
            }
        }

        // Sends an update message to registered UI client.
        private fun sendMessage(what: Int, value: Int) {
            if (mClient == null) {
                Log.d(TAG, "No clients registered.")
                return
            }
            try {
                mClient?.send(Message.obtain(null, what, value, 0))
            } catch (e: RemoteException) {
                // Unable to reach client.
                e.printStackTrace()
            }
        }
    }

    // Connect channel through the Bluetooth Health API.
    private fun connectChannel() {
        Log.i(TAG, "connectChannel()")
        mBluetoothHealth!!.connectChannelToSource(mDevice, mHealthAppConfig)
    }

    // Disconnect channel through the Bluetooth Health API.
    private fun disconnectChannel() {
        Log.i(TAG, "disconnectChannel()")
        mBluetoothHealth!!.disconnectChannel(mDevice, mHealthAppConfig, mChannelId)
    }
}

private class WriteThread(private val mFd: ParcelFileDescriptor?) : Thread() {
    override fun run() {
        val fos = FileOutputStream(mFd!!.fileDescriptor)
        val data_AR = byteArrayOf(
            0xE3.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x2C.toByte(),
            0x00.toByte(), 0x00.toByte(),
            0x50.toByte(), 0x79.toByte(),
            0x00.toByte(), 0x26.toByte(),
            0x80.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(), 0x00.toByte(),
            0x80.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x08.toByte(),  //bt add for phone, can be automate in the future
            0x3C.toByte(), 0x5A.toByte(), 0x37.toByte(), 0xFF.toByte(),
            0xFE.toByte(), 0x95.toByte(), 0xEE.toByte(), 0xE3.toByte(),
            0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
        val data_DR = byteArrayOf(
            0xE7.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x12.toByte(),
            0x00.toByte(), 0x10.toByte(),
            0x00.toByte(), 0x24.toByte(),
            0x02.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x0A.toByte(),
            0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x0D.toByte(), 0x1D.toByte(),
            0x00.toByte(), 0x00.toByte()
        )
        val get_MDS = byteArrayOf(
            0xE7.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x0E.toByte(),
            0x00.toByte(), 0x0C.toByte(),
            0x00.toByte(), 0x24.toByte(),
            0x01.toByte(), 0x03.toByte(),
            0x00.toByte(), 0x06.toByte(),
            0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte()
        )
        val data_RR = byteArrayOf(
            0xE5.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x02.toByte(),
            0x00.toByte(), 0x00.toByte()
        )

//            final byte data_RRQ[] = new byte[] {        (byte) 0xE4, (byte) 0x00,
//                                                        (byte) 0x00, (byte) 0x02,
//                                                        (byte) 0x00, (byte) 0x00 };
//
//            final byte data_ABORT[] = new byte[] {      (byte) 0xE6, (byte) 0x00,
//                                                        (byte) 0x00, (byte) 0x02,
//                                                        (byte) 0x00, (byte) 0x00 };
        try {
            Log.i(TAG, count.toString())
            if (count == 1) {
                fos.write(data_AR)
                Log.i(TAG, "Association Responded!")
            } else if (count == 2) {
                fos.write(get_MDS)
                Log.i(TAG, "Get MDS object attributes!")
            } else if (count == 3) {
                fos.write(data_DR)
                Log.i(TAG, "Data Responsed!")
            } else if (count == 4) {
                fos.write(data_RR)
                Log.i(TAG, "Data Released!")
            }
        } catch (ioe: IOException) {
        }
    }
}

// Thread to read incoming data received from the HDP device.  This sample application merely
// reads the raw byte from the incoming file descriptor.  The data should be interpreted using
// a health manager which implements the IEEE 11073-xxxxx specifications.
private class ReadThread(private val mFd: ParcelFileDescriptor) : Thread() {
    override fun run() {
        Log.e("TEST", "Read Data 1")
        val fis = FileInputStream(mFd.fileDescriptor)
        val data = ByteArray(200)
        Log.i(TAG, "Read Data 2")
        try {
            while (fis.read(data) > -1) {
                // At this point, the application can pass the raw data to a parser that
                // has implemented the IEEE 11073-xxxxx specifications.  Instead, this sample
                // simply indicates that some data has been received.
                Log.i(TAG, "INBOUND")
                val test: String = byte2hex(data) ?: ""
                Log.i(TAG, test)
                if (data[0] != 0x00.toByte()) {
                    if (data[0] == 0xE2.toByte()) {
                        Log.i(TAG, "E2 - Association Request")
                        count = 1
                        WriteThread(mFd).start()
                        try {
                            sleep(100)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        count = 2
                        WriteThread(mFd).start()
                    } else if (data[0] == 0xE7.toByte()) {
                        Log.i(TAG, "E7 - Data Given")
                        if (data[3] != 0xda.toByte()) {
                            invoke[0] = data[6]
                            invoke[1] = data[7]
                            Log.i(TAG, "E7 - Reading?")
                            val sys: ByteBuffer = ByteBuffer.allocate(2)
                            sys.order(ByteOrder.LITTLE_ENDIAN)
                            sys.put(data[45])
                            sys.put(data[46])
                            val sysVal: Short = sys.getShort(0)
                            Log.i(TAG, " Sys - $sysVal")
                            val dia: ByteBuffer = ByteBuffer.allocate(2)
                            dia.order(ByteOrder.LITTLE_ENDIAN)
                            dia.put(data[47])
                            dia.put(data[48])
                            val diaVal: Short = dia.getShort(0)
                            Log.i(TAG, " Dia - $diaVal")
                            sendMessage(9919, diaVal.toInt())
                            sendMessage(9920, sysVal.toInt())
                            for (i in 0 until data.size - 2) {
                                val bb: ByteBuffer = ByteBuffer.allocate(2)
                                bb.order(ByteOrder.LITTLE_ENDIAN)
                                bb.put(data[i])
                                bb.put(data[i + 1])
                                val shortVal: Short = bb.getShort(0)
                                Log.i(TAG, "$i Short Val - $shortVal")
                            }
                            count = 3
                            //set invoke id so get correct response
                            WriteThread(mFd).start()
                        }
                        //parse data!!
                    } else if (data[0] == 0xE4.toByte()) {
                        //count = 4;
                        // (new WriteThread(mFd)).start();
                    }
                    //zero out the data
                    for (i in data.indices) {
                        data[i] = 0x00.toByte()
                    }
                }
                sendMessage(STATUS_READ_DATA, 0)
            }
        } catch (ioe: IOException) {
        }
        if (mFd != null) {
            try {
                mFd.close()
            } catch (e: IOException) { /* Do nothing. */
            }
        }
        sendMessage(STATUS_READ_DATA_DONE, 0)
    }

    // Sends an update message to registered UI client.
    private fun sendMessage(what: Int, value: Int) {
        if (mClient == null) {
            Log.d(TAG, "No clients registered.")
            return
        }
        try {
            mClient?.send(Message.obtain(null, what, value, 0))
        } catch (e: RemoteException) {
            // Unable to reach client.
            e.printStackTrace()
        }
    }

    fun byte2hex(b: ByteArray): String? {
        // String Buffer can be used instead
        var hs = ""
        var stmp = ""
        for (n in b.indices) {
            stmp = Integer.toHexString((b[n] and 0xFF.toByte()).toInt())
            hs = if (stmp.length == 1) {
                hs + "0" + stmp
            } else {
                hs + stmp
            }
            if (n < b.size - 1) {
                hs = hs + ""
            }
        }
        return hs
    }
}