import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';

class PermissionService {
  static final PermissionService _instance = PermissionService._internal();
  factory PermissionService() => _instance;
  PermissionService._internal();

  static const String _permissionBoxName = 'permission_settings';
  static const String _permissionGrantedKey = 'permission_granted_v2';
  static const String _firstLaunchKey = 'first_launch_completed';
  
  Box? _settingsBox;
  int? _cachedSdkVersion;

  Future<void> initialize() async {
    _settingsBox = await Hive.openBox(_permissionBoxName);
    debugPrint('PermissionService: Initialized');
    debugPrint('PermissionService: Permission granted: ${_wasPermissionGranted()}');
    debugPrint('PermissionService: First launch completed: ${_isFirstLaunchCompleted()}');
  }

  Future<int> _getAndroidSdkVersion() async {
    if (_cachedSdkVersion != null) return _cachedSdkVersion!;
    if (!Platform.isAndroid) return 0;
    final deviceInfo = DeviceInfoPlugin();
    final androidInfo = await deviceInfo.androidInfo;
    _cachedSdkVersion = androidInfo.version.sdkInt;
    return _cachedSdkVersion!;
  }

  /// Check if permission was previously granted and stored
  bool _wasPermissionGranted() {
    return _settingsBox?.get(_permissionGrantedKey, defaultValue: false) ?? false;
  }

  /// Check if first launch has completed
  bool _isFirstLaunchCompleted() {
    return _settingsBox?.get(_firstLaunchKey, defaultValue: false) ?? false;
  }

  /// Store that permission was granted permanently
  Future<void> _markPermissionGranted() async {
    await _settingsBox?.put(_permissionGrantedKey, true);
    await _settingsBox?.put(_firstLaunchKey, true);
    debugPrint('PermissionService: Marked permission as granted permanently');
  }

  /// Check if we need to show permission dialog
  /// Returns true if permission is granted OR was previously granted
  Future<bool> checkStoragePermission() async {
    if (!Platform.isAndroid) return true;

    // If permission was previously granted and stored, return true immediately
    // This is the key - once granted, we never ask again
    if (_wasPermissionGranted()) {
      debugPrint('PermissionService: Previously granted, skipping check');
      return true;
    }

    final sdkVersion = await _getAndroidSdkVersion();
    
    // For Android 11+ using SAF, we don't strictly need storage permission
    // We just need to mark first launch as complete
    if (sdkVersion >= 30) {
      final status = await Permission.storage.status;
      if (status.isGranted) {
        await _markPermissionGranted();
        return true;
      }
      // Even if not granted, check if first launch completed
      // (user may have triggered SAF access which is sufficient)
      if (_isFirstLaunchCompleted()) {
        await _markPermissionGranted();
        return true;
      }
      return false;
    } else {
      // Android 10 and below - need storage permission
      final status = await Permission.storage.status;
      if (status.isGranted) {
        await _markPermissionGranted();
        return true;
      }
      return false;
    }
  }

  Future<bool> requestStoragePermission() async {
    if (!Platform.isAndroid) return true;

    final sdkVersion = await _getAndroidSdkVersion();

    if (sdkVersion >= 30) {
      // Android 11+ - Request storage (may or may not be needed, SAF is primary)
      await Permission.storage.request();
      // Mark as granted regardless - SAF is the actual access method
      await _markPermissionGranted();
      debugPrint('PermissionService: Android 11+ permission request complete');
      return true;
    } else {
      // Android 10 and below
      final status = await Permission.storage.request();
      if (status.isGranted) {
        await _markPermissionGranted();
        return true;
      }
      return false;
    }
  }

  Future<bool> isPermanentlyDenied() async {
    if (!Platform.isAndroid) return false;

    final sdkVersion = await _getAndroidSdkVersion();

    // Android 11+ doesn't need storage permission (uses SAF)
    if (sdkVersion >= 30) return false;

    final status = await Permission.storage.status;
    return status.isPermanentlyDenied;
  }

  Future<void> openSettings() async {
    await openAppSettings();
  }

  Future<PermissionStatus> getStoragePermissionStatus() async {
    return await Permission.storage.status;
  }
}
