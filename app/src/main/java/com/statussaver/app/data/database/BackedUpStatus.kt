package com.statussaver.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FileType {
    IMAGE,
    VIDEO
}

@Entity(tableName = "backed_up_statuses")
data class BackedUpStatus(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filename: String,
    val originalUri: String,
    val backupPath: String,
    val fileType: FileType,
    val backupTimestamp: Long,
    val fileSize: Long
)
