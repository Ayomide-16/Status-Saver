import 'package:hive_flutter/hive_flutter.dart';
import 'package:flutter/foundation.dart';

/// Service to track downloaded statuses persistently
class DownloadTracker {
  static final DownloadTracker _instance = DownloadTracker._internal();
  factory DownloadTracker() => _instance;
  DownloadTracker._internal();

  static const String _boxName = 'downloaded_statuses';
  Box<String>? _box;

  Future<void> initialize() async {
    _box = await Hive.openBox<String>(_boxName);
    debugPrint('DownloadTracker: Initialized with ${_box?.length ?? 0} entries');
  }

  /// Check if a status has been downloaded (by filename)
  bool isDownloaded(String fileName) {
    if (_box == null) return false;
    return _box!.containsKey(fileName);
  }

  /// Mark a status as downloaded
  Future<void> markAsDownloaded(String fileName) async {
    if (_box == null) return;
    final timestamp = DateTime.now().toIso8601String();
    await _box!.put(fileName, timestamp);
    debugPrint('DownloadTracker: Marked $fileName as downloaded');
  }

  /// Remove download mark (if user deletes the saved status)
  Future<void> removeDownloadMark(String fileName) async {
    if (_box == null) return;
    await _box!.delete(fileName);
  }

  /// Get all downloaded filenames
  List<String> getAllDownloaded() {
    if (_box == null) return [];
    return _box!.keys.cast<String>().toList();
  }

  /// Get total count of downloaded statuses
  int get downloadedCount => _box?.length ?? 0;
}
