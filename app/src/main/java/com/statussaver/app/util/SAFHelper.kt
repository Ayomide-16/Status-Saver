package com.statussaver.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

object SAFHelper {
    private const val TAG = "SAFHelper"

    /**
     * Get the stored SAF URI from SharedPreferences
     */
    fun getStoredUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(Constants.KEY_SAF_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    /**
     * Store the SAF URI in SharedPreferences
     */
    fun storeUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(Constants.KEY_SAF_URI, uri.toString()).apply()
        Log.d(TAG, "Stored SAF URI: $uri")
    }

    /**
     * Clear the stored SAF URI
     */
    fun clearStoredUri(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(Constants.KEY_SAF_URI).apply()
        Log.d(TAG, "Cleared stored SAF URI")
    }

    /**
     * Take persistable URI permission
     */
    fun takePersistablePermission(context: Context, uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
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
        val uri = getStoredUri(context) ?: return false
        
        return try {
            val persistedUris = context.contentResolver.persistedUriPermissions
            val hasPermission = persistedUris.any { 
                it.uri == uri && it.isReadPermission 
            }
            
            if (hasPermission) {
                // Also verify we can actually access the folder
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                documentFile?.exists() == true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission: ${e.message}")
            false
        }
    }

    /**
     * List all status files from the granted folder
     */
    fun listStatusFiles(context: Context): List<DocumentFile> {
        val uri = getStoredUri(context) ?: return emptyList()
        
        return try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            documentFile?.listFiles()?.filter { file ->
                if (file.isDirectory) return@filter false
                val name = file.name?.lowercase() ?: return@filter false
                val isImage = Constants.IMAGE_EXTENSIONS.any { name.endsWith(it) }
                val isVideo = Constants.VIDEO_EXTENSIONS.any { name.endsWith(it) }
                isImage || isVideo
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing status files: ${e.message}")
            emptyList()
        }
    }

    /**
     * Check if a file is a video based on its name
     */
    fun isVideoFile(fileName: String): Boolean {
        val name = fileName.lowercase()
        return Constants.VIDEO_EXTENSIONS.any { name.endsWith(it) }
    }

    /**
     * Check if a file is an image based on its name
     */
    fun isImageFile(fileName: String): Boolean {
        val name = fileName.lowercase()
        return Constants.IMAGE_EXTENSIONS.any { name.endsWith(it) }
    }
}
