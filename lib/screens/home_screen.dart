import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/theme.dart';
import '../providers/status_provider.dart';
import '../widgets/status_grid.dart';
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
    final provider = context.read<StatusProvider>();
    final isAlreadySaved = provider.isStatusDownloaded(status.name) || status.isSaved;
    
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => StatusViewer(
          status: status,
          isAlreadySaved: isAlreadySaved,
          onSave: () async {
            final success = await provider.saveStatus(status);
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    success ? 'Status saved!' : 'Failed to save',
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
          content: Text(success ? 'Status saved!' : 'Failed to save'),
          backgroundColor: success ? AppColors.success : AppColors.error,
        ),
      );
    }
  }

  Future<void> _saveAllStatuses() async {
    final provider = context.read<StatusProvider>();
    
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

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Row(
          children: [
            Icon(Icons.download_rounded, color: AppColors.primaryGreen),
            const SizedBox(width: 12),
            const Text('Save All?'),
          ],
        ),
        content: Text(
          'Save all ${statuses.length} ${_tabController.index == 0 ? 'images' : 'videos'}?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Save All'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() => _isSavingAll = true);

    int successCount = 0;
    for (final status in statuses) {
      if (await provider.saveStatus(status)) successCount++;
    }

    setState(() => _isSavingAll = false);

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('$successCount/${statuses.length} saved successfully'),
          backgroundColor: AppColors.success,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<StatusProvider>(
      builder: (context, provider, _) {
        // Show permission dialog if not granted
        if (!provider.hasPermission && provider.isInitialized) {
          return PermissionDialog(
            onRequestPermission: () => provider.requestPermission(),
            onOpenSettings: () => provider.openSettings(),
          );
        }

        // Show SAF access dialog for Android 11+ (API 30+)
        if (provider.needsSafAccess && provider.isInitialized) {
          return SafAccessDialog(
            onRequestAccess: () => provider.requestSafAccess(),
          );
        }

        // Show no WhatsApp dialog
        if (provider.isInitialized && !provider.hasWhatsApp && provider.hasPermission) {
          return const NoWhatsAppDialog();
        }

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
                          Text('Images (${provider.liveImages.length})'),
                        ],
                      ),
                    ),
                    Tab(
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.videocam_rounded, size: 20),
                          const SizedBox(width: 8),
                          Text('Videos (${provider.liveVideos.length})'),
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
                        statuses: provider.liveImages,
                        isLoading: provider.isLoading,
                        onTap: _openStatus,
                        onSave: _saveStatus,
                        emptyMessage: 'No image statuses found',
                        emptyIcon: Icons.image_outlined,
                      ),
                    ),

                    // Videos Tab
                    RefreshIndicator(
                      onRefresh: _handleRefresh,
                      color: AppColors.primaryGreen,
                      child: StatusGrid(
                        statuses: provider.liveVideos,
                        isLoading: provider.isLoading,
                        onTap: _openStatus,
                        onSave: _saveStatus,
                        emptyMessage: 'No video statuses found',
                        emptyIcon: Icons.videocam_off_outlined,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          floatingActionButton: provider.liveStatuses.isNotEmpty
              ? FloatingActionButton.extended(
                  onPressed: _isSavingAll ? null : _saveAllStatuses,
                  icon: _isSavingAll
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.download_rounded),
                  label: Text(_isSavingAll ? 'Saving...' : 'Save All'),
                )
              : null,
        );
      },
    );
  }
}
