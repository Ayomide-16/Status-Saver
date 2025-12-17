import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:workmanager/workmanager.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:path_provider/path_provider.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import '../config/constants.dart';
import '../models/cache_metadata.dart';

/// Background task names
const String backgroundCacheTask = 'com.statussaver.cache_statuses';
const String periodicCacheTask = 'com.statussaver.periodic_cache';

/// Initialize the background worker
Future<void> initializeBackgroundService() async {
  try {
    await Workmanager().initialize(
      callbackDispatcher,
      isInDebugMode: kDebugMode,
    );
    
    // Cancel any existing tasks first
    await Workmanager().cancelAll();
    
    // Register periodic task - runs every 15 minutes (minimum for WorkManager)
    await Workmanager().registerPeriodicTask(
      periodicCacheTask,
      backgroundCacheTask,
      frequency: const Duration(minutes: 15),
      constraints: Constraints(
        networkType: NetworkType.not_required,
        requiresBatteryNotLow: false,
        requiresCharging: false,
        requiresDeviceIdle: false,
        requiresStorageNotLow: false,
      ),
      existingWorkPolicy: ExistingWorkPolicy.replace,
      backoffPolicy: BackoffPolicy.linear,
      backoffPolicyDelay: const Duration(minutes: 5),
    );
    
    debugPrint('Background: Periodic caching task registered (every 15 min)');
    
    // Also run once immediately after 30 seconds
    await Workmanager().registerOneOffTask(
      '${periodicCacheTask}_startup',
      backgroundCacheTask,
      initialDelay: const Duration(seconds: 30),
    );
    
    debugPrint('Background: One-off startup task scheduled');
  } catch (e) {
    debugPrint('Background: Failed to initialize: $e');
  }
}

/// Top-level callback for WorkManager - MUST be a top-level function
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    debugPrint('Background: ========= TASK STARTED =========');
    debugPrint('Background: Task: $task at ${DateTime.now()}');
    
    try {
      // Initialize Hive for background
      await Hive.initFlutter();
      if (!Hive.isAdapterRegistered(1)) {
        Hive.registerAdapter(CacheMetadataAdapter());
      }
      
      // Run the caching logic
      final result = await _backgroundCacheStatuses();
      
      debugPrint('Background: ========= TASK COMPLETED: $result =========');
      return result;
    } catch (e, stack) {
      debugPrint('Background: ========= TASK FAILED =========');
      debugPrint('Background: Error: $e');
      debugPrint('Background: Stack: $stack');
      return false;
    }
  });
}

/// Cache WhatsApp statuses in background
Future<bool> _backgroundCacheStatuses() async {
  debugPrint('Background: Starting status caching...');
  
  Box<CacheMetadata>? cacheBox;
  
  try {
    cacheBox = await Hive.openBox<CacheMetadata>(AppConstants.cacheMetadataBox);
    debugPrint('Background: Cache box opened with ${cacheBox.length} existing entries');
    
    // Get app directories
    final appDir = await getExternalStorageDirectory();
    if (appDir == null) {
      debugPrint('Background: ERROR - No external storage directory');
      return false;
    }
    
    final cacheDir = Directory('${appDir.path}/${AppConstants.cacheFolderName}');
    if (!await cacheDir.exists()) {
      await cacheDir.create(recursive: true);
    }
    debugPrint('Background: Cache dir: ${cacheDir.path}');
    
    final thumbnailsDir = Directory('${appDir.path}/${AppConstants.thumbnailsFolderName}');
    if (!await thumbnailsDir.exists()) {
      await thumbnailsDir.create(recursive: true);
    }
    
    // Find WhatsApp status directory - try multiple paths
    Directory? statusDir;
    for (final path in AppConstants.whatsAppStatusPaths) {
      final dir = Directory(path);
      if (await dir.exists()) {
        statusDir = dir;
        debugPrint('Background: Found status directory: $path');
        break;
      }
    }
    
    // Also check the SAF temp cache directory as fallback
    if (statusDir == null) {
      final tempDir = await getTemporaryDirectory();
      final safCacheDir = Directory('${tempDir.path}/status_cache');
      if (await safCacheDir.exists()) {
        statusDir = safCacheDir;
        debugPrint('Background: Using SAF cache as source: ${safCacheDir.path}');
      }
    }
    
    if (statusDir == null) {
      debugPrint('Background: ERROR - No status directory found');
      return false;
    }
    
    int cachedCount = 0;
    int skippedCount = 0;
    int errorCount = 0;
    
    final files = await statusDir.list().toList();
    debugPrint('Background: Found ${files.length} files to process');
    
    for (final file in files) {
      if (file is File) {
        final fileName = file.path.split(Platform.pathSeparator).last;
        final extension = fileName.split('.').last.toLowerCase();
        final isImage = AppConstants.imageExtensions.contains('.$extension');
        final isVideo = AppConstants.videoExtensions.contains('.$extension');
        
        if (!isImage && !isVideo) continue;
        
        // Check if already cached (by filename)
        final existingEntries = cacheBox.values.where((m) => m.fileName == fileName && !m.isExpired);
        if (existingEntries.isNotEmpty) {
          skippedCount++;
          continue;
        }
        
        try {
          // Copy to cache
          final cachedPath = '${cacheDir.path}/$fileName';
          final destFile = File(cachedPath);
          
          if (!await destFile.exists()) {
            await file.copy(cachedPath);
          }
          
          // Generate thumbnail for videos
          String? thumbnailPath;
          if (isVideo) {
            try {
              thumbnailPath = await VideoThumbnail.thumbnailFile(
                video: cachedPath,
                thumbnailPath: thumbnailsDir.path,
                imageFormat: ImageFormat.JPEG,
                maxWidth: AppConstants.thumbnailWidth,
                quality: AppConstants.thumbnailQuality,
              );
            } catch (e) {
              debugPrint('Background: Thumbnail failed for $fileName');
            }
          }
          
          // Save metadata
          final stat = await file.stat();
          final metadata = CacheMetadata.create(
            originalPath: file.path,
            cachedPath: cachedPath,
            isVideo: isVideo,
            fileSize: stat.size,
            fileName: fileName,
            cacheDurationDays: AppConstants.cacheDurationDays,
            thumbnailPath: thumbnailPath,
          );
          
          await cacheBox.put(fileName, metadata);
          cachedCount++;
        } catch (e) {
          debugPrint('Background: Error caching $fileName: $e');
          errorCount++;
        }
      }
    }
    
    // Cleanup expired cache
    int cleanedCount = 0;
    final expiredKeys = <dynamic>[];
    for (final entry in cacheBox.toMap().entries) {
      if (entry.value.isExpired) {
        expiredKeys.add(entry.key);
        try {
          final cachedFile = File(entry.value.cachedPath);
          if (await cachedFile.exists()) {
            await cachedFile.delete();
            cleanedCount++;
          }
          if (entry.value.thumbnailPath != null) {
            final thumbFile = File(entry.value.thumbnailPath!);
            if (await thumbFile.exists()) await thumbFile.delete();
          }
        } catch (_) {}
      }
    }
    for (final key in expiredKeys) {
      await cacheBox.delete(key);
    }
    
    await cacheBox.flush();
    
    debugPrint('Background: Summary - Cached: $cachedCount, Skipped: $skippedCount, Errors: $errorCount, Cleaned: $cleanedCount');
    
    return true;
  } catch (e) {
    debugPrint('Background: Fatal error: $e');
    return false;
  } finally {
    await cacheBox?.close();
  }
}

/// Run background cache manually (for testing or on-demand)
Future<void> runBackgroundCacheNow() async {
  try {
    await Workmanager().registerOneOffTask(
      '${periodicCacheTask}_manual_${DateTime.now().millisecondsSinceEpoch}',
      backgroundCacheTask,
    );
    debugPrint('Background: Manual cache task scheduled');
  } catch (e) {
    debugPrint('Background: Failed to schedule manual task: $e');
  }
}

/// Cancel background tasks
Future<void> cancelBackgroundService() async {
  await Workmanager().cancelAll();
  debugPrint('Background: All tasks cancelled');
}
