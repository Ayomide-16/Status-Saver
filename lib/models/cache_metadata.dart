import 'package:hive/hive.dart';

part 'cache_metadata.g.dart';

@HiveType(typeId: 1)
class CacheMetadata extends HiveObject {
  @HiveField(0)
  final String originalPath;
  
  @HiveField(1)
  final String cachedPath;
  
  @HiveField(2)
  final DateTime cachedAt;
  
  @HiveField(3)
  final DateTime expiresAt;
  
  @HiveField(4)
  final bool isVideo;
  
  @HiveField(5)
  final int fileSize;
  
  @HiveField(6)
  final String fileName;
  
  @HiveField(7)
  String? thumbnailPath;

  CacheMetadata({
    required this.originalPath,
    required this.cachedPath,
    required this.cachedAt,
    required this.expiresAt,
    required this.isVideo,
    required this.fileSize,
    required this.fileName,
    this.thumbnailPath,
  });

  factory CacheMetadata.create({
    required String originalPath,
    required String cachedPath,
    required bool isVideo,
    required int fileSize,
    required String fileName,
    int cacheDurationDays = 7,
    String? thumbnailPath,
  }) {
    final now = DateTime.now();
    return CacheMetadata(
      originalPath: originalPath,
      cachedPath: cachedPath,
      cachedAt: now,
      expiresAt: now.add(Duration(days: cacheDurationDays)),
      isVideo: isVideo,
      fileSize: fileSize,
      fileName: fileName,
      thumbnailPath: thumbnailPath,
    );
  }

  bool get isExpired => DateTime.now().isAfter(expiresAt);

  Duration get remainingTime => expiresAt.difference(DateTime.now());
  
  int get daysUntilExpiry => isExpired ? 0 : remainingTime.inDays;

  String get formattedRemainingTime {
    if (isExpired) return 'Expired';
    
    final remaining = remainingTime;
    if (remaining.inDays > 0) {
      return '${remaining.inDays}d ${remaining.inHours % 24}h left';
    }
    if (remaining.inHours > 0) {
      return '${remaining.inHours}h ${remaining.inMinutes % 60}m left';
    }
    if (remaining.inMinutes > 0) {
      return '${remaining.inMinutes}m left';
    }
    return 'Expiring soon';
  }

  String get formattedCachedAt {
    final now = DateTime.now();
    final diff = now.difference(cachedAt);
    
    if (diff.inMinutes < 1) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    
    return '${cachedAt.day}/${cachedAt.month}/${cachedAt.year}';
  }

  String get formattedSize {
    if (fileSize < 1024) return '$fileSize B';
    if (fileSize < 1024 * 1024) return '${(fileSize / 1024).toStringAsFixed(1)} KB';
    return '${(fileSize / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  double get expiryProgress {
    final totalDuration = expiresAt.difference(cachedAt);
    final elapsed = DateTime.now().difference(cachedAt);
    return (elapsed.inSeconds / totalDuration.inSeconds).clamp(0.0, 1.0);
  }
}
