package com.statussaver.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import com.statussaver.app.data.database.*
import com.statussaver.app.util.Constants
import com.statussaver.app.util.PermissionHelper
import com.statussaver.app.util.SAFHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class StatusRepository(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val statusDao = database.statusDao()
    
    companion object {
        private const val TAG = "StatusRepository"
        const val PUBLIC_FOLDER_NAME = "SA Status Saver"
    }
    
    // ========== Directory Management ==========
    
    fun getCacheDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), Constants.CACHE_FOLDER_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Get the public saved directory visible in gallery apps.
     * Creates "SA Status Saver" folder in device root storage.
     */
    fun getSavedDirectory(): File {
        val dir = File(Environment.getExternalStorageDirectory(), PUBLIC_FOLDER_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Get subdirectory for images within SA Status Saver folder
     */
    private fun getSavedImagesDirectory(): File {
        val dir = File(getSavedDirectory(), "Images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Get subdirectory for videos within SA Status Saver folder
     */
    private fun getSavedVideosDirectory(): File {
        val dir = File(getSavedDirectory(), "Videos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    fun getWhatsAppStatusUri(): String {
        return SAFHelper.getStoredUri(context)?.toString() ?: "Not set"
    }
    
    // ========== Media Scanner ==========
    
    /**
     * Scan the saved file so it appears in gallery immediately
     */
    private fun scanMediaFile(file: File) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null
            ) { path, uri ->
                Log.d(TAG, "Scanned file: $path -> $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media file: ${e.message}")
        }
    }
    
    // ========== Save Methods ==========
    
    /**
     * Save file using MediaStore API (Android 10+) - works without special permissions
     * Returns the saved file path or null on failure
     */
    private suspend fun saveViaMediaStore(
        inputUri: Uri,
        filename: String,
        isVideo: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext null
            }
            
            val mimeType = if (isVideo) {
                getMimeType(filename, "video/mp4")
            } else {
                getMimeType(filename, "image/jpeg")
            }
            
            val relativePath = if (isVideo) {
                Environment.DIRECTORY_MOVIES + "/$PUBLIC_FOLDER_NAME"
            } else {
                Environment.DIRECTORY_PICTURES + "/$PUBLIC_FOLDER_NAME"
            }
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            
            val insertUri = context.contentResolver.insert(collection, contentValues)
                ?: return@withContext null
            
            // Copy content
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                context.contentResolver.openOutputStream(insertUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            
            // Update IS_PENDING to 0 to make file visible
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(insertUri, contentValues, null, null)
            }
            
            // Get actual file path
            val savedPath = getFilePathFromMediaStoreUri(insertUri)
            Log.d(TAG, "Saved via MediaStore: $filename -> $savedPath")
            return@withContext savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving via MediaStore: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Save file using MediaStore API from a local File
     */
    private suspend fun saveFileViaMediaStore(
        sourceFile: File,
        filename: String,
        isVideo: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext null
            }
            
            val mimeType = if (isVideo) {
                getMimeType(filename, "video/mp4")
            } else {
                getMimeType(filename, "image/jpeg")
            }
            
            val relativePath = if (isVideo) {
                Environment.DIRECTORY_MOVIES + "/$PUBLIC_FOLDER_NAME"
            } else {
                Environment.DIRECTORY_PICTURES + "/$PUBLIC_FOLDER_NAME"
            }
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            
            val insertUri = context.contentResolver.insert(collection, contentValues)
                ?: return@withContext null
            
            // Copy content from file
            FileInputStream(sourceFile).use { input ->
                context.contentResolver.openOutputStream(insertUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            
            // Update IS_PENDING to 0 to make file visible
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(insertUri, contentValues, null, null)
            }
            
            // Get actual file path
            val savedPath = getFilePathFromMediaStoreUri(insertUri)
            Log.d(TAG, "Saved file via MediaStore: $filename -> $savedPath")
            return@withContext savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file via MediaStore: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Save file directly to public storage (Android 9 or when All Files Access is granted)
     */
    private suspend fun saveDirectly(
        inputUri: Uri,
        filename: String,
        isVideo: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val savedDir = if (isVideo) getSavedVideosDirectory() else getSavedImagesDirectory()
            val destFile = File(savedDir, filename)
            
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            
            if (!destFile.exists() || destFile.length() == 0L) {
                return@withContext null
            }
            
            // Scan so it appears in gallery
            scanMediaFile(destFile)
            
            Log.d(TAG, "Saved directly: $filename -> ${destFile.absolutePath}")
            return@withContext destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving directly: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Save file directly from a local file
     */
    private suspend fun saveFileDirectly(
        sourceFile: File,
        filename: String,
        isVideo: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val savedDir = if (isVideo) getSavedVideosDirectory() else getSavedImagesDirectory()
            val destFile = File(savedDir, filename)
            
            sourceFile.copyTo(destFile, overwrite = true)
            
            if (!destFile.exists() || destFile.length() == 0L) {
                return@withContext null
            }
            
            // Scan so it appears in gallery
            scanMediaFile(destFile)
            
            Log.d(TAG, "Saved file directly: $filename -> ${destFile.absolutePath}")
            return@withContext destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file directly: ${e.message}", e)
            return@withContext null
        }
    }
    
    private fun getMimeType(filename: String, default: String): String {
        val ext = filename.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> default
        }
    }
    
    private fun getFilePathFromMediaStoreUri(uri: Uri): String {
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file path from uri: ${e.message}")
        }
        return uri.toString()
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
    
    /**
     * Get a cached status by filename (direct query, not LiveData)
     */
    suspend fun getCachedStatusByFilename(filename: String): StatusEntity? {
        return statusDao.getStatusByFilenameAndSource(filename, StatusSource.CACHED)
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
            if (id == -1L) {
                return@withContext null
            }
            
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
     * Save a live status (from SAF) to public SA Status Saver folder
     * Uses MediaStore API on Android 10+ for guaranteed compatibility
     */
    suspend fun saveStatus(filename: String, sourceUri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(sourceUri)
            val isVideo = SAFHelper.isVideoFile(filename)
            
            var savedPath: String? = null
            
            // Try MediaStore first on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savedPath = saveViaMediaStore(uri, filename, isVideo)
            }
            
            // Fallback to direct save if MediaStore failed or on older Android
            if (savedPath == null) {
                if (PermissionHelper.canWriteToExternalStorage(context)) {
                    savedPath = saveDirectly(uri, filename, isVideo)
                }
            }
            
            if (savedPath == null) {
                Log.e(TAG, "Failed to save: $filename - no save method succeeded")
                return@withContext false
            }
            
            val fileType = if (isVideo) FileType.VIDEO else FileType.IMAGE
            
            // Add to saved statuses
            val status = StatusEntity(
                filename = filename,
                originalUri = sourceUri,
                localPath = savedPath,
                fileType = fileType,
                source = StatusSource.SAVED,
                createdAt = System.currentTimeMillis(),
                fileSize = File(savedPath).let { if (it.exists()) it.length() else 0L }
            )
            statusDao.insertStatus(status)
            
            // Mark as downloaded
            val downloaded = DownloadedStatus(
                filename = filename,
                originalPath = sourceUri,
                savedPath = savedPath
            )
            statusDao.markAsDownloaded(downloaded)
            
            Log.d(TAG, "Saved: $filename to $savedPath")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving status: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Save a cached status to public SA Status Saver folder
     */
    suspend fun saveCachedStatus(cachedStatus: StatusEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(cachedStatus.localPath)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file not found: ${cachedStatus.localPath}")
                return@withContext false
            }
            
            val isVideo = cachedStatus.fileType == FileType.VIDEO
            var savedPath: String? = null
            
            // Try MediaStore first on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savedPath = saveFileViaMediaStore(sourceFile, cachedStatus.filename, isVideo)
            }
            
            // Fallback to direct save
            if (savedPath == null) {
                if (PermissionHelper.canWriteToExternalStorage(context)) {
                    savedPath = saveFileDirectly(sourceFile, cachedStatus.filename, isVideo)
                }
            }
            
            if (savedPath == null) {
                Log.e(TAG, "Failed to save cached: ${cachedStatus.filename}")
                return@withContext false
            }
            
            // Add to saved statuses
            val status = cachedStatus.copy(
                id = 0,
                source = StatusSource.SAVED,
                localPath = savedPath,
                savedAt = System.currentTimeMillis()
            )
            statusDao.insertStatus(status)
            
            // Mark as downloaded
            val downloaded = DownloadedStatus(
                filename = cachedStatus.filename,
                originalPath = cachedStatus.originalUri,
                savedPath = savedPath
            )
            statusDao.markAsDownloaded(downloaded)
            
            Log.d(TAG, "Saved cached status: ${cachedStatus.filename}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cached status: ${e.message}", e)
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
    
    suspend fun markAsDownloadedDirect(filename: String, originalUri: String, savedPath: String) {
        withContext(Dispatchers.IO) {
            val downloaded = DownloadedStatus(
                filename = filename,
                originalPath = originalUri,
                savedPath = savedPath
            )
            statusDao.markAsDownloaded(downloaded)
            
            val fileType = if (SAFHelper.isVideoFile(filename)) FileType.VIDEO else FileType.IMAGE
            val status = StatusEntity(
                filename = filename,
                originalUri = originalUri,
                localPath = savedPath,
                fileType = fileType,
                source = StatusSource.SAVED,
                createdAt = System.currentTimeMillis(),
                fileSize = File(savedPath).let { if (it.exists()) it.length() else 0L }
            )
            statusDao.insertStatus(status)
            
            Log.d(TAG, "Marked as downloaded: $filename")
        }
    }
    
    // ========== Cleanup ==========
    
    suspend fun deleteStatus(status: StatusEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(status.localPath)
            if (file.exists()) {
                file.delete()
                scanMediaFile(file)
            }
            statusDao.deleteStatus(status)
            
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
    
    suspend fun removeDuplicates(): Int = withContext(Dispatchers.IO) {
        try {
            val count = statusDao.removeDuplicates()
            Log.d(TAG, "Removed $count duplicate entries")
            return@withContext count
        } catch (e: Exception) {
            Log.e(TAG, "Error removing duplicates: ${e.message}")
            return@withContext 0
        }
    }
    
    suspend fun cleanupOldCache(): Int = withContext(Dispatchers.IO) {
        try {
            val retentionDays = Constants.getRetentionDays(context)
            val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
            val oldStatuses = statusDao.getCachedStatusesOlderThan(cutoffTime)
            
            var deletedCount = 0
            oldStatuses.forEach { status ->
                if (deleteStatus(status)) {
                    deletedCount++
                }
            }
            
            Log.d(TAG, "Cleaned up $deletedCount old cached files (retention: $retentionDays days)")
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
            
            cleanupOldCache()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in full backup: ${e.message}")
        }
        
        return@withContext Pair(cached, skipped)
    }
}
