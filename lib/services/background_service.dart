import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:workmanager/workmanager.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:path_provider/path_provider.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import '../config/constants.dart';
import '../models/cache_metadata.dart';

/// Background task name
const String backgroundCacheTask = 'com.statussaver.cache_statuses';
const String periodicCacheTask = 'com.statussaver.periodic_cache';

/// Initialize the background worker
Future<void> initializeBackgroundService() async {
  await Workmanager().initialize(
    callbackDispatcher,
    isInDebugMode: kDebugMode,
  );
  
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
    existingWorkPolicy: ExistingWorkPolicy.keep,
    backoffPolicy: BackoffPolicy.linear,
    backoffPolicyDelay: const Duration(minutes: 5),
  );
  
  debugPrint('Background: Periodic caching task registered');
}

/// Top-level callback for WorkManager
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    debugPrint('Background: Executing task: $task');
    
    try {
      // Initialize Hive for background
      await Hive.initFlutter();
      if (!Hive.isAdapterRegistered(1)) {
        Hive.registerAdapter(CacheMetadataAdapter());
      }
      
      // Run the caching logic
      await _cacheWhatsAppStatuses();
      
      debugPrint('Background: Task completed successfully');
      return true;
    } catch (e) {
      debugPrint('Background: Task failed: $e');
      return false;
    }
  });
}

/// Cache WhatsApp statuses in background
Future<void> _cacheWhatsAppStatuses() async {
  final cacheBox = await Hive.openBox<CacheMetadata>(AppConstants.cacheMetadataBox);
  
  // Get app directories
  final appDir = await getExternalStorageDirectory();
  if (appDir == null) {
    debugPrint('Background: No external storage directory');
    return;
  }
  
  final cacheDir = Directory('${appDir.path}/${AppConstants.cacheFolderName}');
  if (!await cacheDir.exists()) {
    await cacheDir.create(recursive: true);
  }
  
  final thumbnailsDir = Directory('${appDir.path}/${AppConstants.thumbnailsFolderName}');
  if (!await thumbnailsDir.exists()) {
    await thumbnailsDir.create(recursive: true);
  }
  
  // Find WhatsApp status directory
  Directory? statusDir;
  for (final path in AppConstants.whatsAppStatusPaths) {
    final dir = Directory(path);
    if (await dir.exists()) {
      statusDir = dir;
      break;
    }
  }
  
  if (statusDir == null) {
    debugPrint('Background: WhatsApp status directory not found');
    return;
  }
  
  int cachedCount = 0;
  
  try {
    final files = statusDir.listSync();
    
    for (final file in files) {
      if (file is File) {
        final extension = file.path.split('.').last.toLowerCase();
        final isImage = AppConstants.imageExtensions.contains('.$extension');
        final isVideo = AppConstants.videoExtensions.contains('.$extension');
        
        if (!isImage && !isVideo) continue;
        
        final fileName = file.path.split('/').last;
        
        // Check if already cached
        final existing = cacheBox.values.where((m) => m.fileName == fileName && !m.isExpired);
        if (existing.isNotEmpty) continue;
        
        // Copy to cache
        final cachedPath = '${cacheDir.path}/$fileName';
        await file.copy(cachedPath);
        
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
            debugPrint('Background: Thumbnail generation failed: $e');
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
      }
    }
    
    // Cleanup expired cache
    final expiredKeys = <dynamic>[];
    for (final entry in cacheBox.toMap().entries) {
      if (entry.value.isExpired) {
        expiredKeys.add(entry.key);
        try {
          final cachedFile = File(entry.value.cachedPath);
          if (await cachedFile.exists()) await cachedFile.delete();
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
    
    debugPrint('Background: Cached $cachedCount new statuses, cleaned ${expiredKeys.length} expired');
  } catch (e) {
    debugPrint('Background: Error caching statuses: $e');
  }
  
  await cacheBox.close();
}

/// Cancel background tasks
Future<void> cancelBackgroundService() async {
  await Workmanager().cancelByUniqueName(periodicCacheTask);
  debugPrint('Background: Periodic task cancelled');
}
