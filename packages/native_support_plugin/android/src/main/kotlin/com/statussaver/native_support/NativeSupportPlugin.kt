package com.statussaver.native_support

import android.content.Context
import android.net.Uri
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** NativeSupportPlugin */
class NativeSupportPlugin: FlutterPlugin, MethodCallHandler {
  private lateinit var channel : MethodChannel
  private lateinit var context : Context

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "native_support")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    if (call.method == "copyContentUriToFile") {
      val uriStr = call.argument<String>("uri")
      val destPath = call.argument<String>("destPath")
      
      if (uriStr == null || destPath == null) {
        result.error("INVALID_ARGS", "Uri and destPath required", null)
        return
      }

      try {
        val success = copyContentUriToFile(Uri.parse(uriStr), destPath)
        result.success(success)
      } catch (e: Exception) {
        result.error("COPY_ERROR", e.message, null)
      }
    } else {
      result.notImplemented()
    }
  }

  private fun copyContentUriToFile(uri: Uri, destPath: String): Boolean {
    var inputStream: InputStream? = null
    var outputStream: FileOutputStream? = null
    
    try {
      val contentResolver = context.contentResolver
      inputStream = contentResolver.openInputStream(uri) ?: return false
      
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
    } catch (e: Exception) {
      e.printStackTrace()
      return false
    } finally {
      try { inputStream?.close() } catch(e: Exception) {}
      try { outputStream?.close() } catch(e: Exception) {}
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }
}
