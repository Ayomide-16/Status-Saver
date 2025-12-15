import 'dart:io';
import 'package:hive/hive.dart';
import 'package:path_provider/path_provider.dart';
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

  Future<void> cacheStatus(StatusItem status, {String? thumbnailPath}) async {
    if (_cacheDir == null) return;

    // Check if already cached
    if (_cacheBox.values.any((m) => m.originalPath == status.path)) {
      return;
    }

    try {
      final sourceFile = File(status.path);
      if (!await sourceFile.exists()) return;

      // Create cached file
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final cachedFileName = 'cached_$timestamp.${status.extension}';
      final cachedPath = '${_cacheDir!.path}/$cachedFileName';

      await sourceFile.copy(cachedPath);

      // Store metadata
      final metadata = CacheMetadata.create(
        originalPath: status.path,
        cachedPath: cachedPath,
        isVideo: status.isVideo,
        fileSize: status.size,
        fileName: status.name,
        cacheDurationDays: AppConstants.cacheDurationDays,
        thumbnailPath: thumbnailPath,
      );

      await _cacheBox.put(status.path, metadata);
    } catch (e) {
      print('Error caching status: $e');
    }
  }

  Future<List<StatusItem>> getCachedStatuses() async {
    final List<StatusItem> statuses = [];

    for (final metadata in _cacheBox.values) {
      if (!metadata.isExpired) {
        final cachedFile = File(metadata.cachedPath);
        if (await cachedFile.exists()) {
          final statusItem = StatusItem(
            path: metadata.cachedPath,
            name: metadata.fileName,
            isVideo: metadata.isVideo,
            timestamp: metadata.cachedAt,
            size: metadata.fileSize,
            thumbnailPath: metadata.thumbnailPath,
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

    for (final entry in _cacheBox.toMap().entries) {
      final metadata = entry.value;
      if (metadata.isExpired) {
        expiredKeys.add(entry.key);

        // Delete cached file
        try {
          final cachedFile = File(metadata.cachedPath);
          if (await cachedFile.exists()) {
            await cachedFile.delete();
          }

          // Delete thumbnail if exists
          if (metadata.thumbnailPath != null) {
            final thumbnailFile = File(metadata.thumbnailPath!);
            if (await thumbnailFile.exists()) {
              await thumbnailFile.delete();
            }
          }
        } catch (e) {
          print('Error deleting expired cache file: $e');
        }
      }
    }

    // Remove metadata entries
    for (final key in expiredKeys) {
      await _cacheBox.delete(key);
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
        print('Error clearing cache file: $e');
      }
    }

    await _cacheBox.clear();
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
          print('Error removing cache file: $e');
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
      print('Error calculating cache size: $e');
    }
    return size;
  }

  int get cachedItemsCount => _cacheBox.length;

  String formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}
