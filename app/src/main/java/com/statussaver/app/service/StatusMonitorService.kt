package com.statussaver.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.statussaver.app.MainActivity
import com.statussaver.app.R
import com.statussaver.app.data.repository.StatusRepository
import com.statussaver.app.util.Constants
import com.statussaver.app.util.SAFHelper
import kotlinx.coroutines.*

class StatusMonitorService : Service() {
    
    companion object {
        private const val TAG = "StatusMonitorService"
        private const val CHECK_INTERVAL_MS = 30_000L // Check every 30 seconds
        
        fun start(context: Context) {
            val intent = Intent(context, StatusMonitorService::class.java).apply {
                action = Constants.SERVICE_ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, StatusMonitorService::class.java).apply {
                action = Constants.SERVICE_ACTION_STOP
            }
            context.stopService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: StatusRepository
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    
    private val checkRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                checkForNewStatuses()
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        repository = StatusRepository(this)
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.SERVICE_ACTION_START -> {
                startMonitoring()
            }
            Constants.SERVICE_ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
    
    private fun startMonitoring() {
        if (isRunning) return
        
        val notification = createNotification()
        startForeground(Constants.NOTIFICATION_ID, notification)
        
        isRunning = true
        handler.post(checkRunnable)
        
        // Initial check
        checkForNewStatuses()
        
        Log.d(TAG, "Monitoring started")
    }
    
    private fun stopMonitoring() {
        isRunning = false
        handler.removeCallbacks(checkRunnable)
        Log.d(TAG, "Monitoring stopped")
    }
    
    private fun checkForNewStatuses() {
        if (!SAFHelper.hasValidPermission(this)) {
            Log.w(TAG, "No valid SAF permission")
            return
        }
        
        serviceScope.launch {
            try {
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val autoSaveEnabled = prefs.getBoolean(Constants.KEY_AUTO_SAVE_ENABLED, false)
                
                val (cached, skipped) = repository.performFullBackup()
                if (cached > 0) {
                    Log.d(TAG, "Cached $cached new statuses")
                    updateNotification(cached)
                    
                    // If Auto-Save is enabled, also save all newly cached files
                    if (autoSaveEnabled) {
                        Log.d(TAG, "Auto-Save enabled, saving cached files...")
                        // Save newly cached files to Saved folder
                        val cachedStatuses = repository.getCachedStatuses().value ?: emptyList()
                        cachedStatuses.take(cached).forEach { status ->
                            repository.saveCachedStatus(status)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for new statuses: ${e.message}")
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "Status Saver Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background monitoring for WhatsApp statuses"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(cachedCount: Int = 0): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val text = if (cachedCount > 0) {
            "Cached $cachedCount new statuses"
        } else {
            "Monitoring for new statuses"
        }
        
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Status Saver")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_saved)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotification(cachedCount: Int) {
        val notification = createNotification(cachedCount)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(Constants.NOTIFICATION_ID, notification)
    }
}
