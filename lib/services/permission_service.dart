import 'dart:io';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';

class PermissionService {
  static final PermissionService _instance = PermissionService._internal();
  factory PermissionService() => _instance;
  PermissionService._internal();

  Future<int> _getAndroidSdkVersion() async {
    if (!Platform.isAndroid) return 0;
    final deviceInfo = DeviceInfoPlugin();
    final androidInfo = await deviceInfo.androidInfo;
    return androidInfo.version.sdkInt;
  }

  Future<bool> checkStoragePermission() async {
    if (!Platform.isAndroid) return true;

    final sdkVersion = await _getAndroidSdkVersion();

    if (sdkVersion >= 33) {
      // Android 13+ - Check for media permissions
      final photos = await Permission.photos.status;
      final videos = await Permission.videos.status;
      return photos.isGranted && videos.isGranted;
    } else if (sdkVersion >= 30) {
      // Android 11-12 - Check for manage external storage
      final status = await Permission.manageExternalStorage.status;
      return status.isGranted;
    } else {
      // Android 10 and below
      final status = await Permission.storage.status;
      return status.isGranted;
    }
  }

  Future<bool> requestStoragePermission() async {
    if (!Platform.isAndroid) return true;

    final sdkVersion = await _getAndroidSdkVersion();

    if (sdkVersion >= 33) {
      // Android 13+ - Request media permissions
      final statuses = await [
        Permission.photos,
        Permission.videos,
      ].request();

      return statuses[Permission.photos]!.isGranted &&
          statuses[Permission.videos]!.isGranted;
    } else if (sdkVersion >= 30) {
      // Android 11-12 - Request manage external storage
      final status = await Permission.manageExternalStorage.request();
      return status.isGranted;
    } else {
      // Android 10 and below
      final status = await Permission.storage.request();
      return status.isGranted;
    }
  }

  Future<bool> isPermanentlyDenied() async {
    if (!Platform.isAndroid) return false;

    final sdkVersion = await _getAndroidSdkVersion();

    if (sdkVersion >= 33) {
      final photos = await Permission.photos.status;
      final videos = await Permission.videos.status;
      return photos.isPermanentlyDenied || videos.isPermanentlyDenied;
    } else if (sdkVersion >= 30) {
      final status = await Permission.manageExternalStorage.status;
      return status.isPermanentlyDenied;
    } else {
      final status = await Permission.storage.status;
      return status.isPermanentlyDenied;
    }
  }

  Future<void> openSettings() async {
    await openAppSettings();
  }

  Future<PermissionStatus> getStoragePermissionStatus() async {
    final sdkVersion = await _getAndroidSdkVersion();

    if (sdkVersion >= 33) {
      final photos = await Permission.photos.status;
      return photos;
    } else if (sdkVersion >= 30) {
      return await Permission.manageExternalStorage.status;
    } else {
      return await Permission.storage.status;
    }
  }
}
