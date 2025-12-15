import 'dart:io';
import 'package:flutter/material.dart';
import '../config/theme.dart';
import '../models/status_item.dart';

class StatusCard extends StatelessWidget {
  final StatusItem status;
  final VoidCallback? onTap;
  final VoidCallback? onSave;
  final VoidCallback? onDelete;
  final VoidCallback? onShare;
  final bool showSave;
  final bool showDelete;
  final bool showShare;

  const StatusCard({
    super.key,
    required this.status,
    this.onTap,
    this.onSave,
    this.onDelete,
    this.onShare,
    this.showSave = true,
    this.showDelete = false,
    this.showShare = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Thumbnail
            AspectRatio(
              aspectRatio: 1,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  // Image
                  _buildThumbnail(),
                  
                  // Video indicator
                  if (status.isVideo)
                    Positioned(
                      top: 8,
                      left: 8,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.7),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.play_arrow_rounded,
                              color: Colors.white,
                              size: 16,
                            ),
                            SizedBox(width: 4),
                            Text(
                              'Video',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  
                  // Cached indicator
                  if (status.isCached)
                    Positioned(
                      top: 8,
                      right: 8,
                      child: Container(
                        padding: const EdgeInsets.all(4),
                        decoration: BoxDecoration(
                          color: AppColors.primaryGreen,
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: const Icon(
                          Icons.history_rounded,
                          color: Colors.white,
                          size: 16,
                        ),
                      ),
                    ),
                ],
              ),
            ),
            
            // Info and actions
            Padding(
              padding: const EdgeInsets.all(8),
              child: Row(
                children: [
                  // File info
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          status.formattedDate,
                          style: TextStyle(
                            fontSize: 12,
                            color: isDark 
                                ? AppColors.darkTextSecondary 
                                : AppColors.lightTextSecondary,
                          ),
                        ),
                        Text(
                          status.formattedSize,
                          style: TextStyle(
                            fontSize: 11,
                            color: isDark 
                                ? AppColors.darkTextSecondary 
                                : AppColors.lightTextSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  
                  // Action buttons
                  if (showSave)
                    IconButton(
                      icon: const Icon(Icons.download_rounded),
                      iconSize: 20,
                      color: AppColors.primaryGreen,
                      onPressed: onSave,
                      tooltip: 'Save',
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(
                        minWidth: 32,
                        minHeight: 32,
                      ),
                    ),
                  if (showShare)
                    IconButton(
                      icon: const Icon(Icons.share_rounded),
                      iconSize: 20,
                      color: AppColors.primaryGreen,
                      onPressed: onShare,
                      tooltip: 'Share',
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(
                        minWidth: 32,
                        minHeight: 32,
                      ),
                    ),
                  if (showDelete)
                    IconButton(
                      icon: const Icon(Icons.delete_outline_rounded),
                      iconSize: 20,
                      color: AppColors.error,
                      onPressed: onDelete,
                      tooltip: 'Delete',
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints(
                        minWidth: 32,
                        minHeight: 32,
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildThumbnail() {
    final file = File(status.thumbnailPath ?? status.path);
    
    if (file.existsSync()) {
      return Image.file(
        file,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => _buildPlaceholder(),
      );
    }
    
    return _buildPlaceholder();
  }

  Widget _buildPlaceholder() {
    return Container(
      color: AppColors.darkSurface,
      child: Icon(
        status.isVideo ? Icons.videocam_rounded : Icons.image_rounded,
        size: 48,
        color: AppColors.darkTextSecondary,
      ),
    );
  }
}
