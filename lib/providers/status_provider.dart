import 'package:flutter/material.dart';
import '../models/status_item.dart';
import '../services/file_service.dart';
import '../services/cache_service.dart';
import '../services/storage_service.dart';
import '../services/permission_service.dart';
import '../services/download_tracker.dart';

class StatusProvider extends ChangeNotifier {
  final FileService _fileService = FileService();
  final CacheService _cacheService = CacheService();
  final StorageService _storageService = StorageService();
  final PermissionService _permissionService = PermissionService();
  final DownloadTracker _downloadTracker = DownloadTracker();

  List<StatusItem> _liveStatuses = [];
  List<StatusItem> _savedStatuses = [];
  List<StatusItem> _cachedStatuses = [];

  bool _isLoading = false;
  bool _hasPermission = false;
  bool _isInitialized = false;
  String? _errorMessage;

  // Getters
  List<StatusItem> get liveStatuses => _liveStatuses;
  List<StatusItem> get savedStatuses => _savedStatuses;
  List<StatusItem> get cachedStatuses => _cachedStatuses;
  
  List<StatusItem> get liveImages => _liveStatuses.where((s) => !s.isVideo).toList();
  List<StatusItem> get liveVideos => _liveStatuses.where((s) => s.isVideo).toList();
  
  List<StatusItem> get savedImages => _savedStatuses.where((s) => !s.isVideo).toList();
  List<StatusItem> get savedVideos => _savedStatuses.where((s) => s.isVideo).toList();
  
  List<StatusItem> get cachedImages => _cachedStatuses.where((s) => !s.isVideo).toList();
  List<StatusItem> get cachedVideos => _cachedStatuses.where((s) => s.isVideo).toList();

  bool get isLoading => _isLoading;
  bool get hasPermission => _hasPermission;
  bool get isInitialized => _isInitialized;
  String? get errorMessage => _errorMessage;
  
  bool get hasWhatsApp => _fileService.hasWhatsAppStatus;
  bool get needsSafAccess => _fileService.useSaf && !_fileService.hasSafAccess;
  bool get useSaf => _fileService.useSaf;

  /// Check if a status has been downloaded (by filename)
  bool isStatusDownloaded(String fileName) {
    return _downloadTracker.isDownloaded(fileName);
  }

  Future<void> initialize() async {
    if (_isInitialized) return;

    _isLoading = true;
    notifyListeners();

    try {
      // Initialize permission service first to load stored state
      await _permissionService.initialize();
      
      // Initialize download tracker
      await _downloadTracker.initialize();
      
      // Check permissions
      _hasPermission = await _permissionService.checkStoragePermission();

      if (_hasPermission) {
        // Initialize services
        await _fileService.initialize();
        await _cacheService.initialize();
        await _storageService.initialize();

        // Cleanup expired cache
        await _cacheService.cleanupExpiredCache();

        // Load all statuses
        await refreshAll();
      }

      _isInitialized = true;
      _errorMessage = null;
    } catch (e) {
      _errorMessage = 'Failed to initialize: $e';
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<bool> requestPermission() async {
    _hasPermission = await _permissionService.requestStoragePermission();
    
    if (_hasPermission) {
      await _fileService.initialize();
      await _cacheService.initialize();
      await _storageService.initialize();
      await refreshAll();
    }
    
    notifyListeners();
    return _hasPermission;
  }

  /// Request SAF folder access (Android 11+)
  Future<bool> requestSafAccess() async {
    final success = await _fileService.requestSafAccess();
    if (success) {
      await refreshLiveStatuses();
    }
    notifyListeners();
    return success;
  }

  Future<void> openSettings() async {
    await _permissionService.openSettings();
  }

  Future<void> refreshAll() async {
    await Future.wait([
      refreshLiveStatuses(),
      refreshSavedStatuses(),
      refreshCachedStatuses(),
    ]);
  }

  Future<void> refreshLiveStatuses() async {
    _isLoading = true;
    notifyListeners();

    try {
      _liveStatuses = await _fileService.getWhatsAppStatuses();
      
      // Auto-cache all live statuses to 7-day cache
      for (final status in _liveStatuses) {
        // Cache in background - don't await to keep UI responsive
        _autoCacheStatus(status);
      }
      
      // Mark cached and downloaded items
      for (var i = 0; i < _liveStatuses.length; i++) {
        final status = _liveStatuses[i];
        final isCached = _cacheService.isAlreadyCached(status.name);
        final isSaved = _downloadTracker.isDownloaded(status.name);
        
        if (isCached || isSaved) {
          _liveStatuses[i] = status.copyWith(
            isCached: isCached,
            isSaved: isSaved,
          );
        }
      }
      
      _errorMessage = null;
    } catch (e) {
      _errorMessage = 'Failed to load statuses: $e';
    }

    _isLoading = false;
    notifyListeners();
    
    // Refresh cached statuses list after auto-caching
    await refreshCachedStatuses();
  }
  
  /// Auto-cache a status to the 7-day cache
  Future<void> _autoCacheStatus(StatusItem status) async {
    try {
      await _cacheService.cacheStatusFromPath(
        localPath: status.path,
        fileName: status.name,
        isVideo: status.isVideo,
        fileSize: status.size,
        thumbnailPath: status.thumbnailPath,
      );
    } catch (e) {
      // Silent fail - caching is not critical
    }
  }

  Future<void> refreshSavedStatuses() async {
    try {
      _savedStatuses = await _storageService.getSavedStatuses();
    } catch (e) {
      print('Error loading saved statuses: $e');
    }
    notifyListeners();
  }

  Future<void> refreshCachedStatuses() async {
    try {
      _cachedStatuses = await _cacheService.getCachedStatuses();
      
      // Mark downloaded items in cached list
      for (var i = 0; i < _cachedStatuses.length; i++) {
        final status = _cachedStatuses[i];
        if (_downloadTracker.isDownloaded(status.name)) {
          _cachedStatuses[i] = status.copyWith(isSaved: true);
        }
      }
    } catch (e) {
      print('Error loading cached statuses: $e');
    }
    notifyListeners();
  }

  Future<bool> saveStatus(StatusItem status) async {
    try {
      final success = await _storageService.saveStatus(status);
      if (success) {
        // Mark as downloaded persistently
        await _downloadTracker.markAsDownloaded(status.name);
        
        // Update the status in all lists to show saved indicator
        _updateStatusSavedState(status.name, true);
        
        await refreshSavedStatuses();
      }
      return success;
    } catch (e) {
      print('Error saving status: $e');
      return false;
    }
  }

  /// Update saved state in all status lists
  void _updateStatusSavedState(String fileName, bool isSaved) {
    // Update in live statuses
    for (var i = 0; i < _liveStatuses.length; i++) {
      if (_liveStatuses[i].name == fileName) {
        _liveStatuses[i] = _liveStatuses[i].copyWith(isSaved: isSaved);
      }
    }
    
    // Update in cached statuses
    for (var i = 0; i < _cachedStatuses.length; i++) {
      if (_cachedStatuses[i].name == fileName) {
        _cachedStatuses[i] = _cachedStatuses[i].copyWith(isSaved: isSaved);
      }
    }
    
    notifyListeners();
  }

  Future<bool> deleteStatus(StatusItem status) async {
    try {
      final success = await _storageService.deleteStatus(status);
      if (success) {
        // Optionally remove download mark when deleting
        // await _downloadTracker.removeDownloadMark(status.name);
        await refreshSavedStatuses();
      }
      return success;
    } catch (e) {
      print('Error deleting status: $e');
      return false;
    }
  }

  /// Alias for deleteStatus (used by saved_screen.dart)
  Future<bool> deleteSavedStatus(StatusItem status) => deleteStatus(status);

  Future<void> shareStatus(StatusItem status) async {
    await _storageService.shareStatus(status);
  }

  Future<void> cacheStatus(StatusItem status, {String? thumbnailPath}) async {
    await _cacheService.cacheStatus(status, thumbnailPath: thumbnailPath);
    
    // Update the status in live list to show cached indicator
    final index = _liveStatuses.indexWhere((s) => s.path == status.path);
    if (index != -1) {
      _liveStatuses[index] = _liveStatuses[index].copyWith(isCached: true);
      notifyListeners();
    }
    
    await refreshCachedStatuses();
  }

  Future<void> removeFromCache(StatusItem status) async {
    await _cacheService.removeFromCache(status.path);
    await refreshCachedStatuses();
  }

  Future<void> clearAllCache() async {
    await _cacheService.clearAllCache();
    await refreshCachedStatuses();
  }

  Future<void> clearCache() async {
    await clearAllCache();
  }

  int get cachedCount => _cacheService.cachedItemsCount;
}
