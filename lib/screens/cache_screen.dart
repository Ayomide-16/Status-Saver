import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/theme.dart';
import '../providers/status_provider.dart';
import '../widgets/status_grid.dart';
import 'status_viewer.dart';

class CacheScreen extends StatefulWidget {
  const CacheScreen({super.key});

  @override
  State<CacheScreen> createState() => _CacheScreenState();
}

class _CacheScreenState extends State<CacheScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _handleRefresh() async {
    await context.read<StatusProvider>().refreshCachedStatuses();
  }

  void _openStatus(status) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => StatusViewer(
          status: status,
          onSave: () async {
            final provider = context.read<StatusProvider>();
            final success = await provider.saveStatus(status);
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(success ? 'Status saved!' : 'Failed to save'),
                  backgroundColor: success ? AppColors.success : AppColors.error,
                ),
              );
            }
          },
        ),
      ),
    );
  }

  Future<void> _saveStatus(status) async {
    final provider = context.read<StatusProvider>();
    final success = await provider.saveStatus(status);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(success ? 'Status saved!' : 'Failed to save'),
          backgroundColor: success ? AppColors.success : AppColors.error,
        ),
      );
    }
  }

  Future<void> _clearCache() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Cache?'),
        content: const Text('This will delete all cached statuses. Saved statuses will not be affected.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: AppColors.error),
            child: const Text('Clear'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      await context.read<StatusProvider>().clearCache();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Cache cleared'),
          backgroundColor: AppColors.success,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    
    return Consumer<StatusProvider>(
      builder: (context, provider, _) {
        return Scaffold(
          body: Column(
            children: [
              // Cache Info Bar
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                color: isDark ? AppColors.darkSurface : AppColors.lightSurface,
                child: Row(
                  children: [
                    Icon(
                      Icons.info_outline_rounded,
                      size: 16,
                      color: isDark 
                          ? AppColors.darkTextSecondary 
                          : AppColors.lightTextSecondary,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Recent statuses are stored temporarily for 7 days. Save them to keep permanently.',
                        style: TextStyle(
                          fontSize: 12,
                          color: isDark 
                              ? AppColors.darkTextSecondary 
                              : AppColors.lightTextSecondary,
                        ),
                      ),
                    ),
                    TextButton(
                      onPressed: provider.cachedStatuses.isNotEmpty ? _clearCache : null,
                      child: const Text('Clear'),
                    ),
                  ],
                ),
              ),
              
              // Tab Bar
              Container(
                color: Theme.of(context).scaffoldBackgroundColor,
                child: TabBar(
                  controller: _tabController,
                  tabs: [
                    Tab(
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.image_rounded, size: 20),
                          const SizedBox(width: 8),
                          Text('Images (${provider.cachedImages.length})'),
                        ],
                      ),
                    ),
                    Tab(
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.videocam_rounded, size: 20),
                          const SizedBox(width: 8),
                          Text('Videos (${provider.cachedVideos.length})'),
                        ],
                      ),
                    ),
                  ],
                ),
              ),

              // Tab Content
              Expanded(
                child: TabBarView(
                  controller: _tabController,
                  children: [
                    // Images Tab
                    RefreshIndicator(
                      onRefresh: _handleRefresh,
                      color: AppColors.primaryGreen,
                      child: StatusGrid(
                        statuses: provider.cachedImages,
                        isLoading: provider.isLoading,
                        onTap: _openStatus,
                        onSave: _saveStatus,
                        emptyMessage: 'No cached images',
                        emptyIcon: Icons.history_rounded,
                      ),
                    ),

                    // Videos Tab
                    RefreshIndicator(
                      onRefresh: _handleRefresh,
                      color: AppColors.primaryGreen,
                      child: StatusGrid(
                        statuses: provider.cachedVideos,
                        isLoading: provider.isLoading,
                        onTap: _openStatus,
                        onSave: _saveStatus,
                        emptyMessage: 'No cached videos',
                        emptyIcon: Icons.history_rounded,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
