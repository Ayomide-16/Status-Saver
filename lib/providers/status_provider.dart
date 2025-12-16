import 'package:flutter/material.dart';
import '../models/status_item.dart';
import '../services/file_service.dart';
import '../services/cache_service.dart';
import '../services/storage_service.dart';
import '../services/permission_service.dart';

class StatusProvider extends ChangeNotifier {
  final FileService _fileService = FileService();
  final CacheService _cacheService = CacheService();
  final StorageService _storageService = StorageService();
  final PermissionService _permissionService = PermissionService();

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

  Future<void> initialize() async {
    if (_isInitialized) return;

    _isLoading = true;
    notifyListeners();

    try {
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
      
      // Mark cached items
      for (var i = 0; i < _liveStatuses.length; i++) {
        if (_cacheService.isAlreadyCached(_liveStatuses[i].name)) {
          _liveStatuses[i] = _liveStatuses[i].copyWith(isCached: true);
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
    } catch (e) {
      print('Error loading cached statuses: $e');
    }
    notifyListeners();
  }

  Future<bool> saveStatus(StatusItem status) async {
    try {
      final success = await _storageService.saveStatus(status);
      if (success) {
        await refreshSavedStatuses();
      }
      return success;
    } catch (e) {
      print('Error saving status: $e');
      return false;
    }
  }

  Future<bool> deleteStatus(StatusItem status) async {
    try {
      final success = await _storageService.deleteStatus(status);
      if (success) {
        await refreshSavedStatuses();
      }
      return success;
    } catch (e) {
      print('Error deleting status: $e');
      return false;
    }
  }

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
    _cachedStatuses = [];
    
    // Update live statuses to remove cached indicators
    for (var i = 0; i < _liveStatuses.length; i++) {
      _liveStatuses[i] = _liveStatuses[i].copyWith(isCached: false);
    }
    
    notifyListeners();
  }

  Future<String?> generateThumbnail(String videoPath) async {
    return await _fileService.generateThumbnail(videoPath);
  }

  int get cachedItemsCount => _cacheService.cachedItemsCount;

  Future<int> getCacheSize() async {
    return await _cacheService.getCacheSize();
  }

  String formatSize(int bytes) => _cacheService.formatSize(bytes);

  // Alias methods
  Future<void> clearCache() async => await clearAllCache();
  
  Future<bool> deleteSavedStatus(StatusItem status) async => await deleteStatus(status);
}
