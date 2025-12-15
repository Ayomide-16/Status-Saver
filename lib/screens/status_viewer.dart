import 'dart:io';
import 'package:flutter/material.dart';
import 'package:photo_view/photo_view.dart';
import 'package:video_player/video_player.dart';
import '../config/theme.dart';
import '../models/status_item.dart';

class StatusViewer extends StatefulWidget {
  final StatusItem status;
  final VoidCallback? onSave;
  final VoidCallback? onShare;
  final bool showSaveButton;

  const StatusViewer({
    super.key,
    required this.status,
    this.onSave,
    this.onShare,
    this.showSaveButton = true,
  });

  @override
  State<StatusViewer> createState() => _StatusViewerState();
}

class _StatusViewerState extends State<StatusViewer> {
  VideoPlayerController? _videoController;
  bool _isPlaying = false;
  bool _isInitialized = false;
  bool _showControls = true;

  @override
  void initState() {
    super.initState();
    if (widget.status.isVideo) {
      _initializeVideo();
    }
  }

  Future<void> _initializeVideo() async {
    _videoController = VideoPlayerController.file(File(widget.status.path));
    
    try {
      await _videoController!.initialize();
      _videoController!.addListener(_videoListener);
      setState(() {
        _isInitialized = true;
      });
    } catch (e) {
      print('Error initializing video: $e');
    }
  }

  void _videoListener() {
    if (_videoController != null) {
      setState(() {
        _isPlaying = _videoController!.value.isPlaying;
      });
    }
  }

  @override
  void dispose() {
    _videoController?.removeListener(_videoListener);
    _videoController?.dispose();
    super.dispose();
  }

  void _togglePlayPause() {
    if (_videoController == null) return;

    if (_isPlaying) {
      _videoController!.pause();
    } else {
      _videoController!.play();
    }
  }

  void _toggleControls() {
    setState(() {
      _showControls = !_showControls;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: GestureDetector(
        onTap: widget.status.isVideo ? _toggleControls : null,
        child: Stack(
          fit: StackFit.expand,
          children: [
            // Content
            widget.status.isVideo ? _buildVideoPlayer() : _buildImageViewer(),

            // Top Bar
            AnimatedOpacity(
              opacity: _showControls ? 1.0 : 0.0,
              duration: const Duration(milliseconds: 200),
              child: _buildTopBar(),
            ),

            // Bottom Bar
            AnimatedOpacity(
              opacity: _showControls ? 1.0 : 0.0,
              duration: const Duration(milliseconds: 200),
              child: _buildBottomBar(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildImageViewer() {
    final file = File(widget.status.path);
    if (!file.existsSync()) {
      return _buildErrorWidget();
    }

    return PhotoView(
      imageProvider: FileImage(file),
      minScale: PhotoViewComputedScale.contained,
      maxScale: PhotoViewComputedScale.covered * 3,
      backgroundDecoration: const BoxDecoration(color: Colors.black),
      loadingBuilder: (context, event) => Center(
        child: CircularProgressIndicator(
          color: AppColors.accent,
          value: event?.expectedTotalBytes != null
              ? event!.cumulativeBytesLoaded / event.expectedTotalBytes!
              : null,
        ),
      ),
      errorBuilder: (context, error, stackTrace) => _buildErrorWidget(),
    );
  }

  Widget _buildVideoPlayer() {
    if (!_isInitialized || _videoController == null) {
      return Center(
        child: CircularProgressIndicator(
          color: AppColors.accent,
        ),
      );
    }

    return Stack(
      alignment: Alignment.center,
      children: [
        // Video
        Center(
          child: AspectRatio(
            aspectRatio: _videoController!.value.aspectRatio,
            child: VideoPlayer(_videoController!),
          ),
        ),

        // Play/Pause Button
        AnimatedOpacity(
          opacity: _showControls ? 1.0 : 0.0,
          duration: const Duration(milliseconds: 200),
          child: GestureDetector(
            onTap: _togglePlayPause,
            child: Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.5),
                shape: BoxShape.circle,
              ),
              child: Icon(
                _isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                size: 50,
                color: Colors.white,
              ),
            ),
          ),
        ),

        // Video Progress
        if (_showControls)
          Positioned(
            bottom: 100,
            left: 20,
            right: 20,
            child: _buildVideoProgress(),
          ),
      ],
    );
  }

  Widget _buildVideoProgress() {
    return Column(
      children: [
        // Progress bar
        VideoProgressIndicator(
          _videoController!,
          allowScrubbing: true,
          colors: VideoProgressColors(
            playedColor: AppColors.accent,
            bufferedColor: AppColors.accent.withValues(alpha: 0.3),
            backgroundColor: Colors.white.withValues(alpha: 0.2),
          ),
          padding: const EdgeInsets.symmetric(vertical: 8),
        ),

        // Time display
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            ValueListenableBuilder(
              valueListenable: _videoController!,
              builder: (context, VideoPlayerValue value, child) {
                return Text(
                  _formatDuration(value.position),
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                  ),
                );
              },
            ),
            Text(
              _formatDuration(_videoController!.value.duration),
              style: const TextStyle(
                color: Colors.white,
                fontSize: 12,
              ),
            ),
          ],
        ),
      ],
    );
  }

  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = duration.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  Widget _buildErrorWidget() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.error_outline_rounded,
            size: 64,
            color: AppColors.error,
          ),
          const SizedBox(height: 16),
          Text(
            'Failed to load media',
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 16,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTopBar() {
    return Positioned(
      top: 0,
      left: 0,
      right: 0,
      child: Container(
        padding: EdgeInsets.only(
          top: MediaQuery.of(context).padding.top + 8,
          left: 8,
          right: 8,
          bottom: 8,
        ),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Colors.black.withValues(alpha: 0.7),
              Colors.transparent,
            ],
          ),
        ),
        child: Row(
          children: [
            // Back Button
            IconButton(
              onPressed: () => Navigator.pop(context),
              icon: const Icon(
                Icons.arrow_back_rounded,
                color: Colors.white,
              ),
            ),

            // Title
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.status.isVideo ? 'Video' : 'Image',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  Text(
                    widget.status.formattedDate,
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.7),
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),

            // File size
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Text(
                widget.status.formattedSize,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBottomBar() {
    return Positioned(
      bottom: 0,
      left: 0,
      right: 0,
      child: Container(
        padding: EdgeInsets.only(
          top: 16,
          left: 20,
          right: 20,
          bottom: MediaQuery.of(context).padding.bottom + 16,
        ),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.bottomCenter,
            end: Alignment.topCenter,
            colors: [
              Colors.black.withValues(alpha: 0.7),
              Colors.transparent,
            ],
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (widget.onShare != null)
              _buildActionButton(
                icon: Icons.share_rounded,
                label: 'Share',
                onTap: widget.onShare!,
                color: AppColors.accent,
              ),
            if (widget.showSaveButton && widget.onSave != null) ...[
              const SizedBox(width: 32),
              _buildActionButton(
                icon: Icons.download_rounded,
                label: 'Save',
                onTap: widget.onSave!,
                color: AppColors.accent,
                isPrimary: true,
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildActionButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    required Color color,
    bool isPrimary = false,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: EdgeInsets.symmetric(
          horizontal: isPrimary ? 32 : 24,
          vertical: 14,
        ),
        decoration: BoxDecoration(
          gradient: isPrimary ? AppColors.accentGradient : null,
          color: isPrimary ? null : Colors.white.withValues(alpha: 0.2),
          borderRadius: BorderRadius.circular(30),
          border: isPrimary ? null : Border.all(color: Colors.white.withValues(alpha: 0.3)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: Colors.white,
              size: 20,
            ),
            const SizedBox(width: 8),
            Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 14,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
