import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/theme.dart';
import '../providers/status_provider.dart';
import '../widgets/status_grid.dart';
import 'status_viewer.dart';

class SavedScreen extends StatefulWidget {
  const SavedScreen({super.key});

  @override
  State<SavedScreen> createState() => _SavedScreenState();
}

class _SavedScreenState extends State<SavedScreen>
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
    await context.read<StatusProvider>().refreshSavedStatuses();
  }

  void _openStatus(status) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => StatusViewer(
          status: status,
          showSave: false,
          showShare: true,
          showDelete: true,
          onShare: () => context.read<StatusProvider>().shareStatus(status),
          onDelete: () async {
            final confirmed = await showDialog<bool>(
              context: context,
              builder: (context) => AlertDialog(
                title: const Text('Delete Status?'),
                content: const Text('This will permanently delete this status.'),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: const Text('Cancel'),
                  ),
                  TextButton(
                    onPressed: () => Navigator.pop(context, true),
                    style: TextButton.styleFrom(foregroundColor: AppColors.error),
                    child: const Text('Delete'),
                  ),
                ],
              ),
            );
            if (confirmed == true && mounted) {
              await context.read<StatusProvider>().deleteSavedStatus(status);
              Navigator.pop(context);
            }
          },
        ),
      ),
    );
  }

  Future<void> _shareStatus(status) async {
    await context.read<StatusProvider>().shareStatus(status);
  }

  Future<void> _deleteStatus(status) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Status?'),
        content: const Text('This will permanently delete this status.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: AppColors.error),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    
    if (confirmed == true && mounted) {
      await context.read<StatusProvider>().deleteSavedStatus(status);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Status deleted'),
          backgroundColor: AppColors.success,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<StatusProvider>(
      builder: (context, provider, _) {
        return Scaffold(
          body: Column(
            children: [
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
                          Text('Images (${provider.savedImages.length})'),
                        ],
                      ),
                    ),
                    Tab(
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.videocam_rounded, size: 20),
                          const SizedBox(width: 8),
                          Text('Videos (${provider.savedVideos.length})'),
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
                        statuses: provider.savedImages,
                        isLoading: provider.isLoading,
                        onTap: _openStatus,
                        onShare: _shareStatus,
                        onDelete: _deleteStatus,
                        showSave: false,
                        showDelete: true,
                        showShare: true,
                        emptyMessage: 'No saved images',
                        emptyIcon: Icons.bookmark_outline_rounded,
                      ),
                    ),

                    // Videos Tab
                    RefreshIndicator(
                      onRefresh: _handleRefresh,
                      color: AppColors.primaryGreen,
                      child: StatusGrid(
                        statuses: provider.savedVideos,
                        isLoading: provider.isLoading,
                        onTap: _openStatus,
                        onShare: _shareStatus,
                        onDelete: _deleteStatus,
                        showSave: false,
                        showDelete: true,
                        showShare: true,
                        emptyMessage: 'No saved videos',
                        emptyIcon: Icons.bookmark_outline_rounded,
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
