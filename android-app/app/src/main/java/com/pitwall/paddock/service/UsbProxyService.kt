package com.pitwall.paddock.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class UsbProxyService : Service() {
    companion object {
        private const val TAG = "UsbProxyService"
        private const val ACTION_USB_PERMISSION = "com.pitwall.paddock.USB_PERMISSION"
        private const val TCP_PORT = 9000
        private const val BAUD_RATE = 500000 // For BMW E46 M3
        private const val NOTIFICATION_CHANNEL_ID = "usb_proxy_channel"
        private const val NOTIFICATION_ID = 1

        @Volatile var isRunning = false
    }

    private lateinit var usbManager: UsbManager
    private var usbPort: UsbSerialPort? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // Register receiver for USB permission
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "USB Proxy Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the USB-to-TCP proxy alive in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pitwall USB Proxy")
            .setContentText("Bridging USB CAN adapter to localhost:9000")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // simple default icon
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else 0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isRunning) {
            Log.i(TAG, "Starting UsbProxyService...")
            isRunning = true
            startTcpServer()
            findAndConnectUsb()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Stopping UsbProxyService...")
        isRunning = false
        try { unregisterReceiver(usbReceiver) } catch (e: Exception) {}
        closeAll()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTcpServer() {
        thread(name = "UsbProxy_TcpServer") {
            try {
                serverSocket = ServerSocket(TCP_PORT)
                Log.i(TAG, "TCP Server listening on port $TCP_PORT")
                while (isRunning) {
                    clientSocket = serverSocket?.accept()
                    Log.i(TAG, "Client connected to TCP Proxy!")
                    
                    // If USB is already connected, start bridging
                    usbPort?.let { startBridging(it, clientSocket!!) }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "TCP Server Error", e)
            }
        }
    }

    private fun findAndConnectUsb() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            Log.w(TAG, "No USB serial devices found.")
            return
        }

        // Just take the first available driver
        val driver = availableDrivers[0]
        val device = driver.device

        if (usbManager.hasPermission(device)) {
            connectUsbDevice(driver.ports[0])
        } else {
            Log.i(TAG, "Requesting USB permission...")
            val permissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.i(TAG, "USB Permission granted.")
                            val driver = UsbSerialProber.getDefaultProber().probeDevice(it)
                            if (driver != null && driver.ports.isNotEmpty()) {
                                connectUsbDevice(driver.ports[0])
                            }
                        }
                    } else {
                        Log.e(TAG, "USB Permission denied.")
                    }
                }
            }
        }
    }

    private fun connectUsbDevice(port: UsbSerialPort) {
        try {
            val connection = usbManager.openDevice(port.driver.device)
            if (connection == null) {
                Log.e(TAG, "Failed to open USB device.")
                return
            }
            port.open(connection)
            port.setParameters(
                BAUD_RATE,
                8, // data bits
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            usbPort = port
            Log.i(TAG, "USB Device connected at $BAUD_RATE baud.")

            // If a client is already connected, start bridging
            clientSocket?.let { startBridging(port, it) }

        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to USB device", e)
            closeAll()
        }
    }

    private fun startBridging(port: UsbSerialPort, socket: Socket) {
        Log.i(TAG, "Starting bi-directional bridge between USB and TCP.")

        // Thread 1: USB -> TCP
        thread(name = "UsbProxy_UsbToTcp") {
            val buffer = ByteArray(4096)
            try {
                val outputStream = socket.getOutputStream()
                while (isRunning && socket.isConnected && port.isOpen) {
                    val len = port.read(buffer, 100)
                    if (len > 0) {
                        outputStream.write(buffer, 0, len)
                        outputStream.flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "USB read error", e)
                closeAll()
            }
        }

        // Thread 2: TCP -> USB
        thread(name = "UsbProxy_TcpToUsb") {
            val buffer = ByteArray(4096)
            try {
                val inputStream = socket.getInputStream()
                while (isRunning && socket.isConnected && port.isOpen) {
                    val len = inputStream.read(buffer)
                    if (len > 0) {
                        port.write(buffer.copyOfRange(0, len), 100)
                    } else if (len == -1) {
                        break // End of stream
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP read error", e)
                closeAll()
            }
        }
    }

    private fun closeAll() {
        try { usbPort?.close() } catch (e: IOException) {}
        try { clientSocket?.close() } catch (e: IOException) {}
        try { serverSocket?.close() } catch (e: IOException) {}
        usbPort = null
        clientSocket = null
    }
}
