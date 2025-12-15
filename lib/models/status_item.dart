import 'dart:io';
import 'package:hive/hive.dart';

part 'status_item.g.dart';

@HiveType(typeId: 0)
class StatusItem extends HiveObject {
  @HiveField(0)
  final String path;
  
  @HiveField(1)
  final String name;
  
  @HiveField(2)
  final bool isVideo;
  
  @HiveField(3)
  final DateTime timestamp;
  
  @HiveField(4)
  final int size;
  
  @HiveField(5)
  String? thumbnailPath;
  
  @HiveField(6)
  final String? originalPath;
  
  @HiveField(7)
  bool isCached;
  
  @HiveField(8)
  bool isSaved;

  StatusItem({
    required this.path,
    required this.name,
    required this.isVideo,
    required this.timestamp,
    required this.size,
    this.thumbnailPath,
    this.originalPath,
    this.isCached = false,
    this.isSaved = false,
  });

  factory StatusItem.fromFile(File file, {bool isVideo = false}) {
    final stat = file.statSync();
    return StatusItem(
      path: file.path,
      name: file.path.split(Platform.pathSeparator).last,
      isVideo: isVideo,
      timestamp: stat.modified,
      size: stat.size,
    );
  }

  String get formattedSize {
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  String get formattedDate {
    final now = DateTime.now();
    final diff = now.difference(timestamp);
    
    if (diff.inMinutes < 1) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    
    return '${timestamp.day}/${timestamp.month}/${timestamp.year}';
  }

  String get extension => name.split('.').last.toLowerCase();

  bool get exists => File(path).existsSync();

  StatusItem copyWith({
    String? path,
    String? name,
    bool? isVideo,
    DateTime? timestamp,
    int? size,
    String? thumbnailPath,
    String? originalPath,
    bool? isCached,
    bool? isSaved,
  }) {
    return StatusItem(
      path: path ?? this.path,
      name: name ?? this.name,
      isVideo: isVideo ?? this.isVideo,
      timestamp: timestamp ?? this.timestamp,
      size: size ?? this.size,
      thumbnailPath: thumbnailPath ?? this.thumbnailPath,
      originalPath: originalPath ?? this.originalPath,
      isCached: isCached ?? this.isCached,
      isSaved: isSaved ?? this.isSaved,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is StatusItem && other.path == path;
  }

  @override
  int get hashCode => path.hashCode;
}
