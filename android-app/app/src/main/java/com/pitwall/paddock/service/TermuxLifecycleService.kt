package com.pitwall.paddock.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class TermuxLifecycleService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("TermuxLifecycle", "TermuxLifecycleService started. Listening for app shutdown.")
        // Sticky means if Android kills the service, it'll restart it, 
        // but we only really care while the app is actively in Recents.
        return START_NOT_STICKY 
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i("TermuxLifecycle", "App swiped from recents. Sending shutdown intent to Termux.")
        
        // App is being closed by the user, send the kill command to Termux
        TermuxLauncher.shutdownServer(this)

        // Stop the USB Proxy Service if it's running
        stopService(Intent(this, UsbProxyService::class.java))

        // Stop the service itself since the app is dying
        stopSelf()
    }
}
