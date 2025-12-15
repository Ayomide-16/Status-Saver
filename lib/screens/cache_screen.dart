import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/theme.dart';
import '../providers/status_provider.dart';
import '../services/cache_service.dart';
import '../widgets/status_grid.dart';
import '../widgets/custom_widgets.dart';
import 'status_viewer.dart';

class CacheScreen extends StatefulWidget {
  const CacheScreen({super.key});

  @override
  State<CacheScreen> createState() => _CacheScreenState();
}

class _CacheScreenState extends State<CacheScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final CacheService _cacheService = CacheService();
  int _cacheSize = 0;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadCacheInfo();
    });
  }

  Future<void> _loadCacheInfo() async {
    final size = await _cacheService.getCacheSize();
    if (mounted) {
      setState(() {
        _cacheSize = size;
      });
    }
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _handleRefresh() async {
    await context.read<StatusProvider>().refreshCachedStatuses();
    await _loadCacheInfo();
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

  Future<void> _clearCache() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppColors.surfaceDark,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Row(
          children: [
            Icon(Icons.warning_amber_rounded, color: AppColors.warning),
            const SizedBox(width: 12),
            Text(
              'Clear Cache?',
              style: TextStyle(color: AppColors.textPrimary),
            ),
          ],
        ),
        content: Text(
          'This will delete all cached statuses. This action cannot be undone.',
          style: TextStyle(color: AppColors.textSecondary),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('Cancel', style: TextStyle(color: AppColors.textSecondary)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text('Clear', style: TextStyle(color: AppColors.error)),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await context.read<StatusProvider>().clearAllCache();
      await _loadCacheInfo();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Cache cleared successfully'),
            backgroundColor: AppColors.success,
          ),
        );
      }
    }
  }

  String? _getCacheTimeLeft(status) {
    final metadata = _cacheService.getCacheMetadata(status.originalPath ?? status.path);
    return metadata?.formattedRemainingTime;
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

                  // Cache Info Card
                  _buildCacheInfoCard(provider),

                  // Tab Bar
                  CustomTabBar(
                    tabController: _tabController,
                    tabs: const ['Images', 'Videos'],
                    icons: const [Icons.image_rounded, Icons.videocam_rounded],
                    counts: [
                      provider.cachedImages.length,
                      provider.cachedVideos.length,
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
                            statuses: provider.cachedImages,
                            isLoading: false,
                            onTap: _openStatus,
                            onSave: _saveStatus,
                            getCacheTimeLeft: _getCacheTimeLeft,
                            emptyMessage: 'No cached images',
                            emptyIcon: Icons.cached_outlined,
                          ),
                        ),

                        // Videos Tab
                        RefreshIndicator(
                          onRefresh: _handleRefresh,
                          color: AppColors.accent,
                          backgroundColor: AppColors.surfaceDark,
                          child: StatusGrid(
                            statuses: provider.cachedVideos,
                            isLoading: false,
                            onTap: _openStatus,
                            onSave: _saveStatus,
                            getCacheTimeLeft: _getCacheTimeLeft,
                            emptyMessage: 'No cached videos',
                            emptyIcon: Icons.cached_outlined,
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
                  shaderCallback: (bounds) => LinearGradient(
                    colors: [AppColors.warning, Colors.orange],
                  ).createShader(bounds),
                  child: const Text(
                    'Cache',
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ),
                Text(
                  '${provider.cachedStatuses.length} statuses cached',
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),

          // Clear Cache Button
          if (provider.cachedStatuses.isNotEmpty)
            IconButton(
              onPressed: _clearCache,
              icon: Icon(
                Icons.delete_sweep_rounded,
                color: AppColors.error,
              ),
              tooltip: 'Clear Cache',
            ),
        ],
      ),
    );
  }

  Widget _buildCacheInfoCard(StatusProvider provider) {
    if (provider.cachedStatuses.isEmpty) return const SizedBox.shrink();

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            AppColors.warning.withValues(alpha: 0.2),
            AppColors.warning.withValues(alpha: 0.1),
          ],
        ),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: AppColors.warning.withValues(alpha: 0.3),
        ),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppColors.warning.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(
              Icons.storage_rounded,
              color: AppColors.warning,
              size: 24,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Cache Storage',
                  style: TextStyle(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '${_cacheService.formatSize(_cacheSize)} used • Auto-deletes after 7 days',
                  style: TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          Icon(
            Icons.info_outline_rounded,
            color: AppColors.warning.withValues(alpha: 0.7),
            size: 20,
          ),
        ],
      ),
    );
  }
}
