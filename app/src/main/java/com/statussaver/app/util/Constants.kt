package com.statussaver.app.util

object Constants {
    // SharedPreferences
    const val PREFS_NAME = "status_saver_prefs"
    const val KEY_SAF_URI = "saf_uri"
    const val KEY_FIRST_LAUNCH = "first_launch"
    const val KEY_BACKUP_INTERVAL = "backup_interval"
    
    // WorkManager
    const val BACKUP_WORK_NAME = "status_backup_work"
    const val BACKUP_WORK_TAG = "status_backup"
    const val DEFAULT_BACKUP_INTERVAL_MINUTES = 15L
    
    // File Management
    const val BACKUP_FOLDER_NAME = "StatusBackup"
    const val RETENTION_DAYS = 7
    
    // WhatsApp Paths (hints for SAF picker)
    val WHATSAPP_STATUS_PATHS = listOf(
        "Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
        "WhatsApp/Media/.Statuses",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
    )
    
    // File Extensions
    val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
    val VIDEO_EXTENSIONS = listOf(".mp4", ".3gp", ".mkv", ".avi", ".webm")
}
