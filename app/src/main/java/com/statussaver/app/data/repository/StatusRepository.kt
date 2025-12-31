package com.statussaver.app.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import com.statussaver.app.data.database.AppDatabase
import com.statussaver.app.data.database.BackedUpStatus
import com.statussaver.app.data.database.FileType
import com.statussaver.app.util.Constants
import com.statussaver.app.util.SAFHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StatusRepository(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val statusDao = database.statusDao()
    
    companion object {
        private const val TAG = "StatusRepository"
    }
    
    /**
     * Get all backed up statuses as LiveData
     */
    fun getAllBackups(): LiveData<List<BackedUpStatus>> = statusDao.getAllBackups()
    
    /**
     * Get the backup directory
     */
    fun getBackupDirectory(): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            Constants.BACKUP_FOLDER_NAME
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Check if a file has already been backed up
     */
    suspend fun isAlreadyBackedUp(filename: String): Boolean {
        return statusDao.existsByFilename(filename)
    }
    
    /**
     * Backup a status file from SAF to local storage
     */
    suspend fun backupFile(documentFile: DocumentFile): BackedUpStatus? = withContext(Dispatchers.IO) {
        try {
            val filename = documentFile.name ?: return@withContext null
            
            // Check if already backed up
            if (isAlreadyBackedUp(filename)) {
                Log.d(TAG, "File already backed up: $filename")
                return@withContext null
            }
            
            val backupDir = getBackupDirectory()
            val destFile = File(backupDir, filename)
            
            // Copy file content
            context.contentResolver.openInputStream(documentFile.uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (!destFile.exists()) {
                Log.e(TAG, "Failed to copy file: $filename")
                return@withContext null
            }
            
            // Determine file type
            val fileType = if (SAFHelper.isVideoFile(filename)) FileType.VIDEO else FileType.IMAGE
            
            // Create database entry
            val status = BackedUpStatus(
                filename = filename,
                originalUri = documentFile.uri.toString(),
                backupPath = destFile.absolutePath,
                fileType = fileType,
                backupTimestamp = System.currentTimeMillis(),
                fileSize = destFile.length()
            )
            
            val id = statusDao.insert(status)
            Log.d(TAG, "Backed up file: $filename (id: $id)")
            
            return@withContext status.copy(id = id)
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up file: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Delete a backed up status
     */
    suspend fun deleteBackup(status: BackedUpStatus): Boolean = withContext(Dispatchers.IO) {
        try {
            // Delete the file
            val file = File(status.backupPath)
            if (file.exists()) {
                file.delete()
            }
            
            // Delete from database
            statusDao.delete(status)
            Log.d(TAG, "Deleted backup: ${status.filename}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting backup: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Clean up old backups (older than retention period)
     */
    suspend fun cleanupOldBackups(): Int = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (Constants.RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            val oldBackups = statusDao.getBackupsOlderThan(cutoffTime)
            
            var deletedCount = 0
            oldBackups.forEach { status ->
                if (deleteBackup(status)) {
                    deletedCount++
                }
            }
            
            Log.d(TAG, "Cleaned up $deletedCount old backups")
            return@withContext deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old backups: ${e.message}")
            return@withContext 0
        }
    }
    
    /**
     * Get count of backed up files
     */
    suspend fun getBackupCount(): Int = statusDao.getCount()
    
    /**
     * Perform a full backup of all new status files
     */
    suspend fun performFullBackup(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var backedUp = 0
        var skipped = 0
        
        try {
            val statusFiles = SAFHelper.listStatusFiles(context)
            Log.d(TAG, "Found ${statusFiles.size} status files")
            
            statusFiles.forEach { documentFile ->
                val result = backupFile(documentFile)
                if (result != null) {
                    backedUp++
                } else {
                    skipped++
                }
            }
            
            // Clean up old backups
            cleanupOldBackups()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in full backup: ${e.message}")
        }
        
        return@withContext Pair(backedUp, skipped)
    }
}
