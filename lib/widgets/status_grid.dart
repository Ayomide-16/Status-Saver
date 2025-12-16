import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import '../config/theme.dart';
import '../models/status_item.dart';
import 'status_card.dart';
import 'loading_shimmer.dart';

class StatusGrid extends StatelessWidget {
  final List<StatusItem> statuses;
  final bool isLoading;
  final Function(StatusItem)? onTap;
  final Function(StatusItem)? onSave;
  final Function(StatusItem)? onDelete;
  final Function(StatusItem)? onShare;
  final bool showSave;
  final bool showDelete;
  final bool showShare;
  final String emptyMessage;
  final IconData emptyIcon;

  const StatusGrid({
    super.key,
    required this.statuses,
    this.isLoading = false,
    this.onTap,
    this.onSave,
    this.onDelete,
    this.onShare,
    this.showSave = true,
    this.showDelete = false,
    this.showShare = false,
    this.emptyMessage = 'No statuses found',
    this.emptyIcon = Icons.inbox_rounded,
  });

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const LoadingShimmerGrid();
    }

    if (statuses.isEmpty) {
      return _buildEmptyState(context);
    }

    return MasonryGridView.count(
      crossAxisCount: 2,
      mainAxisSpacing: 8,
      crossAxisSpacing: 8,
      padding: const EdgeInsets.all(12),
      itemCount: statuses.length,
      itemBuilder: (context, index) {
        final status = statuses[index];
        return StatusCard(
          status: status,
          onTap: onTap != null ? () => onTap!(status) : null,
          onSave: onSave != null ? () => onSave!(status) : null,
          onDelete: onDelete != null ? () => onDelete!(status) : null,
          onShare: onShare != null ? () => onShare!(status) : null,
          showSave: showSave,
          showDelete: showDelete,
          showShare: showShare,
        );
      },
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    // Wrap in ListView to make it scrollable for RefreshIndicator
    return LayoutBuilder(
      builder: (context, constraints) {
        return ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(
              height: constraints.maxHeight,
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      emptyIcon,
                      size: 80,
                      color: isDark 
                          ? AppColors.darkTextSecondary 
                          : AppColors.lightTextSecondary,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      emptyMessage,
                      style: TextStyle(
                        fontSize: 16,
                        color: isDark 
                            ? AppColors.darkTextSecondary 
                            : AppColors.lightTextSecondary,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Pull down to refresh',
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark 
                            ? AppColors.darkTextSecondary 
                            : AppColors.lightTextSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}
