package com.zim.jackettprowler.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Boot Receiver - Restarts background daemon after device reboot
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted - checking if daemon should start")
            
            if (BackgroundServiceDaemon.isEnabled(context)) {
                Log.d(TAG, "Starting background daemon after boot...")
                DaemonController.initialize(context)
            }
        }
    }
}
