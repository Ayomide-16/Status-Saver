package com.statussaver.app.util

object Constants {
    // SharedPreferences
    const val PREFS_NAME = "status_saver_prefs"
    const val KEY_SAF_URI = "saf_uri"
    const val KEY_FIRST_LAUNCH = "first_launch"
    const val KEY_PERMISSION_GRANTED = "permission_granted"
    const val KEY_FOLDER_SELECTED = "folder_selected"
    const val KEY_BACKUP_INTERVAL = "backup_interval"
    const val KEY_AUTO_SAVE_ENABLED = "auto_save_enabled"
    const val KEY_CACHE_DURATION_DAYS = "cache_duration_days"
    
    // Folder Names
    const val CACHE_FOLDER_NAME = "StatusCache"
    const val SAVED_FOLDER_NAME = "StatusSaved"
    
    // WorkManager
    const val BACKUP_WORK_NAME = "status_backup_work"
    const val BACKUP_WORK_TAG = "status_backup"
    const val DEFAULT_BACKUP_INTERVAL_MINUTES = 15L
    
    // File Management
    const val DEFAULT_RETENTION_DAYS = 7
    const val MIN_RETENTION_DAYS = 1
    const val MAX_RETENTION_DAYS = 60
    
    // WhatsApp Paths (hints for SAF picker)
    val WHATSAPP_STATUS_PATHS = listOf(
        "Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
        "WhatsApp/Media/.Statuses",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
    )
    
    // File Extensions
    val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
    val VIDEO_EXTENSIONS = listOf(".mp4", ".3gp", ".mkv", ".avi", ".webm")
    
    // Notification
    const val NOTIFICATION_CHANNEL_ID = "status_saver_service"
    const val NOTIFICATION_ID = 1001
    
    // Service
    const val SERVICE_ACTION_START = "com.statussaver.app.START_SERVICE"
    const val SERVICE_ACTION_STOP = "com.statussaver.app.STOP_SERVICE"
    
    /**
     * Get retention days from preferences or default
     */
    fun getRetentionDays(context: android.content.Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CACHE_DURATION_DAYS, DEFAULT_RETENTION_DAYS)
    }
    
    /**
     * Set retention days
     */
    fun setRetentionDays(context: android.content.Context, days: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CACHE_DURATION_DAYS, days.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)).apply()
    }
}
