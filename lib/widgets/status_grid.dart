import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import '../models/status_item.dart';
import '../config/theme.dart';
import 'status_card.dart';
import 'loading_shimmer.dart';

class StatusGrid extends StatelessWidget {
  final List<StatusItem> statuses;
  final bool isLoading;
  final Function(StatusItem) onTap;
  final Function(StatusItem)? onSave;
  final Function(StatusItem)? onDelete;
  final Function(StatusItem)? onShare;
  final bool showSaveButton;
  final bool showDeleteButton;
  final bool showCacheIndicator;
  final String? Function(StatusItem)? getCacheTimeLeft;
  final String emptyMessage;
  final IconData emptyIcon;

  const StatusGrid({
    super.key,
    required this.statuses,
    required this.isLoading,
    required this.onTap,
    this.onSave,
    this.onDelete,
    this.onShare,
    this.showSaveButton = true,
    this.showDeleteButton = false,
    this.showCacheIndicator = false,
    this.getCacheTimeLeft,
    this.emptyMessage = 'No status found',
    this.emptyIcon = Icons.photo_library_outlined,
  });

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const LoadingShimmerGrid();
    }

    if (statuses.isEmpty) {
      return _buildEmptyState();
    }

    return MasonryGridView.count(
      crossAxisCount: 2,
      mainAxisSpacing: 12,
      crossAxisSpacing: 12,
      padding: const EdgeInsets.all(16),
      itemCount: statuses.length,
      itemBuilder: (context, index) {
        final status = statuses[index];
        return StatusCard(
          status: status,
          onTap: () => onTap(status),
          onSave: onSave != null ? () => onSave!(status) : null,
          onDelete: onDelete != null ? () => onDelete!(status) : null,
          onShare: onShare != null ? () => onShare!(status) : null,
          showSaveButton: showSaveButton,
          showDeleteButton: showDeleteButton,
          showCacheIndicator: showCacheIndicator,
          cacheTimeLeft: getCacheTimeLeft?.call(status),
        );
      },
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.surfaceLight.withValues(alpha: 0.5),
              shape: BoxShape.circle,
            ),
            child: Icon(
              emptyIcon,
              size: 64,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 24),
          Text(
            emptyMessage,
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 16,
              fontWeight: FontWeight.w500,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            'Pull down to refresh',
            style: TextStyle(
              color: AppColors.textTertiary,
              fontSize: 12,
            ),
          ),
        ],
      ),
    );
  }
}
