import 'dart:io';
import 'package:flutter/material.dart';
import '../config/theme.dart';
import '../models/status_item.dart';

class StatusCard extends StatefulWidget {
  final StatusItem status;
  final VoidCallback? onTap;
  final VoidCallback? onSave;
  final VoidCallback? onDelete;
  final VoidCallback? onShare;
  final bool showSave;
  final bool showDelete;
  final bool showShare;
  final String? expiryText;

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
    this.expiryText,
  });

  @override
  State<StatusCard> createState() => _StatusCardState();
}

class _StatusCardState extends State<StatusCard> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnimation;
  bool _isPressed = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 150),
      vsync: this,
    );
    _scaleAnimation = Tween<double>(begin: 1.0, end: 0.95).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onTapDown(_) {
    setState(() => _isPressed = true);
    _controller.forward();
  }

  void _onTapUp(_) {
    setState(() => _isPressed = false);
    _controller.reverse();
  }

  void _onTapCancel() {
    setState(() => _isPressed = false);
    _controller.reverse();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return AnimatedBuilder(
      animation: _scaleAnimation,
      builder: (context, child) {
        return Transform.scale(
          scale: _scaleAnimation.value,
          child: GestureDetector(
            onTapDown: _onTapDown,
            onTapUp: _onTapUp,
            onTapCancel: _onTapCancel,
            onTap: widget.onTap,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: (isDark ? Colors.black : Colors.grey).withValues(alpha: _isPressed ? 0.1 : 0.2),
                    blurRadius: _isPressed ? 4 : 8,
                    offset: Offset(0, _isPressed ? 2 : 4),
                  ),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: Stack(
                  children: [
                    // Image/Video thumbnail
                    AspectRatio(
                      aspectRatio: 0.75,
                      child: Hero(
                        tag: widget.status.path,
                        child: _buildThumbnail(isDark),
                      ),
                    ),
                    
                    // Gradient overlay at bottom
                    Positioned(
                      left: 0,
                      right: 0,
                      bottom: 0,
                      child: Container(
                        height: 80,
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [
                              Colors.transparent,
                              Colors.black.withValues(alpha: 0.7),
                            ],
                          ),
                        ),
                      ),
                    ),
                    
                    // Video indicator with play icon
                    if (widget.status.isVideo)
                      Positioned(
                        top: 8,
                        left: 8,
                        child: _buildVideoIndicator(),
                      ),
                    
                    // Cached badge
                    if (widget.status.isCached)
                      Positioned(
                        top: 8,
                        right: 8,
                        child: _buildCachedBadge(),
                      ),
                    
                    // Expiry text for cached items
                    if (widget.expiryText != null)
                      Positioned(
                        top: 8,
                        right: 8,
                        child: _buildExpiryBadge(),
                      ),
                    
                    // Bottom info bar
                    Positioned(
                      left: 0,
                      right: 0,
                      bottom: 0,
                      child: _buildBottomBar(isDark),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildThumbnail(bool isDark) {
    final file = File(widget.status.path);

    if (!file.existsSync()) {
      return Container(
        color: isDark ? AppColors.darkCard : AppColors.lightCard,
        child: Center(
          child: Icon(
            Icons.broken_image_outlined,
            size: 40,
            color: isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary,
          ),
        ),
      );
    }

    if (widget.status.isVideo) {
      // Show thumbnail or placeholder for video
      return Container(
        color: isDark ? AppColors.darkCard : AppColors.lightCard,
        child: Center(
          child: Container(
            width: 60,
            height: 60,
            decoration: BoxDecoration(
              color: AppColors.primaryGreen.withValues(alpha: 0.9),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.play_arrow_rounded,
              size: 36,
              color: Colors.white,
            ),
          ),
        ),
      );
    }

    return Image.file(
      file,
      fit: BoxFit.cover,
      errorBuilder: (_, __, ___) => Container(
        color: isDark ? AppColors.darkCard : AppColors.lightCard,
        child: const Icon(Icons.broken_image_outlined),
      ),
    );
  }

  Widget _buildVideoIndicator() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.7),
        borderRadius: BorderRadius.circular(20),
      ),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.videocam_rounded, color: Colors.white, size: 14),
          SizedBox(width: 4),
          Text(
            'VIDEO',
            style: TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }

  Widget _buildCachedBadge() {
    return Container(
      padding: const EdgeInsets.all(6),
      decoration: BoxDecoration(
        color: AppColors.primaryGreen.withValues(alpha: 0.9),
        shape: BoxShape.circle,
      ),
      child: const Icon(Icons.access_time_rounded, color: Colors.white, size: 14),
    );
  }

  Widget _buildExpiryBadge() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: AppColors.primaryGreen.withValues(alpha: 0.9),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        widget.expiryText!,
        style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.w600),
      ),
    );
  }

  Widget _buildBottomBar(bool isDark) {
    return Padding(
      padding: const EdgeInsets.all(8),
      child: Row(
        children: [
          // Time info
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  widget.status.formattedDate,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                Text(
                  widget.status.formattedSize,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.7),
                    fontSize: 10,
                  ),
                ),
              ],
            ),
          ),
          
          // Action buttons
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (widget.showSave && widget.onSave != null)
                _buildActionButton(
                  icon: Icons.download_rounded,
                  color: AppColors.primaryGreen,
                  onTap: widget.onSave!,
                ),
              if (widget.showShare && widget.onShare != null)
                _buildActionButton(
                  icon: Icons.share_rounded,
                  color: Colors.blue,
                  onTap: widget.onShare!,
                ),
              if (widget.showDelete && widget.onDelete != null)
                _buildActionButton(
                  icon: Icons.delete_outline_rounded,
                  color: AppColors.error,
                  onTap: widget.onDelete!,
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildActionButton({
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.9),
            shape: BoxShape.circle,
          ),
          child: Icon(icon, size: 16, color: Colors.white),
        ),
      ),
    );
  }
}
