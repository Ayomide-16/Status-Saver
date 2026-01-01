package com.statussaver.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.statussaver.app.service.StatusMonitorService
import com.statussaver.app.util.SAFHelper

/**
 * Receiver to restart the monitoring service after device boot
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Boot completed, checking if service should start")
            
            // Only start service if we have valid SAF permission
            if (SAFHelper.hasValidPermission(context)) {
                Log.d(TAG, "Starting monitoring service after boot")
                StatusMonitorService.start(context)
            } else {
                Log.d(TAG, "No valid SAF permission, service not started")
            }
        }
    }
}
