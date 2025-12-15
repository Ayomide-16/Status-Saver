import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/theme.dart';
import '../providers/status_provider.dart';
import '../widgets/status_grid.dart';
import '../widgets/custom_widgets.dart';
import '../widgets/permission_dialog.dart';
import 'status_viewer.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isSavingAll = false;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    
    // Initialize provider
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<StatusProvider>().initialize();
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _handleRefresh() async {
    await context.read<StatusProvider>().refreshLiveStatuses();
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
                  content: Text(
                    success ? 'Status saved successfully!' : 'Failed to save status',
                  ),
                  backgroundColor: success ? AppColors.success : AppColors.error,
                ),
              );
            }
          },
        ),
      ),
    );

    // Cache the status when viewed
    context.read<StatusProvider>().cacheStatus(status);
  }

  Future<void> _saveStatus(status) async {
    final provider = context.read<StatusProvider>();
    final success = await provider.saveStatus(status);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            success ? 'Status saved successfully!' : 'Failed to save status',
          ),
          backgroundColor: success ? AppColors.success : AppColors.error,
        ),
      );
    }
  }

  Future<void> _saveAllStatuses() async {
    final provider = context.read<StatusProvider>();
    
    // Get current tab statuses
    final statuses = _tabController.index == 0 
        ? provider.liveImages 
        : provider.liveVideos;
    
    if (statuses.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('No ${_tabController.index == 0 ? 'images' : 'videos'} to save'),
            backgroundColor: AppColors.warning,
          ),
        );
      }
      return;
    }

    // Show confirmation dialog
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surfaceDark,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Row(
          children: [
            Icon(Icons.download_rounded, color: AppColors.accent),
            const SizedBox(width: 12),
            Text(
              'Save All?',
              style: TextStyle(color: AppColors.textPrimary),
            ),
          ],
        ),
        content: Text(
          'This will save all ${statuses.length} ${_tabController.index == 0 ? 'images' : 'videos'} to your device.',
          style: TextStyle(color: AppColors.textSecondary),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('Cancel', style: TextStyle(color: AppColors.textSecondary)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text('Save All', style: TextStyle(color: AppColors.accent)),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() {
      _isSavingAll = true;
    });

    int successCount = 0;
    int failCount = 0;

    for (final status in statuses) {
      final success = await provider.saveStatus(status);
      if (success) {
        successCount++;
      } else {
        failCount++;
      }
    }

    setState(() {
      _isSavingAll = false;
    });

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            failCount == 0
                ? 'All $successCount statuses saved successfully!'
                : '$successCount saved, $failCount failed',
          ),
          backgroundColor: failCount == 0 ? AppColors.success : AppColors.warning,
          duration: const Duration(seconds: 3),
        ),
      );
    }
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
              // Show permission dialog if not granted
              if (!provider.hasPermission && provider.isInitialized) {
                return PermissionDialog(
                  onRequestPermission: () => provider.requestPermission(),
                  onOpenSettings: () => provider.openSettings(),
                );
              }

              // Show no WhatsApp dialog
              if (provider.isInitialized && !provider.hasWhatsApp && provider.hasPermission) {
                return const NoWhatsAppDialog();
              }

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
                      provider.liveImages.length,
                      provider.liveVideos.length,
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
                            statuses: provider.liveImages,
                            isLoading: provider.isLoading,
                            onTap: _openStatus,
                            onSave: _saveStatus,
                            showCacheIndicator: true,
                            emptyMessage: 'No image statuses found',
                            emptyIcon: Icons.image_outlined,
                          ),
                        ),

                        // Videos Tab
                        RefreshIndicator(
                          onRefresh: _handleRefresh,
                          color: AppColors.accent,
                          backgroundColor: AppColors.surfaceDark,
                          child: StatusGrid(
                            statuses: provider.liveVideos,
                            isLoading: provider.isLoading,
                            onTap: _openStatus,
                            onSave: _saveStatus,
                            showCacheIndicator: true,
                            emptyMessage: 'No video statuses found',
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
      // Save All FAB
      floatingActionButton: Consumer<StatusProvider>(
        builder: (context, provider, _) {
          if (!provider.hasPermission || !provider.isInitialized) {
            return const SizedBox.shrink();
          }

          final hasStatuses = _tabController.index == 0
              ? provider.liveImages.isNotEmpty
              : provider.liveVideos.isNotEmpty;

          if (!hasStatuses) return const SizedBox.shrink();

          return FloatingActionButton.extended(
            onPressed: _isSavingAll ? null : _saveAllStatuses,
            backgroundColor: AppColors.accent,
            icon: _isSavingAll
                ? SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: AppColors.backgroundDark,
                    ),
                  )
                : Icon(Icons.download_rounded, color: AppColors.backgroundDark),
            label: Text(
              _isSavingAll ? 'Saving...' : 'Save All',
              style: TextStyle(
                color: AppColors.backgroundDark,
                fontWeight: FontWeight.w600,
              ),
            ),
          );
        },
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
                      AppColors.primaryGradient.createShader(bounds),
                  child: const Text(
                    'Live Status',
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ),
                Text(
                  '${provider.liveStatuses.length} statuses available',
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),

          // Refresh Button
          IconButton(
            onPressed: provider.isLoading ? null : _handleRefresh,
            icon: provider.isLoading
                ? SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: AppColors.accent,
                    ),
                  )
                : Icon(
                    Icons.refresh_rounded,
                    color: AppColors.textSecondary,
                  ),
          ),
        ],
      ),
    );
  }
}
