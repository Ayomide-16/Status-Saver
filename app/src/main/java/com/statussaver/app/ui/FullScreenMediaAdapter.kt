package com.statussaver.app.ui

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.github.chrisbanes.photoview.PhotoView
import com.statussaver.app.R
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusSource
import java.io.File

/**
 * Adapter for ViewPager2 in FullScreenViewActivity.
 * Handles both images and videos with gesture controls.
 */
class FullScreenMediaAdapter(
    private val items: List<MediaItem>,
    private val onDownloadStateChanged: (Int, Boolean) -> Unit,
    private val onControlsVisibilityChanged: (Boolean) -> Unit = {}
) : RecyclerView.Adapter<FullScreenMediaAdapter.MediaViewHolder>() {

    private var currentVideoView: VideoView? = null
    private var currentMediaPlayer: MediaPlayer? = null
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fullscreen_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun pauseCurrentVideo() {
        currentVideoView?.pause()
    }

    fun releaseCurrentVideo() {
        currentVideoView?.stopPlayback()
        currentVideoView = null
        currentMediaPlayer = null
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        holder.cleanup()
    }

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: PhotoView = itemView.findViewById(R.id.mediaImage)
        private val videoView: VideoView = itemView.findViewById(R.id.mediaVideo)
        private val touchOverlay: View = itemView.findViewById(R.id.touchOverlay)
        private val leftSeekIndicator: LinearLayout = itemView.findViewById(R.id.leftSeekIndicator)
        private val rightSeekIndicator: LinearLayout = itemView.findViewById(R.id.rightSeekIndicator)
        private val speedIndicator: TextView = itemView.findViewById(R.id.speedIndicator)
        
        // Custom controls
        private val customControls: View = itemView.findViewById(R.id.customControls)
        private val btnRewind: ImageButton = itemView.findViewById(R.id.btnRewind)
        private val btnPlayPause: ImageButton = itemView.findViewById(R.id.btnPlayPause)
        private val btnForward: ImageButton = itemView.findViewById(R.id.btnForward)
        private val seekBar: SeekBar = itemView.findViewById(R.id.videoSeekBar)
        private val txtCurrentTime: TextView = itemView.findViewById(R.id.txtCurrentTime)
        private val txtDuration: TextView = itemView.findViewById(R.id.txtDuration)

        private var gestureHandler: VideoGestureHandler? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        private var mediaPlayer: MediaPlayer? = null
        private var isReverseMode = false
        private var reverseHandler: Handler? = null
        private var reverseRunnable: Runnable? = null
        private var controlsVisible = true
        private var savedPosition = 0

        // Separate handlers for animations to avoid conflicts
        private val leftAnimHandler = Handler(Looper.getMainLooper())
        private val rightAnimHandler = Handler(Looper.getMainLooper())
        
        // Video zoom state
        private var videoScaleFactor = 1.0f
        private val minScale = 1.0f
        private val maxScale = 3.0f
        private var scaleGestureDetector: ScaleGestureDetector? = null
        
        // Controls position offset for FABs (in dp)
        private val controlsOffsetForFabs = 80f

        @SuppressLint("ClickableViewAccessibility")
        fun bind(item: MediaItem) {
            if (item.fileType == FileType.VIDEO) {
                setupVideo(item)
            } else {
                setupImage(item)
            }
        }

        private fun setupImage(item: MediaItem) {
            imageView.visibility = View.VISIBLE
            videoView.visibility = View.GONE
            touchOverlay.visibility = View.GONE
            customControls.visibility = View.GONE
            resetIndicators()

            when (item.source) {
                StatusSource.LIVE -> {
                    val uri = Uri.parse(item.uri)
                    imageView.load(uri) { crossfade(true) }
                }
                else -> {
                    val file = File(item.path)
                    imageView.load(file) { crossfade(true) }
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun setupVideo(item: MediaItem) {
            imageView.visibility = View.GONE
            videoView.visibility = View.VISIBLE
            touchOverlay.visibility = View.VISIBLE
            customControls.visibility = View.VISIBLE
            resetIndicators()

            val uri = when (item.source) {
                StatusSource.LIVE -> Uri.parse(item.uri)
                else -> {
                    val file = File(item.path)
                    FileProvider.getUriForFile(
                        itemView.context,
                        "${itemView.context.packageName}.fileprovider",
                        file
                    )
                }
            }

            videoView.setVideoURI(uri)
            videoView.setMediaController(null)

            videoView.setOnPreparedListener { mp ->
                mediaPlayer = mp
                currentMediaPlayer = mp
                mp.isLooping = false
                videoView.start()
                
                setupCustomControls()
                updatePlayPauseButton()
                startSeekBarUpdates()
            }

            currentVideoView = videoView

            // Setup gesture handler
            gestureHandler = VideoGestureHandler(
                itemView.context,
                touchOverlay,
                object : VideoGestureHandler.GestureListener {
                    override fun onDoubleTapSeek(forward: Boolean) {
                        performSeek(forward)
                    }

                    override fun onLongPressStart(isRightSide: Boolean) {
                        if (isRightSide) {
                            // 2x forward speed
                            startFastForward()
                        } else {
                            // Reverse playback
                            startReversePlayback()
                        }
                    }

                    override fun onLongPressEnd() {
                        stopSpeedControl()
                    }

                    override fun onDoubleTapCenter() {
                        togglePlayPause()
                    }

                    override fun onSingleTap() {
                        toggleControls()
                    }
                }
            )

            // Note: Video zoom disabled for smoother gesture handling
            // VideoView doesn't support smooth scaling like PhotoView does for images

            touchOverlay.setOnTouchListener { _, event ->
                gestureHandler?.onTouchEvent(event) ?: false
            }
        }
        
        private fun performSeek(forward: Boolean) {
            val currentPos = videoView.currentPosition
            val seekAmount = 3000 // 3 seconds
            
            val newPos = if (forward) {
                minOf(currentPos + seekAmount, videoView.duration)
            } else {
                maxOf(currentPos - seekAmount, 0)
            }
            
            videoView.seekTo(newPos)
            showSeekAnimation(forward)
        }
        
        private fun setupCustomControls() {
            val duration = videoView.duration
            seekBar.max = duration
            txtDuration.text = formatTime(duration)
            
            // Rewind button - 3 seconds
            btnRewind.setOnClickListener {
                performSeek(false)
            }
            
            // Forward button - 3 seconds
            btnForward.setOnClickListener {
                performSeek(true)
            }
            
            // Play/Pause button
            btnPlayPause.setOnClickListener {
                if (videoView.isPlaying) {
                    videoView.pause()
                } else {
                    videoView.start()
                }
                updatePlayPauseButton()
            }
            
            // SeekBar
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        videoView.seekTo(progress)
                        txtCurrentTime.text = formatTime(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        
        private fun startSeekBarUpdates() {
            val updateRunnable = object : Runnable {
                override fun run() {
                    try {
                        if (videoView.isPlaying) {
                            seekBar.progress = videoView.currentPosition
                            txtCurrentTime.text = formatTime(videoView.currentPosition)
                        }
                        mainHandler.postDelayed(this, 200)
                    } catch (e: Exception) {
                        // Video view might be released
                    }
                }
            }
            mainHandler.post(updateRunnable)
        }
        
        private fun updatePlayPauseButton() {
            val iconRes = if (videoView.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            btnPlayPause.setImageResource(iconRes)
        }
        
        private fun togglePlayPause() {
            if (videoView.isPlaying) {
                videoView.pause()
            } else {
                videoView.start()
            }
            updatePlayPauseButton()
        }
        
        private fun toggleControls() {
            controlsVisible = !controlsVisible
            val targetAlpha = if (controlsVisible) 1f else 0f
            
            // Convert dp to pixels for the offset
            val density = itemView.context.resources.displayMetrics.density
            val offsetPx = controlsOffsetForFabs * density
            
            // When controls are visible, move them up to avoid FAB overlap
            val targetTranslationY = if (controlsVisible) -offsetPx else 0f
            
            customControls.animate()
                .alpha(targetAlpha)
                .translationY(targetTranslationY)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withStartAction { 
                    if (controlsVisible) customControls.visibility = View.VISIBLE 
                }
                .withEndAction {
                    if (!controlsVisible) customControls.visibility = View.GONE
                }
                .start()
            
            // Notify Activity to sync FAB visibility
            onControlsVisibilityChanged(controlsVisible)
        }
        
        private fun formatTime(ms: Int): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }

        private fun setPlaybackSpeed(speed: Float) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mediaPlayer?.let { mp ->
                        val params = mp.playbackParams
                        params.speed = speed
                        mp.playbackParams = params
                    }
                } catch (e: Exception) {
                    // Some devices may not support playback speed
                }
            }
        }
        
        private fun startFastForward() {
            setPlaybackSpeed(2.0f)
            speedIndicator.text = "2x ▶▶"
            speedIndicator.visibility = View.VISIBLE
            speedIndicator.alpha = 1f
        }
        
        private fun startReversePlayback() {
            isReverseMode = true
            savedPosition = videoView.currentPosition
            
            // Show indicator
            speedIndicator.text = "◀◀ 2x"
            speedIndicator.visibility = View.VISIBLE
            speedIndicator.alpha = 1f
            
            // Keep video playing but rapidly seek backward
            reverseHandler = Handler(Looper.getMainLooper())
            reverseRunnable = object : Runnable {
                override fun run() {
                    if (isReverseMode) {
                        try {
                            val current = videoView.currentPosition
                            if (current > 100) {
                                // Seek backward while still playing for smoother effect
                                val newPos = maxOf(current - 150, 0)
                                videoView.seekTo(newPos)
                                reverseHandler?.postDelayed(this, 50)
                            } else {
                                // Reached beginning
                                isReverseMode = false
                            }
                        } catch (e: Exception) {
                            isReverseMode = false
                        }
                    }
                }
            }
            reverseHandler?.post(reverseRunnable!!)
        }
        
        private fun stopSpeedControl() {
            // Stop reverse mode
            isReverseMode = false
            reverseRunnable?.let { reverseHandler?.removeCallbacks(it) }
            reverseHandler = null
            reverseRunnable = null
            
            // Reset speed to normal
            setPlaybackSpeed(1.0f)
            
            // Make sure video is playing
            if (!videoView.isPlaying) {
                videoView.start()
            }
            
            // Hide speed indicator with animation
            speedIndicator.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    speedIndicator.visibility = View.GONE
                    speedIndicator.alpha = 1f
                }
                .start()
        }

        private fun showSeekAnimation(forward: Boolean) {
            // Clear any pending animations
            leftAnimHandler.removeCallbacksAndMessages(null)
            rightAnimHandler.removeCallbacksAndMessages(null)
            
            // Hide both indicators first (in case previous animation was running)
            leftSeekIndicator.clearAnimation()
            rightSeekIndicator.clearAnimation()
            leftSeekIndicator.visibility = View.GONE
            rightSeekIndicator.visibility = View.GONE
            
            // Select the correct indicator based on direction
            val indicator = if (forward) rightSeekIndicator else leftSeekIndicator
            val animHandler = if (forward) rightAnimHandler else leftAnimHandler
            
            // Reset indicator state
            indicator.alpha = 0f
            indicator.scaleX = 0.6f
            indicator.scaleY = 0.6f
            indicator.visibility = View.VISIBLE
            
            // Animate in with scale and fade
            indicator.animate()
                .alpha(1f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(150)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction {
                    // Slight scale back
                    indicator.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    
                    // Schedule fade out
                    animHandler.postDelayed({
                        indicator.animate()
                            .alpha(0f)
                            .scaleX(0.8f)
                            .scaleY(0.8f)
                            .setDuration(250)
                            .withEndAction { 
                                indicator.visibility = View.GONE 
                            }
                            .start()
                    }, 350)
                }
                .start()
        }

        private fun resetIndicators() {
            leftAnimHandler.removeCallbacksAndMessages(null)
            rightAnimHandler.removeCallbacksAndMessages(null)
            leftSeekIndicator.visibility = View.GONE
            rightSeekIndicator.visibility = View.GONE
            speedIndicator.visibility = View.GONE
            leftSeekIndicator.clearAnimation()
            rightSeekIndicator.clearAnimation()
        }

        fun cleanup() {
            gestureHandler?.cleanup()
            gestureHandler = null
            mainHandler.removeCallbacksAndMessages(null)
            leftAnimHandler.removeCallbacksAndMessages(null)
            rightAnimHandler.removeCallbacksAndMessages(null)
            stopSpeedControl()
            videoView.stopPlayback()
            mediaPlayer = null
        }
    }
}
