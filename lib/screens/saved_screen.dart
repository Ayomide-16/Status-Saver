import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/theme.dart';
import '../providers/status_provider.dart';
import '../widgets/status_grid.dart';
import '../widgets/custom_widgets.dart';
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
    
    // Refresh saved statuses
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<StatusProvider>().refreshSavedStatuses();
    });
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
          showSaveButton: false,
          onShare: () => context.read<StatusProvider>().shareStatus(status),
        ),
      ),
    );
  }

  Future<void> _deleteStatus(status) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surfaceDark,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          'Delete Status?',
          style: TextStyle(color: AppColors.textPrimary),
        ),
        content: Text(
          'This action cannot be undone.',
          style: TextStyle(color: AppColors.textSecondary),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('Cancel', style: TextStyle(color: AppColors.textSecondary)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text('Delete', style: TextStyle(color: AppColors.error)),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      final provider = context.read<StatusProvider>();
      final success = await provider.deleteStatus(status);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              success ? 'Status deleted' : 'Failed to delete status',
            ),
            backgroundColor: success ? AppColors.success : AppColors.error,
          ),
        );
      }
    }
  }

  void _shareStatus(status) {
    context.read<StatusProvider>().shareStatus(status);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: AppColors.backgroundGradient,
        ),
        child: SafeArea(
          child: Consumer<StatusProvider>(
            builder: (context, provider, _) {
              return Column(
                children: [
                  // App Bar
                  _buildAppBar(provider),

                  // Tab Bar
                  CustomTabBar(
                    tabController: _tabController,
                    tabs: const ['Images', 'Videos'],
                    icons: const [Icons.image_rounded, Icons.videocam_rounded],
                    counts: [
                      provider.savedImages.length,
                      provider.savedVideos.length,
                    ],
                  ),

                  // Tab Content
                  Expanded(
                    child: TabBarView(
                      controller: _tabController,
                      children: [
                        // Images Tab
                        RefreshIndicator(
                          onRefresh: _handleRefresh,
                          color: AppColors.accent,
                          backgroundColor: AppColors.surfaceDark,
                          child: StatusGrid(
                            statuses: provider.savedImages,
                            isLoading: false,
                            onTap: _openStatus,
                            onDelete: _deleteStatus,
                            onShare: _shareStatus,
                            showSaveButton: false,
                            showDeleteButton: true,
                            emptyMessage: 'No saved images yet',
                            emptyIcon: Icons.bookmark_border_rounded,
                          ),
                        ),

                        // Videos Tab
                        RefreshIndicator(
                          onRefresh: _handleRefresh,
                          color: AppColors.accent,
                          backgroundColor: AppColors.surfaceDark,
                          child: StatusGrid(
                            statuses: provider.savedVideos,
                            isLoading: false,
                            onTap: _openStatus,
                            onDelete: _deleteStatus,
                            onShare: _shareStatus,
                            showSaveButton: false,
                            showDeleteButton: true,
                            emptyMessage: 'No saved videos yet',
                            emptyIcon: Icons.videocam_off_outlined,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildAppBar(StatusProvider provider) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          // Title
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ShaderMask(
                  shaderCallback: (bounds) =>
                      AppColors.accentGradient.createShader(bounds),
                  child: const Text(
                    'Saved',
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ),
                Text(
                  '${provider.savedStatuses.length} statuses saved',
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),

          // Info Icon
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.accent.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(
              Icons.bookmark_rounded,
              color: AppColors.accent,
            ),
          ),
        ],
      ),
    );
  }
}
