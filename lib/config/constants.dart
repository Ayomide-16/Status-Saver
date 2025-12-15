import 'dart:io';

class AppConstants {
  // App Info
  static const String appName = 'Status Saver';
  static const String appVersion = '1.0.0';
  
  // Cache Settings
  static const int cacheDurationDays = 7;
  static const Duration cacheCheckInterval = Duration(hours: 1);
  
  // File Extensions
  static const List<String> imageExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp'];
  static const List<String> videoExtensions = ['.mp4', '.3gp', '.mkv', '.avi', '.webm'];
  
  // Storage Paths
  static List<String> get whatsAppStatusPaths {
    if (Platform.isAndroid) {
      return [
        '/storage/emulated/0/WhatsApp/Media/.Statuses',
        '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses',
        '/storage/emulated/0/WhatsApp Business/Media/.Statuses',
        '/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses',
      ];
    }
    return [];
  }
  
  // Folder Names
  static const String savedFolderName = 'StatusSaver';
  static const String cacheFolderName = 'StatusCache';
  static const String thumbnailsFolderName = 'Thumbnails';
  
  // Grid Settings
  static const int gridCrossAxisCount = 2;
  static const double gridSpacing = 12.0;
  static const double gridChildAspectRatio = 0.85;
  
  // Animation Durations
  static const Duration animationFast = Duration(milliseconds: 200);
  static const Duration animationNormal = Duration(milliseconds: 300);
  static const Duration animationSlow = Duration(milliseconds: 500);
  
  // Thumbnail Settings
  static const int thumbnailWidth = 256;
  static const int thumbnailHeight = 256;
  static const int thumbnailQuality = 75;
  
  // Hive Box Names
  static const String cacheMetadataBox = 'cache_metadata';
  static const String settingsBox = 'settings';
}
