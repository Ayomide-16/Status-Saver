package com.statussaver.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

object SAFHelper {
    private const val TAG = "SAFHelper"
    
    /**
     * Store the WhatsApp status folder URI
     */
    fun storeUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(Constants.KEY_SAF_URI, uri.toString()).apply()
        Log.d(TAG, "Stored URI: $uri")
    }
    
    /**
     * Get the stored WhatsApp status folder URI
     */
    fun getStoredUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(Constants.KEY_SAF_URI, null)
        return uriString?.let { Uri.parse(it) }
    }
    
    /**
     * Take persistable permission for the URI
     */
    fun takePersistablePermission(context: Context, uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            Log.d(TAG, "Took persistable permission for: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable permission: ${e.message}")
            false
        }
    }
    
    /**
     * Check if we have valid permission for the stored URI
     */
    fun hasValidPermission(context: Context): Boolean {
        val storedUri = getStoredUri(context) ?: return false
        
        val persistedUris = context.contentResolver.persistedUriPermissions
        for (permission in persistedUris) {
            if (permission.uri == storedUri && permission.isReadPermission) {
                return true
            }
        }
        return false
    }
    
    /**
     * List all status files from the stored URI
     */
    fun listStatusFiles(context: Context): List<DocumentFile> {
        val storedUri = getStoredUri(context) ?: return emptyList()
        
        return try {
            val documentFile = DocumentFile.fromTreeUri(context, storedUri)
            if (documentFile == null || !documentFile.exists() || !documentFile.canRead()) {
                Log.e(TAG, "Cannot access document tree")
                return emptyList()
            }
            
            documentFile.listFiles()
                .filter { file ->
                    val name = file.name ?: return@filter false
                    // Skip hidden files and non-media files
                    !name.startsWith(".") && (isImageFile(name) || isVideoFile(name))
                }
                .sortedByDescending { it.lastModified() }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing status files: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get a specific file from the status folder
     */
    fun getStatusFile(context: Context, filename: String): DocumentFile? {
        val storedUri = getStoredUri(context) ?: return null
        
        return try {
            val documentFile = DocumentFile.fromTreeUri(context, storedUri)
            documentFile?.findFile(filename)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting status file: ${e.message}")
            null
        }
    }
    
    /**
     * Check if the file is an image
     */
    fun isImageFile(filename: String): Boolean {
        val lower = filename.lowercase()
        return Constants.IMAGE_EXTENSIONS.any { lower.endsWith(it) }
    }
    
    /**
     * Check if the file is a video
     */
    fun isVideoFile(filename: String): Boolean {
        val lower = filename.lowercase()
        return Constants.VIDEO_EXTENSIONS.any { lower.endsWith(it) }
    }
    
    /**
     * Clear stored URI and permissions
     */
    fun clearStoredUri(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(Constants.KEY_SAF_URI).apply()
        Log.d(TAG, "Cleared stored URI")
    }
}
