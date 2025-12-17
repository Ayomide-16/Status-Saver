import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:hive/hive.dart';
import 'package:path_provider/path_provider.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import '../config/constants.dart';
import '../models/cache_metadata.dart';
import '../models/status_item.dart';

class CacheService {
  static final CacheService _instance = CacheService._internal();
  factory CacheService() => _instance;
  CacheService._internal();

  late Box<CacheMetadata> _cacheBox;
  Directory? _cacheDir;
  Directory? _thumbnailsDir;

  String? get cacheDirectoryPath => _cacheDir?.path;

  Future<void> initialize() async {
    _cacheBox = await Hive.openBox<CacheMetadata>(AppConstants.cacheMetadataBox);
    
    final appDir = await getExternalStorageDirectory();
    if (appDir != null) {
      _cacheDir = Directory('${appDir.path}/${AppConstants.cacheFolderName}');
      if (!await _cacheDir!.exists()) {
        await _cacheDir!.create(recursive: true);
      }

      _thumbnailsDir = Directory('${appDir.path}/${AppConstants.thumbnailsFolderName}');
      if (!await _thumbnailsDir!.exists()) {
        await _thumbnailsDir!.create(recursive: true);
      }
    }
  }

  /// Check if a file is already cached by its original filename
  bool isAlreadyCached(String fileName) {
    return _cacheBox.values.any((m) => m.fileName == fileName && !m.isExpired);
  }

  /// Get cache metadata by filename
  CacheMetadata? getCacheMetadataByName(String fileName) {
    try {
      return _cacheBox.values.firstWhere(
        (m) => m.fileName == fileName && !m.isExpired,
      );
    } catch (e) {
      return null;
    }
  }

  /// Generate thumbnail for a video file
  Future<String?> _generateThumbnail(String videoPath) async {
    if (_thumbnailsDir == null) return null;

    try {
      final thumbnailPath = await VideoThumbnail.thumbnailFile(
        video: videoPath,
        thumbnailPath: _thumbnailsDir!.path,
        imageFormat: ImageFormat.JPEG,
        maxWidth: AppConstants.thumbnailWidth,
        quality: AppConstants.thumbnailQuality,
      );
      return thumbnailPath;
    } catch (e) {
      debugPrint('Cache: Error generating thumbnail: $e');
      return null;
    }
  }

  /// Cache a status from a local path (used for auto-caching live statuses)
  Future<void> cacheStatusFromPath({
    required String localPath,
    required String fileName,
    required bool isVideo,
    required int fileSize,
    String? thumbnailPath,
  }) async {
    if (_cacheDir == null) return;

    // Check if already cached by filename (prevent duplicates)
    if (isAlreadyCached(fileName)) {
      return;
    }

    try {
      // Copy file to cache directory
      final cachedPath = '${_cacheDir!.path}/$fileName';
      final sourceFile = File(localPath);
      
      if (!await sourceFile.exists()) {
        return;
      }
      
      // Copy to cache
      await sourceFile.copy(cachedPath);

      // Generate thumbnail for videos if not provided
      String? finalThumbnailPath = thumbnailPath;
      if (isVideo && finalThumbnailPath == null) {
        finalThumbnailPath = await _generateThumbnail(cachedPath);
      }

      // Store metadata
      final metadata = CacheMetadata.create(
        originalPath: localPath,
        cachedPath: cachedPath,
        isVideo: isVideo,
        fileSize: fileSize,
        fileName: fileName,
        cacheDurationDays: AppConstants.cacheDurationDays,
        thumbnailPath: finalThumbnailPath,
      );

      await _cacheBox.put(fileName, metadata);
      debugPrint('Cache: Cached $fileName (thumbnail: ${finalThumbnailPath != null})');
    } catch (e) {
      debugPrint('Cache: Error caching $fileName: $e');
    }
  }

  /// Legacy method - cache status from StatusItem
  Future<void> cacheStatus(StatusItem status, {String? thumbnailPath}) async {
    await cacheStatusFromPath(
      localPath: status.path,
      fileName: status.name,
      isVideo: status.isVideo,
      fileSize: status.size,
      thumbnailPath: thumbnailPath ?? status.thumbnailPath,
    );
  }

  Future<List<StatusItem>> getCachedStatuses() async {
    final List<StatusItem> statuses = [];

    for (final metadata in _cacheBox.values) {
      if (!metadata.isExpired) {
        final cachedFile = File(metadata.cachedPath);
        if (await cachedFile.exists()) {
          // Generate thumbnail if video and missing
          String? thumbnailPath = metadata.thumbnailPath;
          if (metadata.isVideo && (thumbnailPath == null || !File(thumbnailPath).existsSync())) {
            thumbnailPath = await _generateThumbnail(metadata.cachedPath);
            // Update metadata with new thumbnail
            if (thumbnailPath != null) {
              metadata.thumbnailPath = thumbnailPath;
              await metadata.save();
            }
          }

          final statusItem = StatusItem(
            path: metadata.cachedPath,
            name: metadata.fileName,
            isVideo: metadata.isVideo,
            timestamp: metadata.cachedAt,
            size: metadata.fileSize,
            thumbnailPath: thumbnailPath,
            originalPath: metadata.originalPath,
            isCached: true,
          );
          statuses.add(statusItem);
        }
      }
    }

    statuses.sort((a, b) => b.timestamp.compareTo(a.timestamp));
    return statuses;
  }

  Future<void> cleanupExpiredCache() async {
    final expiredKeys = <dynamic>[];
    int cleanedCount = 0;

    for (final entry in _cacheBox.toMap().entries) {
      final metadata = entry.value;
      if (metadata.isExpired) {
        expiredKeys.add(entry.key);

        // Delete cached file
        try {
          final cachedFile = File(metadata.cachedPath);
          if (await cachedFile.exists()) {
            await cachedFile.delete();
            cleanedCount++;
          }

          // Delete thumbnail if exists
          if (metadata.thumbnailPath != null) {
            final thumbnailFile = File(metadata.thumbnailPath!);
            if (await thumbnailFile.exists()) {
              await thumbnailFile.delete();
            }
          }
        } catch (e) {
          debugPrint('Cache: Error deleting expired file: $e');
        }
      }
    }

    // Remove metadata entries
    for (final key in expiredKeys) {
      await _cacheBox.delete(key);
    }
    
    if (cleanedCount > 0) {
      debugPrint('Cache: Cleaned up $cleanedCount expired files');
    }
  }

  Future<void> clearAllCache() async {
    for (final metadata in _cacheBox.values) {
      try {
        final cachedFile = File(metadata.cachedPath);
        if (await cachedFile.exists()) {
          await cachedFile.delete();
        }

        if (metadata.thumbnailPath != null) {
          final thumbnailFile = File(metadata.thumbnailPath!);
          if (await thumbnailFile.exists()) {
            await thumbnailFile.delete();
          }
        }
      } catch (e) {
        debugPrint('Cache: Error clearing file: $e');
      }
    }

    await _cacheBox.clear();
    debugPrint('Cache: All cache cleared');
  }

  Future<void> removeFromCache(String cachedPath) async {
    final keysToRemove = <dynamic>[];

    for (final entry in _cacheBox.toMap().entries) {
      if (entry.value.cachedPath == cachedPath) {
        keysToRemove.add(entry.key);

        try {
          final cachedFile = File(entry.value.cachedPath);
          if (await cachedFile.exists()) {
            await cachedFile.delete();
          }

          if (entry.value.thumbnailPath != null) {
            final thumbnailFile = File(entry.value.thumbnailPath!);
            if (await thumbnailFile.exists()) {
              await thumbnailFile.delete();
            }
          }
        } catch (e) {
          debugPrint('Cache: Error removing file: $e');
        }
      }
    }

    for (final key in keysToRemove) {
      await _cacheBox.delete(key);
    }
  }

  bool isCached(String originalPath) {
    final metadata = _cacheBox.get(originalPath);
    return metadata != null && !metadata.isExpired;
  }

  CacheMetadata? getCacheMetadata(String originalPath) {
    return _cacheBox.get(originalPath);
  }

  Future<int> getCacheSize() async {
    if (_cacheDir == null) return 0;

    int size = 0;
    try {
      if (await _cacheDir!.exists()) {
        await for (final file in _cacheDir!.list(recursive: true)) {
          if (file is File) {
            size += await file.length();
          }
        }
      }
    } catch (e) {
      debugPrint('Cache: Error calculating size: $e');
    }
    return size;
  }

  int get cachedItemsCount => _cacheBox.length;

  String formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
  
  /// Get days remaining for a cached file
  int getDaysRemaining(String fileName) {
    final metadata = getCacheMetadataByName(fileName);
    if (metadata == null) return 0;
    return metadata.daysUntilExpiry;
  }
}
