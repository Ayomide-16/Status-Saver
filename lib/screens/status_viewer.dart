import 'dart:io';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import 'package:photo_view/photo_view.dart';
import '../config/theme.dart';
import '../models/status_item.dart';

class StatusViewer extends StatefulWidget {
  final StatusItem status;
  final VoidCallback? onSave;
  final VoidCallback? onShare;
  final VoidCallback? onDelete;
  final bool showSave;
  final bool showShare;
  final bool showDelete;
  final bool isAlreadySaved;

  const StatusViewer({
    super.key,
    required this.status,
    this.onSave,
    this.onShare,
    this.onDelete,
    this.showSave = true,
    this.showShare = false,
    this.showDelete = false,
    this.isAlreadySaved = false,
  });

  @override
  State<StatusViewer> createState() => _StatusViewerState();
}

class _StatusViewerState extends State<StatusViewer> {
  VideoPlayerController? _videoController;
  bool _isVideoInitialized = false;
  late bool _isSaved;

  @override
  void initState() {
    super.initState();
    _isSaved = widget.isAlreadySaved || widget.status.isSaved;
    if (widget.status.isVideo) {
      _initializeVideo();
    }
  }

  Future<void> _initializeVideo() async {
    _videoController = VideoPlayerController.file(File(widget.status.path));
    try {
      await _videoController!.initialize();
      await _videoController!.setLooping(true);
      setState(() => _isVideoInitialized = true);
      _videoController!.play();
    } catch (e) {
      debugPrint('Error initializing video: $e');
    }
  }

  @override
  void dispose() {
    _videoController?.dispose();
    super.dispose();
  }

  void _handleSave() {
    if (_isSaved) return; // Already saved, do nothing
    
    widget.onSave?.call();
    setState(() => _isSaved = true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(
          widget.status.isVideo ? 'Video' : 'Image',
          style: const TextStyle(color: Colors.white),
        ),
        actions: [
          // Save/Downloaded button
          if (widget.showSave && widget.onSave != null)
            _isSaved
                ? Container(
                    margin: const EdgeInsets.symmetric(horizontal: 8),
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.success.withValues(alpha: 0.2),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.check_rounded,
                      color: AppColors.success,
                      size: 24,
                    ),
                  )
                : IconButton(
                    icon: const Icon(Icons.download_rounded),
                    onPressed: _handleSave,
                    tooltip: 'Save',
                  ),
          if (widget.showShare && widget.onShare != null)
            IconButton(
              icon: const Icon(Icons.share_rounded),
              onPressed: widget.onShare,
              tooltip: 'Share',
            ),
          if (widget.showDelete && widget.onDelete != null)
            IconButton(
              icon: const Icon(Icons.delete_outline_rounded),
              color: AppColors.error,
              onPressed: widget.onDelete,
              tooltip: 'Delete',
            ),
        ],
      ),
      body: widget.status.isVideo ? _buildVideoPlayer() : _buildImageViewer(),
    );
  }

  Widget _buildImageViewer() {
    return PhotoView(
      imageProvider: FileImage(File(widget.status.path)),
      backgroundDecoration: const BoxDecoration(color: Colors.black),
      minScale: PhotoViewComputedScale.contained,
      maxScale: PhotoViewComputedScale.covered * 3,
      errorBuilder: (_, __, ___) => _buildErrorWidget(),
    );
  }

  Widget _buildVideoPlayer() {
    if (!_isVideoInitialized || _videoController == null) {
      return const Center(
        child: CircularProgressIndicator(
          color: AppColors.primaryGreen,
        ),
      );
    }

    return Stack(
      fit: StackFit.expand,
      children: [
        // Video
        Center(
          child: AspectRatio(
            aspectRatio: _videoController!.value.aspectRatio,
            child: VideoPlayer(_videoController!),
          ),
        ),
        
        // Play/Pause overlay
        Center(
          child: GestureDetector(
            onTap: () {
              setState(() {
                _videoController!.value.isPlaying
                    ? _videoController!.pause()
                    : _videoController!.play();
              });
            },
            child: AnimatedOpacity(
              opacity: _videoController!.value.isPlaying ? 0 : 1,
              duration: const Duration(milliseconds: 200),
              child: Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.7),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.play_arrow_rounded,
                  color: Colors.white,
                  size: 40,
                ),
              ),
            ),
          ),
        ),
        
        // Progress bar
        Positioned(
          left: 0,
          right: 0,
          bottom: 0,
          child: VideoProgressIndicator(
            _videoController!,
            allowScrubbing: true,
            colors: VideoProgressColors(
              playedColor: AppColors.primaryGreen,
              bufferedColor: AppColors.primaryGreen.withValues(alpha: 0.3),
              backgroundColor: Colors.white.withValues(alpha: 0.2),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildErrorWidget() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.error_outline_rounded,
            size: 64,
            color: Colors.white54,
          ),
          SizedBox(height: 16),
          Text(
            'Failed to load media',
            style: TextStyle(color: Colors.white54),
          ),
        ],
      ),
    );
  }
}
