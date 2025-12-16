package com.statussaver.status_saver

import android.net.Uri
import android.content.ContentResolver
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : FlutterActivity() {
    private val CHANNEL = "status_saver/saf"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "copyContentUriToFile" -> {
                    val contentUri = call.argument<String>("uri")
                    val destPath = call.argument<String>("destPath")
                    
                    if (contentUri == null || destPath == null) {
                        result.error("INVALID_ARGS", "URI and destPath are required", null)
                        return@setMethodCallHandler
                    }
                    
                    try {
                        val success = copyContentUriToFile(contentUri, destPath)
                        result.success(success)
                    } catch (e: Exception) {
                        result.error("COPY_ERROR", e.message, null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }
    
    private fun copyContentUriToFile(contentUri: String, destPath: String): Boolean {
        val uri = Uri.parse(contentUri)
        val contentResolver: ContentResolver = applicationContext.contentResolver
        
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        
        try {
            inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return false
            }
            
            val destFile = File(destPath)
            destFile.parentFile?.mkdirs()
            
            outputStream = FileOutputStream(destFile)
            
            val buffer = ByteArray(4096)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            
            outputStream.flush()
            return destFile.exists()
            
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }
}
