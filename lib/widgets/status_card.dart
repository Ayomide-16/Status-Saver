import 'dart:io';
import 'package:flutter/material.dart';
import '../config/theme.dart';
import '../models/status_item.dart';

class StatusCard extends StatelessWidget {
  final StatusItem status;
  final VoidCallback onTap;
  final VoidCallback? onSave;
  final VoidCallback? onDelete;
  final VoidCallback? onShare;
  final bool showSaveButton;
  final bool showDeleteButton;
  final bool showCacheIndicator;
  final String? cacheTimeLeft;

  const StatusCard({
    super.key,
    required this.status,
    required this.onTap,
    this.onSave,
    this.onDelete,
    this.onShare,
    this.showSaveButton = true,
    this.showDeleteButton = false,
    this.showCacheIndicator = false,
    this.cacheTimeLeft,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.3),
              blurRadius: 10,
              offset: const Offset(0, 5),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(16),
          child: Stack(
            fit: StackFit.expand,
            children: [
              // Thumbnail/Image
              _buildThumbnail(),

              // Gradient overlay
              Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.transparent,
                      Colors.black.withValues(alpha: 0.7),
                    ],
                    stops: const [0.5, 1.0],
                  ),
                ),
              ),

              // Video indicator
              if (status.isVideo) _buildVideoIndicator(),

              // Cached indicator
              if (showCacheIndicator && status.isCached) _buildCachedBadge(),

              // Cache time left
              if (cacheTimeLeft != null) _buildCacheTimeLeft(),

              // Bottom info row
              Positioned(
                left: 8,
                right: 8,
                bottom: 8,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    // File info
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            status.formattedDate,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 10,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                          Text(
                            status.formattedSize,
                            style: TextStyle(
                              color: Colors.white.withValues(alpha: 0.7),
                              fontSize: 9,
                            ),
                          ),
                        ],
                      ),
                    ),

                    // Action buttons
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (onShare != null)
                          _buildActionButton(
                            icon: Icons.share_rounded,
                            onTap: onShare!,
                            color: AppColors.accent,
                          ),
                        if (showDeleteButton && onDelete != null)
                          _buildActionButton(
                            icon: Icons.delete_rounded,
                            onTap: onDelete!,
                            color: AppColors.error,
                          ),
                        if (showSaveButton && onSave != null)
                          _buildActionButton(
                            icon: Icons.download_rounded,
                            onTap: onSave!,
                            color: AppColors.accent,
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildThumbnail() {
    if (status.isVideo) {
      // Check for thumbnail
      if (status.thumbnailPath != null) {
        final thumbnailFile = File(status.thumbnailPath!);
        if (thumbnailFile.existsSync()) {
          return Image.file(
            thumbnailFile,
            fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => _buildPlaceholder(),
          );
        }
      }
      return _buildVideoPlaceholder();
    }

    final file = File(status.path);
    if (file.existsSync()) {
      return Image.file(
        file,
        fit: BoxFit.cover,
        cacheWidth: 300,
        errorBuilder: (_, __, ___) => _buildPlaceholder(),
      );
    }
    return _buildPlaceholder();
  }

  Widget _buildVideoPlaceholder() {
    return Container(
      color: AppColors.surfaceLight,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.videocam_rounded,
              size: 40,
              color: AppColors.textSecondary,
            ),
            const SizedBox(height: 4),
            Text(
              status.formattedSize,
              style: TextStyle(
                color: AppColors.textSecondary,
                fontSize: 10,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPlaceholder() {
    return Container(
      color: AppColors.surfaceLight,
      child: Center(
        child: Icon(
          Icons.image_rounded,
          size: 40,
          color: AppColors.textSecondary,
        ),
      ),
    );
  }

  Widget _buildVideoIndicator() {
    return Positioned(
      top: 8,
      left: 8,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppColors.videoTabColor.withValues(alpha: 0.9),
          borderRadius: BorderRadius.circular(8),
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.play_circle_filled_rounded,
              size: 14,
              color: Colors.white,
            ),
            SizedBox(width: 4),
            Text(
              'VIDEO',
              style: TextStyle(
                color: Colors.white,
                fontSize: 9,
                fontWeight: FontWeight.bold,
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCachedBadge() {
    return Positioned(
      top: 8,
      right: 8,
      child: Container(
        padding: const EdgeInsets.all(6),
        decoration: BoxDecoration(
          color: AppColors.accent.withValues(alpha: 0.9),
          shape: BoxShape.circle,
        ),
        child: const Icon(
          Icons.cached_rounded,
          size: 12,
          color: Colors.white,
        ),
      ),
    );
  }

  Widget _buildCacheTimeLeft() {
    return Positioned(
      top: 8,
      right: 8,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppColors.warning.withValues(alpha: 0.9),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          cacheTimeLeft!,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 9,
            fontWeight: FontWeight.bold,
          ),
        ),
      ),
    );
  }

  Widget _buildActionButton({
    required IconData icon,
    required VoidCallback onTap,
    required Color color,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(left: 8),
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.2),
          shape: BoxShape.circle,
          border: Border.all(color: color.withValues(alpha: 0.5)),
        ),
        child: Icon(
          icon,
          size: 16,
          color: color,
        ),
      ),
    );
  }
}
