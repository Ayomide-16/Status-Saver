import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import '../config/constants.dart';
import '../models/status_item.dart';

class StorageService {
  static final StorageService _instance = StorageService._internal();
  factory StorageService() => _instance;
  StorageService._internal();

  Directory? _savedDir;
  Directory? _thumbnailsDir;

  Future<void> initialize() async {
    final appDir = await getExternalStorageDirectory();
    if (appDir != null) {
      _savedDir = Directory('${appDir.path}/${AppConstants.savedFolderName}');
      if (!await _savedDir!.exists()) {
        await _savedDir!.create(recursive: true);
      }
      
      // Create thumbnails directory
      _thumbnailsDir = Directory('${appDir.path}/${AppConstants.thumbnailsFolderName}');
      if (!await _thumbnailsDir!.exists()) {
        await _thumbnailsDir!.create(recursive: true);
      }
    }

    // Also try to create in external storage for gallery access
    try {
      final externalDir = Directory('/storage/emulated/0/${AppConstants.savedFolderName}');
      if (!await externalDir.exists()) {
        await externalDir.create(recursive: true);
      }
      _savedDir = externalDir;
    } catch (e) {
      debugPrint('Could not create external saved folder: $e');
    }
  }

  Directory? get savedDir => _savedDir;

  /// Generate thumbnail for a video
  Future<String?> _generateThumbnail(String videoPath, String fileName) async {
    if (_thumbnailsDir == null) return null;
    
    // Check if thumbnail already exists
    final thumbName = '${fileName.split('.').first}_thumb.jpg';
    final existingThumb = File('${_thumbnailsDir!.path}/$thumbName');
    if (await existingThumb.exists()) {
      return existingThumb.path;
    }

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
      debugPrint('Error generating thumbnail for saved video: $e');
      return null;
    }
  }

  Future<bool> saveStatus(StatusItem status) async {
    if (_savedDir == null) {
      await initialize();
    }
    if (_savedDir == null) return false;

    try {
      final sourceFile = File(status.path);
      if (!await sourceFile.exists()) return false;

      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final newFileName = 'status_$timestamp.${status.extension}';
      final destPath = '${_savedDir!.path}/$newFileName';

      await sourceFile.copy(destPath);
      
      // Generate thumbnail for videos
      if (status.isVideo) {
        await _generateThumbnail(destPath, newFileName);
      }
      
      // Notify media scanner on Android
      if (Platform.isAndroid) {
        try {
          Process.run('am', [
            'broadcast',
            '-a',
            'android.intent.action.MEDIA_SCANNER_SCAN_FILE',
            '-d',
            'file://$destPath'
          ]);
        } catch (e) {
          debugPrint('Could not notify media scanner: $e');
        }
      }

      return true;
    } catch (e) {
      debugPrint('Error saving status: $e');
      return false;
    }
  }

  Future<List<StatusItem>> getSavedStatuses() async {
    if (_savedDir == null) {
      await initialize();
    }
    if (_savedDir == null || !await _savedDir!.exists()) {
      return [];
    }

    final List<StatusItem> statuses = [];

    try {
      final files = _savedDir!.listSync();
      
      for (final file in files) {
        if (file is File) {
          final extension = file.path.split('.').last.toLowerCase();
          final isImage = AppConstants.imageExtensions.contains('.$extension');
          final isVideo = AppConstants.videoExtensions.contains('.$extension');

          if (isImage || isVideo) {
            final fileName = file.path.split(Platform.pathSeparator).last;
            
            // Generate thumbnail for videos
            String? thumbnailPath;
            if (isVideo) {
              thumbnailPath = await _generateThumbnail(file.path, fileName);
            }
            
            final statusItem = StatusItem.fromFile(file, isVideo: isVideo);
            statuses.add(statusItem.copyWith(
              isSaved: true,
              thumbnailPath: thumbnailPath,
            ));
          }
        }
      }

      statuses.sort((a, b) => b.timestamp.compareTo(a.timestamp));
    } catch (e) {
      debugPrint('Error loading saved statuses: $e');
    }

    return statuses;
  }

  Future<bool> deleteStatus(StatusItem status) async {
    try {
      final file = File(status.path);
      if (await file.exists()) {
        await file.delete();
        
        // Also delete thumbnail if exists
        if (status.thumbnailPath != null) {
          final thumbFile = File(status.thumbnailPath!);
          if (await thumbFile.exists()) {
            await thumbFile.delete();
          }
        }
        
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Error deleting status: $e');
      return false;
    }
  }

  Future<void> shareStatus(StatusItem status) async {
    try {
      await Share.shareXFiles(
        [XFile(status.path)],
        text: 'Check out this status!',
      );
    } catch (e) {
      debugPrint('Error sharing status: $e');
    }
  }

  Future<int> getSavedCount() async {
    if (_savedDir == null || !await _savedDir!.exists()) return 0;
    
    int count = 0;
    try {
      final files = _savedDir!.listSync();
      for (final file in files) {
        if (file is File) {
          final extension = file.path.split('.').last.toLowerCase();
          if (AppConstants.imageExtensions.contains('.$extension') ||
              AppConstants.videoExtensions.contains('.$extension')) {
            count++;
          }
        }
      }
    } catch (e) {
      debugPrint('Error counting saved statuses: $e');
    }
    return count;
  }

  Future<int> getSavedSize() async {
    if (_savedDir == null) return 0;

    int size = 0;
    try {
      if (await _savedDir!.exists()) {
        final files = _savedDir!.listSync();
        for (final file in files) {
          if (file is File) {
            size += await file.length();
          }
        }
      }
    } catch (e) {
      debugPrint('Error calculating saved size: $e');
    }
    return size;
  }

  String formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
}
