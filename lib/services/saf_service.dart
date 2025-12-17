import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
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
  
  // Platform channel for native file operations
  static const MethodChannel _channel = MethodChannel('status_saver/saf');
  
  Box? _settingsBox;
  String? _grantedUri;
  final _safUtil = SafUtil();
  
  bool get hasAccess => _grantedUri != null && _grantedUri!.isNotEmpty;
  String? get grantedUri => _grantedUri;

  Future<void> initialize() async {
    try {
      _settingsBox = await Hive.openBox(_safBoxName);
      _grantedUri = _settingsBox?.get(_uriKey);
      
      debugPrint('SAF: Initialized');
      debugPrint('SAF: Box opened: ${_settingsBox != null}');
      debugPrint('SAF: Stored URI exists: ${_grantedUri != null && _grantedUri!.isNotEmpty}');
      if (_grantedUri != null) {
        debugPrint('SAF: URI value: $_grantedUri');
      }
    } catch (e) {
      debugPrint('SAF: Initialize error: $e');
    }
  }

  /// Request user to pick WhatsApp status folder
  Future<bool> requestFolderAccess() async {
    try {
      debugPrint('SAF: Opening folder picker...');
      
      final directory = await _safUtil.pickDirectory(
        writePermission: false,
      );
      
      if (directory != null) {
        _grantedUri = directory.uri;
        
        // Save to box with explicit flush
        await _settingsBox?.put(_uriKey, _grantedUri);
        await _settingsBox?.flush();
        
        debugPrint('SAF: Folder access granted and saved: $_grantedUri');
        
        // Verify it was saved
        final savedUri = _settingsBox?.get(_uriKey);
        debugPrint('SAF: Verification - saved URI: $savedUri');
        
        return true;
      }
      debugPrint('SAF: Folder picker cancelled');
      return false;
    } catch (e) {
      debugPrint('SAF: Folder access error: $e');
      return false;
    }
  }

  /// List files from the granted folder
  Future<List<SafDocumentFile>> listStatusFiles() async {
    if (!hasAccess) {
      debugPrint('SAF: No access - grantedUri is null or empty');
      return [];
    }
    
    try {
      debugPrint('SAF: Listing files from $_grantedUri');
      final files = await _safUtil.list(_grantedUri!);
      debugPrint('SAF: Total files found: ${files.length}');
      
      // Filter to only images and videos
      final filtered = files.where((file) {
        if (file.isDir) return false;
        final name = file.name.toLowerCase();
        final isImage = AppConstants.imageExtensions.any((ext) => name.endsWith(ext));
        final isVideo = AppConstants.videoExtensions.any((ext) => name.endsWith(ext));
        return isImage || isVideo;
      }).toList();
      
      debugPrint('SAF: Media files: ${filtered.length}');
      return filtered;
    } catch (e) {
      debugPrint('SAF: List error: $e');
      // If listing fails, don't clear the URI - it might be a temporary error
      return [];
    }
  }

  /// Copy a content:// URI file to local storage using native Android code
  Future<bool> _copyContentUriToFile(String uri, String destPath) async {
    try {
      final result = await _channel.invokeMethod<bool>('copyContentUriToFile', {
        'uri': uri,
        'destPath': destPath,
      });
      return result ?? false;
    } catch (e) {
      debugPrint('SAF: Native copy error: $e');
      return false;
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
      
      // Create cache directory
      final dir = Directory(cacheDir);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }
      
      // Use native Android method to copy content:// URI to local file
      final success = await _copyContentUriToFile(file.uri, cachedPath);
      
      if (success && await cachedFile.exists()) {
        return cachedPath;
      }
      
      return null;
    } catch (e) {
      debugPrint('SAF: Cache copy error for ${file.name}: $e');
      return null;
    }
  }

  /// Cache file to the weekly storage (7-day cache)
  Future<String?> cacheToWeeklyStorage(SafDocumentFile file, String destDir) async {
    try {
      final destPath = '$destDir/${file.name}';
      
      // Check if already exists
      final destFile = File(destPath);
      if (await destFile.exists()) {
        return destPath;
      }
      
      // Create directory if needed
      final dir = Directory(destDir);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }
      
      // Copy using native method
      final success = await _copyContentUriToFile(file.uri, destPath);
      
      if (success && await destFile.exists()) {
        return destPath;
      }
      
      return null;
    } catch (e) {
      debugPrint('SAF: Weekly cache error for ${file.name}: $e');
      return null;
    }
  }

  /// Clear stored URI (for testing/reset)
  Future<void> clearAccess() async {
    _grantedUri = null;
    await _settingsBox?.delete(_uriKey);
    await _settingsBox?.flush();
    debugPrint('SAF: Access cleared');
  }
}
