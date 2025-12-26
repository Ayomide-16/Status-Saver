import 'dart:io';
import 'package:flutter/foundation.dart';

import 'package:hive_flutter/hive_flutter.dart';
import 'package:saf_util/saf_util.dart';
import 'package:saf_util/saf_util_platform_interface.dart';
import 'package:path_provider/path_provider.dart';
import 'package:uri_to_file/uri_to_file.dart';
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
  bool _isInitialized = false;
  
  bool get hasAccess => _grantedUri != null && _grantedUri!.isNotEmpty;
  String? get grantedUri => _grantedUri;

  Future<void> initialize() async {
    if (_isInitialized) {
      debugPrint('SAF: Already initialized, hasAccess: $hasAccess');
      return;
    }
    
    try {
      debugPrint('SAF: ===== INITIALIZING =====');
      
      // Open Hive box
      _settingsBox = await Hive.openBox(_safBoxName);
      debugPrint('SAF: Hive box opened: ${_settingsBox?.name}');
      
      // Get stored URI
      _grantedUri = _settingsBox?.get(_uriKey);
      debugPrint('SAF: Retrieved URI from box: $_grantedUri');
      
      // If we have a stored URI, verify it's still valid by checking persisted permissions
      if (_grantedUri != null && _grantedUri!.isNotEmpty) {
        debugPrint('SAF: Checking if persisted permission is still valid...');
        try {
          // Try to list - if it fails, the permission was revoked
          final testList = await _safUtil.list(_grantedUri!);
          debugPrint('SAF: Permission valid - found ${testList.length} files');
        } catch (e) {
          debugPrint('SAF: Permission check failed: $e');
        }
      } else {
        debugPrint('SAF: No stored URI found');
      }
      
      _isInitialized = true;
      debugPrint('SAF: ===== INIT COMPLETE - hasAccess: $hasAccess =====');
    } catch (e, stack) {
      debugPrint('SAF: Initialize error: $e');
      debugPrint('SAF: Stack: $stack');
    }
  }

  /// Request user to pick WhatsApp status folder
  Future<bool> requestFolderAccess() async {
    try {
      debugPrint('SAF: Opening folder picker...');
      
      // saf_util handles persisted permissions automatically
      final directory = await _safUtil.pickDirectory(
        writePermission: false,
      );
      
      if (directory != null) {
        _grantedUri = directory.uri;
        debugPrint('SAF: Directory picked: $_grantedUri');
        
        // Ensure box is open
        if (_settingsBox == null || !_settingsBox!.isOpen) {
          _settingsBox = await Hive.openBox(_safBoxName);
        }
        
        // Save to box with explicit flush
        await _settingsBox?.put(_uriKey, _grantedUri);
        await _settingsBox?.flush();
        
        debugPrint('SAF: URI saved to Hive');
        
        // Verify it was saved
        final savedUri = _settingsBox?.get(_uriKey);
        debugPrint('SAF: Verification - saved URI: $savedUri');
        
        return true;
      }
      debugPrint('SAF: Folder picker cancelled');
      return false;
    } catch (e, stack) {
      debugPrint('SAF: Folder access error: $e');
      debugPrint('SAF: Stack: $stack');
      return false;
    }
  }

  /// List files from the granted folder
  Future<List<SafDocumentFile>> listStatusFiles() async {
    if (!hasAccess) {
      debugPrint('SAF: listStatusFiles - No access (URI: $_grantedUri)');
      return [];
    }
    
    try {
      debugPrint('SAF: Listing files from $_grantedUri');
      final files = await _safUtil.list(_grantedUri!);
      
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
      return [];
    }
  }

  /// Copy a content:// URI file to local storage using uri_to_file
  Future<bool> _copyContentUriToFile(String uri, String destPath) async {
    try {
      // Use uri_to_file to get a temporary File object from content URI
      final tempFile = await toFile(uri);
      
      // Now copy this temp file to our destination
      await tempFile.copy(destPath);
      
      // Check if success
      if (await File(destPath).exists()) {
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('SAF: Copy error using uri_to_file: $e');
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
      
      // Copy using uri_to_file logic
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
      
      // Copy using uri_to_file logic
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
