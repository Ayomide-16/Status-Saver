package com.statussaver.app.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import com.statussaver.app.data.database.*
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
    
    // ========== Directory Management ==========
    
    fun getCacheDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), Constants.CACHE_FOLDER_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    fun getSavedDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), Constants.SAVED_FOLDER_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    // ========== Live Status (from WhatsApp folder) ==========
    
    data class StatusFile(
        val filename: String,
        val uri: String,
        val path: String,
        val fileType: FileType,
        val size: Long,
        val lastModified: Long,
        val isDownloaded: Boolean = false
    )
    
    suspend fun getLiveStatuses(fileType: FileType? = null): List<StatusFile> = withContext(Dispatchers.IO) {
        try {
            val statusFiles = SAFHelper.listStatusFiles(context)
            val downloadedFilenames = statusDao.getAllDownloadedFilenames().toSet()
            
            statusFiles
                .filter { file ->
                    val name = file.name ?: return@filter false
                    val type = if (SAFHelper.isVideoFile(name)) FileType.VIDEO else FileType.IMAGE
                    fileType == null || type == fileType
                }
                .map { file ->
                    val name = file.name ?: ""
                    StatusFile(
                        filename = name,
                        uri = file.uri.toString(),
                        path = file.uri.toString(),
                        fileType = if (SAFHelper.isVideoFile(name)) FileType.VIDEO else FileType.IMAGE,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        isDownloaded = downloadedFilenames.contains(name)
                    )
                }
                .sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting live statuses: ${e.message}")
            emptyList()
        }
    }
    
    // ========== Cached Status ==========
    
    fun getCachedStatuses(): LiveData<List<StatusEntity>> {
        return statusDao.getStatusesBySource(StatusSource.CACHED)
    }
    
    fun getCachedStatusesByType(fileType: FileType): LiveData<List<StatusEntity>> {
        return statusDao.getStatusesBySourceAndType(StatusSource.CACHED, fileType)
    }
    
    suspend fun cacheStatus(documentFile: DocumentFile): StatusEntity? = withContext(Dispatchers.IO) {
        try {
            val filename = documentFile.name ?: return@withContext null
            
            // Check if already cached
            if (statusDao.existsByFilenameAndSource(filename, StatusSource.CACHED)) {
                Log.d(TAG, "Already cached: $filename")
                return@withContext null
            }
            
            val cacheDir = getCacheDirectory()
            val destFile = File(cacheDir, filename)
            
            // Copy file
            context.contentResolver.openInputStream(documentFile.uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (!destFile.exists()) {
                Log.e(TAG, "Failed to cache: $filename")
                return@withContext null
            }
            
            val fileType = if (SAFHelper.isVideoFile(filename)) FileType.VIDEO else FileType.IMAGE
            
            val status = StatusEntity(
                filename = filename,
                originalUri = documentFile.uri.toString(),
                localPath = destFile.absolutePath,
                fileType = fileType,
                source = StatusSource.CACHED,
                createdAt = documentFile.lastModified(),
                fileSize = destFile.length()
            )
            
            val id = statusDao.insertStatus(status)
            Log.d(TAG, "Cached: $filename (id: $id)")
            
            return@withContext status.copy(id = id)
        } catch (e: Exception) {
            Log.e(TAG, "Error caching status: ${e.message}")
            return@withContext null
        }
    }
    
    // ========== Saved Status ==========
    
    fun getSavedStatuses(): LiveData<List<StatusEntity>> {
        return statusDao.getStatusesBySource(StatusSource.SAVED)
    }
    
    fun getSavedStatusesByType(fileType: FileType): LiveData<List<StatusEntity>> {
        return statusDao.getStatusesBySourceAndType(StatusSource.SAVED, fileType)
    }
    
    /**
     * Save a live status (from SAF) to permanent storage
     */
    suspend fun saveStatus(filename: String, sourceUri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val savedDir = getSavedDirectory()
            val destFile = File(savedDir, filename)
            
            // Copy from URI
            val uri = android.net.Uri.parse(sourceUri)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (!destFile.exists()) {
                Log.e(TAG, "Failed to save: $filename")
                return@withContext false
            }
            
            val fileType = if (SAFHelper.isVideoFile(filename)) FileType.VIDEO else FileType.IMAGE
            
            // Add to saved statuses
            val status = StatusEntity(
                filename = filename,
                originalUri = sourceUri,
                localPath = destFile.absolutePath,
                fileType = fileType,
                source = StatusSource.SAVED,
                createdAt = System.currentTimeMillis(),
                fileSize = destFile.length()
            )
            statusDao.insertStatus(status)
            
            // Mark as downloaded
            val downloaded = DownloadedStatus(
                filename = filename,
                originalPath = sourceUri,
                savedPath = destFile.absolutePath
            )
            statusDao.markAsDownloaded(downloaded)
            
            Log.d(TAG, "Saved: $filename")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving status: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Save a cached status to permanent storage
     */
    suspend fun saveCachedStatus(cachedStatus: StatusEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val savedDir = getSavedDirectory()
            val sourceFile = File(cachedStatus.localPath)
            val destFile = File(savedDir, cachedStatus.filename)
            
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file not found: ${cachedStatus.localPath}")
                return@withContext false
            }
            
            sourceFile.copyTo(destFile, overwrite = true)
            
            // Add to saved statuses
            val status = cachedStatus.copy(
                id = 0, // New ID
                source = StatusSource.SAVED,
                localPath = destFile.absolutePath,
                savedAt = System.currentTimeMillis()
            )
            statusDao.insertStatus(status)
            
            // Mark as downloaded
            val downloaded = DownloadedStatus(
                filename = cachedStatus.filename,
                originalPath = cachedStatus.originalUri,
                savedPath = destFile.absolutePath
            )
            statusDao.markAsDownloaded(downloaded)
            
            Log.d(TAG, "Saved cached status: ${cachedStatus.filename}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cached status: ${e.message}")
            return@withContext false
        }
    }
    
    // ========== Download State ==========
    
    suspend fun isDownloaded(filename: String): Boolean {
        return statusDao.isDownloaded(filename)
    }
    
    fun getAllDownloaded(): LiveData<List<DownloadedStatus>> {
        return statusDao.getAllDownloaded()
    }
    
    suspend fun getAllDownloadedFilenames(): Set<String> {
        return statusDao.getAllDownloadedFilenames().toSet()
    }
    
    // ========== Cleanup ==========
    
    suspend fun deleteStatus(status: StatusEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(status.localPath)
            if (file.exists()) {
                file.delete()
            }
            statusDao.deleteStatus(status)
            
            // If it was saved, also remove from downloaded
            if (status.source == StatusSource.SAVED) {
                statusDao.removeDownloadedStatus(status.filename)
            }
            
            Log.d(TAG, "Deleted status: ${status.filename}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting status: ${e.message}")
            return@withContext false
        }
    }
    
    suspend fun cleanupOldCache(): Int = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (Constants.RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            val oldStatuses = statusDao.getCachedStatusesOlderThan(cutoffTime)
            
            var deletedCount = 0
            oldStatuses.forEach { status ->
                if (deleteStatus(status)) {
                    deletedCount++
                }
            }
            
            Log.d(TAG, "Cleaned up $deletedCount old cached files")
            return@withContext deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up cache: ${e.message}")
            return@withContext 0
        }
    }
    
    // ========== Full Backup ==========
    
    suspend fun performFullBackup(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var cached = 0
        var skipped = 0
        
        try {
            val statusFiles = SAFHelper.listStatusFiles(context)
            Log.d(TAG, "Found ${statusFiles.size} status files")
            
            statusFiles.forEach { documentFile ->
                val result = cacheStatus(documentFile)
                if (result != null) {
                    cached++
                } else {
                    skipped++
                }
            }
            
            // Clean up old files
            cleanupOldCache()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in full backup: ${e.message}")
        }
        
        return@withContext Pair(cached, skipped)
    }
}
