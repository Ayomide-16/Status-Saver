import 'dart:io';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:saf_util/saf_util.dart';
import 'package:saf_util/saf_util_platform_interface.dart';
import 'package:path_provider/path_provider.dart';
import '../config/constants.dart';

/// Service to handle Storage Access Framework for Android 11+
class SafService {
  static final SafService _instance = SafService._internal();
  factory SafService() => _instance;
  SafService._internal();

  static const String _safBoxName = 'saf_settings';
  static const String _uriKey = 'whatsapp_status_uri';
  
  Box? _settingsBox;
  String? _grantedUri;
  final _safUtil = SafUtil();
  
  bool get hasAccess => _grantedUri != null && _grantedUri!.isNotEmpty;
  String? get grantedUri => _grantedUri;

  Future<void> initialize() async {
    _settingsBox = await Hive.openBox(_safBoxName);
    _grantedUri = _settingsBox?.get(_uriKey);
    
    // Verify the URI is still valid
    if (_grantedUri != null) {
      try {
        final hasPermission = await _safUtil.hasPersistedPermission(_grantedUri!);
        if (!hasPermission) {
          _grantedUri = null;
          await _settingsBox?.delete(_uriKey);
        }
      } catch (e) {
        _grantedUri = null;
        await _settingsBox?.delete(_uriKey);
      }
    }
  }

  /// Request user to pick WhatsApp status folder
  Future<bool> requestFolderAccess() async {
    try {
      // Open directory picker
      final directory = await _safUtil.pickDirectory(writePermission: false);
      
      if (directory != null) {
        _grantedUri = directory.uri;
        await _settingsBox?.put(_uriKey, _grantedUri);
        return true;
      }
      return false;
    } catch (e) {
      print('Error requesting folder access: $e');
      return false;
    }
  }

  /// List files from the granted folder
  Future<List<SafDocumentFile>> listStatusFiles() async {
    if (!hasAccess) return [];
    
    try {
      final files = await _safUtil.list(_grantedUri!);
      
      // Filter to only images and videos
      return files.where((file) {
        if (file.isDir) return false;
        final name = file.name.toLowerCase();
        final isImage = AppConstants.imageExtensions.any((ext) => name.endsWith(ext));
        final isVideo = AppConstants.videoExtensions.any((ext) => name.endsWith(ext));
        return isImage || isVideo;
      }).toList();
    } catch (e) {
      print('Error listing status files: $e');
      return [];
    }
  }

  /// Copy a file from SAF to local storage
  Future<String?> copyToLocal(SafDocumentFile file, String destDir) async {
    try {
      // Use copyTo method to copy file to local storage
      final destPath = '$destDir/${file.name}';
      
      // Create destination directory if needed
      final dir = Directory(destDir);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }
      
      // Copy using SAF util
      await _safUtil.copyTo(file.uri, false, destPath);
      
      // Check if file was created successfully
      final destFile = File(destPath);
      if (await destFile.exists()) {
        return destPath;
      }
      return null;
    } catch (e) {
      print('Error copying file: $e');
      return null;
    }
  }

  /// Get a cached copy of a status file for display
  Future<String?> getCachedCopy(SafDocumentFile file) async {
    try {
      final tempDir = await getTemporaryDirectory();
      final cacheDir = '${tempDir.path}/status_cache';
      final cachedPath = '$cacheDir/${file.name}';
      
      // Check if already cached
      final cachedFile = File(cachedPath);
      if (await cachedFile.exists()) {
        return cachedPath;
      }
      
      // Copy file
      return await copyToLocal(file, cacheDir);
    } catch (e) {
      print('Error getting cached copy: $e');
      return null;
    }
  }

  /// Clear the stored URI permission
  Future<void> clearAccess() async {
    _grantedUri = null;
    await _settingsBox?.delete(_uriKey);
  }
}
