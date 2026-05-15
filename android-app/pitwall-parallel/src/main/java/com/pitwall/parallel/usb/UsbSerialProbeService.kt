package com.pitwall.parallel.usb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.pitwall.bridge.ktor.embedded.AimMxPipeline
import com.pitwall.bridge.ktor.embedded.EmbeddedBridgeRegistry
import com.pitwall.bridge.ktor.embedded.EmbeddedNativeCanState
import com.pitwall.bridge.ktor.embedded.PitwallDbcDatabase
import com.pitwall.bridge.ktor.embedded.SlcanParser
import com.pitwall.parallel.MainActivity
import com.pitwall.parallel.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service: USB CDC/serial read loop via [UsbSerialDevice], **slcan** decode,
 * bundled **pitwall.dbc** (AIM) decoding (ADR-015 tall **telemetry_signals** + wide **telemetry**)
 * via [EmbeddedBridgeRegistry] when the embedded Ktor bridge is running.
 *
 * Default baud **115200** (common for slcan adapters); change [DEFAULT_BAUD] for your dongle.
 */
class UsbSerialProbeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val bytesTotal = AtomicLong(0)
    private val slcanLinesParsed = AtomicLong(0)
    /** Sum of wide + tall DuckDB rows appended (see [com.pitwall.bridge.ktor.embedded.EmbeddedBridgeRegistry.appendUsbCanIngest]). */
    private val duckRowsAppended = AtomicLong(0)
    private var lastSampleBytes = 0L
    private var lastSampleTime = System.currentTimeMillis()

    private val lineBuffer = StringBuilder(4096)
    private val aimPipeline = AimMxPipeline()
    private val pendingTall = mutableListOf<Triple<String, Double, Double>>()
    private var dbc: PitwallDbcDatabase? = null
    private val framesSinceFlush = AtomicInteger(0)

    @Volatile
    private var usbSerial: UsbSerialInterface? = null

    private val usbReadCallback = UsbSerialInterface.UsbReadCallback { data ->
        if (data.isEmpty()) return@UsbReadCallback
        bytesTotal.addAndGet(data.size.toLong())
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "RX ${data.size} bytes (total=${bytesTotal.get()})")
        }
        val text = String(data, StandardCharsets.US_ASCII)
        synchronized(lineBuffer) {
            lineBuffer.append(text)
            extractLinesLocked()
        }
    }

    private fun extractLinesLocked() {
        while (true) {
            val idx = lineBuffer.indexOf('\n')
            if (idx < 0) break
            val raw = lineBuffer.substring(0, idx)
            lineBuffer.delete(0, idx + 1)
            val line = raw.trimEnd('\r').trim()
            if (line.isNotEmpty()) processSlcanLine(line)
        }
    }

    private fun processSlcanLine(line: String) {
        val frame = SlcanParser.parseLine(line) ?: return
        slcanLinesParsed.incrementAndGet()
        EmbeddedNativeCanState.recordFrameReceived()
        val wallSec = System.currentTimeMillis() / 1000.0
        if (frame.extended) {
            EmbeddedNativeCanState.recordUnknownId(frame.id)
            return
        }
        val db = dbc ?: return
        val decoded =
            db.decodeStandard(frame.id, frame.data) ?: run {
                EmbeddedNativeCanState.recordUnknownId(frame.id)
                return
            }
        val tall = aimPipeline.consumeDecoded(decoded, wallSec)
        synchronized(pendingTall) {
            pendingTall.addAll(tall)
        }
        framesSinceFlush.incrementAndGet()
    }

    private val usbPermissionReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_PERMISSION) return
                synchronized(this@UsbSerialProbeService) {
                    val device = intent.parcelableExtraCompat<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openSerial(device)
                    } else {
                        Log.w(TAG, "USB permission denied for ${device.deviceName}")
                        updateNotification(
                            getString(R.string.usb_probe_notification_title),
                            getString(R.string.usb_probe_permission_denied),
                        )
                    }
                }
            }
        }

    private val usbAttachReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                    Log.i(TAG, "USB device attached; rescanning")
                    attachBestUsbSerial()
                }
            }
        }

    private val statsTicker =
        object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val total = bytesTotal.get()
                val dtS = (now - lastSampleTime).coerceAtLeast(1L) / 1000.0
                val dBytes = total - lastSampleBytes
                val bps = (dBytes / dtS).toLong()
                lastSampleTime = now
                lastSampleBytes = total
                val subtitle =
                    getString(
                        R.string.usb_probe_notification_stats,
                        total.toString(),
                        bps.toString(),
                        slcanLinesParsed.get().toString(),
                        duckRowsAppended.get().toString(),
                    )
                updateNotification(
                    getString(R.string.usb_probe_notification_title),
                    subtitle,
                )
                handler.postDelayed(this, STATS_INTERVAL_MS)
            }
        }

    private val duckFlushTicker =
        object : Runnable {
            override fun run() {
                if (!EmbeddedBridgeRegistry.isAttached()) {
                    handler.postDelayed(this, DUCK_FLUSH_MS)
                    return
                }
                val pendingFrames = framesSinceFlush.getAndSet(0)
                val tallBatch =
                    synchronized(pendingTall) {
                        if (pendingTall.isEmpty()) {
                            emptyList()
                        } else {
                            ArrayList(pendingTall).also { pendingTall.clear() }
                        }
                    }
                val shouldWide = pendingFrames > 0 && aimPipeline.shouldFlushWide()
                if (shouldWide || tallBatch.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        val wideRow =
                            if (shouldWide) {
                                val wallSec = System.currentTimeMillis() / 1000.0
                                aimPipeline.flushWideRow(wallSec).also { aimPipeline.markWideFlushed() }
                            } else {
                                null
                            }
                        val (w, t) =
                            EmbeddedBridgeRegistry.appendUsbCanIngest(wideRow, tallBatch)
                        val sum = w + t
                        if (sum > 0) {
                            duckRowsAppended.addAndGet(sum.toLong())
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "DuckDB ingest wide=$w tall=$t")
                            }
                        }
                    }
                }
                handler.postDelayed(this, DUCK_FLUSH_MS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        try {
            dbc = PitwallDbcDatabase.parse(assets.open("pitwall.dbc").bufferedReader().readText())
            Log.i(TAG, "Loaded assets pitwall.dbc")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load pitwall.dbc from merged assets", e)
        }
        createChannel()
        ContextCompat.registerReceiver(
            this,
            usbPermissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            usbAttachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, buildNotification(waitingText()))
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot start foreground — runtime permission not yet granted", e)
            stopSelf()
            return START_NOT_STICKY
        }
        attachBestUsbSerial()
        handler.post(statsTicker)
        handler.post(duckFlushTicker)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(statsTicker)
        handler.removeCallbacks(duckFlushTicker)
        try {
            unregisterReceiver(usbPermissionReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(usbAttachReceiver)
        } catch (_: Exception) {
        }
        EmbeddedNativeCanState.setUsbSerialOpen(false, null)
        scope.launch(Dispatchers.IO) {
            try {
                usbSerial?.close()
            } catch (_: Exception) {
            }
        }
        usbSerial = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun waitingText(): String =
        getString(R.string.usb_probe_waiting_device)

    private fun attachBestUsbSerial() {
        val mgr = getSystemService(USB_SERVICE) as UsbManager
        val devices = mgr.deviceList.values.toList()
        if (devices.isEmpty()) {
            Log.i(TAG, "No USB devices — plug in a serial adapter")
            updateNotification(
                getString(R.string.usb_probe_notification_title),
                waitingText(),
            )
            return
        }
        val device = devices.first()
        if (mgr.hasPermission(device)) {
            openSerial(device)
        } else {
            Log.i(TAG, "Requesting USB permission for ${device.deviceName}")
            val pi = permissionPendingIntent()
            mgr.requestPermission(device, pi)
        }
    }

    private fun permissionPendingIntent(): PendingIntent {
        val intent =
            Intent(ACTION_USB_PERMISSION).apply {
                setPackage(packageName)
            }
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
        return PendingIntent.getBroadcast(this, 0, intent, flags)
    }

    private fun openSerial(device: UsbDevice) {
        scope.launch(Dispatchers.IO) {
            synchronized(this@UsbSerialProbeService) {
                EmbeddedNativeCanState.setUsbSerialOpen(false, null)
                try {
                    usbSerial?.close()
                } catch (_: Exception) {
                }
                usbSerial = null

                val mgr = getSystemService(USB_SERVICE) as UsbManager
                val conn =
                    mgr.openDevice(device) ?: run {
                        Log.e(TAG, "UsbManager.openDevice failed")
                        updateNotification(
                            getString(R.string.usb_probe_notification_title),
                            getString(R.string.usb_probe_open_failed),
                        )
                        return@launch
                    }

                val serial =
                    UsbSerialDevice.createUsbSerialDevice(device, conn) ?: run {
                        Log.e(TAG, "UsbSerialDevice.createUsbSerialDevice returned null (unsupported chipset?)")
                        conn.close()
                        updateNotification(
                            getString(R.string.usb_probe_notification_title),
                            getString(R.string.usb_probe_unsupported_adapter),
                        )
                        return@launch
                    }

                if (!serial.syncOpen()) {
                    Log.e(TAG, "serial.syncOpen() failed")
                    serial.close()
                    conn.close()
                    updateNotification(
                        getString(R.string.usb_probe_notification_title),
                        getString(R.string.usb_probe_open_failed),
                    )
                    return@launch
                }

                serial.setBaudRate(DEFAULT_BAUD)
                serial.setDataBits(UsbSerialInterface.DATA_BITS_8)
                serial.setStopBits(UsbSerialInterface.STOP_BITS_1)
                serial.setParity(UsbSerialInterface.PARITY_NONE)
                serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

                usbSerial = serial
                serial.read(usbReadCallback)
                EmbeddedNativeCanState.setUsbSerialOpen(true, device.deviceName)

                Log.i(
                    TAG,
                    "Serial open OK device=${device.deviceName} baud=$DEFAULT_BAUD — bench RX active",
                )
                updateNotification(
                    getString(R.string.usb_probe_notification_title),
                    getString(R.string.usb_probe_streaming, DEFAULT_BAUD),
                )
            }
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.usb_probe_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(subtitle: String): Notification {
        val pending =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_parallel)
            .setContentTitle(getString(R.string.usb_probe_notification_title))
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    private fun updateNotification(title: String, subtitle: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val pending =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val n =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_parallel)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pending)
                .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    private companion object {
        private const val TAG = "UsbSerialProbe"

        /** Same as [UsbManager.ACTION_USB_PERMISSION] — explicit for Kotlin SDK resolution. */
        private const val ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_PERMISSION"

        /** Adjust for your bench dongle (slcan/CANable often 115200 or 500000). */
        const val DEFAULT_BAUD = 115200

        private const val CHANNEL_ID = "usb_serial_probe"
        private const val NOTIFICATION_ID = 7102
        private const val STATS_INTERVAL_MS = 2000L
        private const val DUCK_FLUSH_MS = 100L

        private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(key: String): T? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(key, T::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(key) as? T
            }
    }
}
