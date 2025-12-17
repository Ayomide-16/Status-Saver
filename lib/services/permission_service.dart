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
  static const String _storageGrantedKey = 'storage_permission_granted';
  
  Box? _settingsBox;
  int? _cachedSdkVersion;

  Future<void> initialize() async {
    _settingsBox = await Hive.openBox(_permissionBoxName);
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
    return _settingsBox?.get(_storageGrantedKey, defaultValue: false) ?? false;
  }

  /// Store that permission was granted
  Future<void> _markPermissionGranted() async {
    await _settingsBox?.put(_storageGrantedKey, true);
  }

  Future<bool> checkStoragePermission() async {
    if (!Platform.isAndroid) return true;

    final sdkVersion = await _getAndroidSdkVersion();
    
    // For Android 11+ using SAF, we don't need storage permission
    // SAF access is handled separately
    if (sdkVersion >= 30) {
      // Check if we previously granted basic permission
      if (_wasPermissionGranted()) {
        debugPrint('Permission: Previously granted (Android 11+)');
        return true;
      }
      // For Android 11+, we actually check if basic READ_EXTERNAL_STORAGE is granted
      // or if we've marked it as granted before
      final status = await Permission.storage.status;
      if (status.isGranted) {
        await _markPermissionGranted();
        return true;
      }
      return false;
    } else {
      // Android 10 and below - need storage permission
      final status = await Permission.storage.status;
      if (status.isGranted) {
        await _markPermissionGranted();
      }
      return status.isGranted;
    }
  }

  Future<bool> requestStoragePermission() async {
    if (!Platform.isAndroid) return true;

    final sdkVersion = await _getAndroidSdkVersion();

    if (sdkVersion >= 30) {
      // Android 11+ - Request basic storage or just mark as done
      // The actual access is via SAF
      final status = await Permission.storage.request();
      if (status.isGranted || status.isDenied) {
        // Even if denied, on Android 11+ we'll use SAF
        await _markPermissionGranted();
        debugPrint('Permission: Marked as granted (Android 11+, using SAF)');
        return true;
      }
      return false;
    } else {
      // Android 10 and below
      final status = await Permission.storage.request();
      if (status.isGranted) {
        await _markPermissionGranted();
      }
      return status.isGranted;
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
