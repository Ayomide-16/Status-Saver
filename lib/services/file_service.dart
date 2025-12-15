import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:video_thumbnail/video_thumbnail.dart';
import '../config/constants.dart';
import '../models/status_item.dart';

class FileService {
  static final FileService _instance = FileService._internal();
  factory FileService() => _instance;
  FileService._internal();

  Directory? _whatsAppStatusDir;
  Directory? _savedStatusDir;
  Directory? _cacheDir;
  Directory? _thumbnailsDir;

  Future<void> initialize() async {
    await _findWhatsAppStatusDirectory();
    await _initializeAppDirectories();
  }

  Future<void> _findWhatsAppStatusDirectory() async {
    if (!Platform.isAndroid) return;

    for (final path in AppConstants.whatsAppStatusPaths) {
      final dir = Directory(path);
      if (await dir.exists()) {
        _whatsAppStatusDir = dir;
        break;
      }
    }
  }

  Future<void> _initializeAppDirectories() async {
    final appDir = await getExternalStorageDirectory();
    if (appDir == null) return;

    // Saved status directory
    _savedStatusDir = Directory('${appDir.path}/${AppConstants.savedFolderName}');
    if (!await _savedStatusDir!.exists()) {
      await _savedStatusDir!.create(recursive: true);
    }

    // Cache directory
    _cacheDir = Directory('${appDir.path}/${AppConstants.cacheFolderName}');
    if (!await _cacheDir!.exists()) {
      await _cacheDir!.create(recursive: true);
    }

    // Thumbnails directory
    _thumbnailsDir = Directory('${appDir.path}/${AppConstants.thumbnailsFolderName}');
    if (!await _thumbnailsDir!.exists()) {
      await _thumbnailsDir!.create(recursive: true);
    }
  }

  bool get hasWhatsAppStatus => _whatsAppStatusDir != null;

  Directory? get whatsAppStatusDir => _whatsAppStatusDir;
  Directory? get savedStatusDir => _savedStatusDir;
  Directory? get cacheDir => _cacheDir;
  Directory? get thumbnailsDir => _thumbnailsDir;

  Future<List<StatusItem>> getWhatsAppStatuses() async {
    if (_whatsAppStatusDir == null || !await _whatsAppStatusDir!.exists()) {
      return [];
    }

    final List<StatusItem> statuses = [];

    try {
      final files = _whatsAppStatusDir!.listSync();
      
      for (final file in files) {
        if (file is File) {
          final extension = file.path.split('.').last.toLowerCase();
          final isImage = AppConstants.imageExtensions.contains('.$extension');
          final isVideo = AppConstants.videoExtensions.contains('.$extension');

          if (isImage || isVideo) {
            final statusItem = StatusItem.fromFile(file, isVideo: isVideo);
            statuses.add(statusItem);
          }
        }
      }

      // Sort by timestamp (newest first)
      statuses.sort((a, b) => b.timestamp.compareTo(a.timestamp));
    } catch (e) {
      print('Error loading WhatsApp statuses: $e');
    }

    return statuses;
  }

  Future<List<StatusItem>> getSavedStatuses() async {
    if (_savedStatusDir == null || !await _savedStatusDir!.exists()) {
      return [];
    }

    final List<StatusItem> statuses = [];

    try {
      final files = _savedStatusDir!.listSync();
      
      for (final file in files) {
        if (file is File) {
          final extension = file.path.split('.').last.toLowerCase();
          final isImage = AppConstants.imageExtensions.contains('.$extension');
          final isVideo = AppConstants.videoExtensions.contains('.$extension');

          if (isImage || isVideo) {
            final statusItem = StatusItem.fromFile(file, isVideo: isVideo);
            statuses.add(statusItem.copyWith(isSaved: true));
          }
        }
      }

      statuses.sort((a, b) => b.timestamp.compareTo(a.timestamp));
    } catch (e) {
      print('Error loading saved statuses: $e');
    }

    return statuses;
  }

  Future<bool> saveStatus(StatusItem status) async {
    if (_savedStatusDir == null) return false;

    try {
      final sourceFile = File(status.path);
      if (!await sourceFile.exists()) return false;

      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final newFileName = 'status_$timestamp.${status.extension}';
      final destPath = '${_savedStatusDir!.path}/$newFileName';

      await sourceFile.copy(destPath);
      return true;
    } catch (e) {
      print('Error saving status: $e');
      return false;
    }
  }

  Future<bool> deleteStatus(StatusItem status) async {
    try {
      final file = File(status.path);
      if (await file.exists()) {
        await file.delete();
        return true;
      }
      return false;
    } catch (e) {
      print('Error deleting status: $e');
      return false;
    }
  }

  Future<String?> generateThumbnail(String videoPath) async {
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
      print('Error generating thumbnail: $e');
      return null;
    }
  }

  bool isImage(String path) {
    final extension = path.split('.').last.toLowerCase();
    return AppConstants.imageExtensions.contains('.$extension');
  }

  bool isVideo(String path) {
    final extension = path.split('.').last.toLowerCase();
    return AppConstants.videoExtensions.contains('.$extension');
  }

  Future<int> getDirectorySize(Directory directory) async {
    int size = 0;
    try {
      if (await directory.exists()) {
        await for (final file in directory.list(recursive: true)) {
          if (file is File) {
            size += await file.length();
          }
        }
      }
    } catch (e) {
      print('Error calculating directory size: $e');
    }
    return size;
  }
}
