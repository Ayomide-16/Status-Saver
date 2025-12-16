import 'dart:io';
import 'package:flutter/foundation.dart';
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
    
    debugPrint('SAF Service initialized. Has access: $hasAccess, URI: $_grantedUri');
    
    // Verify the URI is still valid
    if (_grantedUri != null) {
      try {
        final hasPermission = await _safUtil.hasPersistedPermission(_grantedUri!);
        debugPrint('SAF permission check: $hasPermission');
        if (!hasPermission) {
          _grantedUri = null;
          await _settingsBox?.delete(_uriKey);
        }
      } catch (e) {
        debugPrint('SAF permission check error: $e');
        _grantedUri = null;
        await _settingsBox?.delete(_uriKey);
      }
    }
  }

  /// Request user to pick WhatsApp status folder
  Future<bool> requestFolderAccess() async {
    try {
      debugPrint('Requesting folder access via SAF picker...');
      
      // Open directory picker
      final directory = await _safUtil.pickDirectory(
        writePermission: false,
      );
      
      if (directory != null) {
        _grantedUri = directory.uri;
        await _settingsBox?.put(_uriKey, _grantedUri);
        debugPrint('SAF folder access granted: $_grantedUri');
        return true;
      }
      debugPrint('SAF folder picker cancelled');
      return false;
    } catch (e) {
      debugPrint('Error requesting folder access: $e');
      return false;
    }
  }

  /// List files from the granted folder
  Future<List<SafDocumentFile>> listStatusFiles() async {
    if (!hasAccess) {
      debugPrint('SAF: No access to list files');
      return [];
    }
    
    try {
      debugPrint('SAF: Listing files from $_grantedUri');
      final files = await _safUtil.list(_grantedUri!);
      debugPrint('SAF: Found ${files.length} total files');
      
      // Filter to only images and videos
      final filtered = files.where((file) {
        if (file.isDir) return false;
        final name = file.name.toLowerCase();
        final isImage = AppConstants.imageExtensions.any((ext) => name.endsWith(ext));
        final isVideo = AppConstants.videoExtensions.any((ext) => name.endsWith(ext));
        return isImage || isVideo;
      }).toList();
      
      debugPrint('SAF: ${filtered.length} media files after filtering');
      return filtered;
    } catch (e) {
      debugPrint('Error listing status files: $e');
      return [];
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
        debugPrint('SAF: Using cached file: $cachedPath');
        return cachedPath;
      }
      
      // Create cache directory if needed
      final dir = Directory(cacheDir);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }
      
      debugPrint('SAF: Copying file ${file.name} to cache...');
      
      // Use copyTo to copy file to local storage
      try {
        await _safUtil.copyTo(file.uri, false, cachedPath);
        
        // Verify the file was copied
        if (await cachedFile.exists()) {
          debugPrint('SAF: File copied successfully to $cachedPath');
          return cachedPath;
        } else {
          debugPrint('SAF: copyTo completed but file not found at $cachedPath');
        }
      } catch (copyError) {
        debugPrint('SAF: copyTo failed: $copyError');
      }
      
      // The copyTo method should work, if it doesn't we just return null
      // as there's no good alternative in saf_util 0.11.0
      debugPrint('SAF: All copy methods failed for ${file.name}');
      return null;
    } catch (e) {
      debugPrint('Error getting cached copy: $e');
      return null;
    }
  }

  /// Clear the stored URI permission
  Future<void> clearAccess() async {
    _grantedUri = null;
    await _settingsBox?.delete(_uriKey);
  }
  
  /// Clear cached status files
  Future<void> clearCache() async {
    try {
      final tempDir = await getTemporaryDirectory();
      final cacheDir = Directory('${tempDir.path}/status_cache');
      if (await cacheDir.exists()) {
        await cacheDir.delete(recursive: true);
        debugPrint('SAF: Cache cleared');
      }
    } catch (e) {
      debugPrint('Error clearing cache: $e');
    }
  }
}
