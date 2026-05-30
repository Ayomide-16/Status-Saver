package com.statussaver.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FileType {
    IMAGE,
    VIDEO
}

enum class StatusSource {
    LIVE,      // From WhatsApp status folder
    SAVED,     // User saved permanently
    CACHED     // Auto-cached by background service
}

/**
 * Entity for cached/saved status files
 */
@Entity(
    tableName = "statuses",
    indices = [
        androidx.room.Index(value = ["source", "savedAt"]),
        androidx.room.Index(value = ["source", "fileType", "savedAt"]),
        androidx.room.Index(value = ["filename", "source"], unique = true)
    ]
)
data class StatusEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filename: String,
    val originalUri: String,
    val localPath: String,
    val fileType: FileType,
    val source: StatusSource,
    val createdAt: Long,
    val savedAt: Long = System.currentTimeMillis(),
    val fileSize: Long
)

/**
 * Entity for tracking downloaded status files
 * This persists which files have been saved by the user
 */
@Entity(
    tableName = "downloaded_status",
    primaryKeys = ["filename", "source"]
)
data class DownloadedStatus(
    val filename: String,
    val source: StatusSource,
    val originalPath: String,
    val savedPath: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
