package com.statussaver.app.ui

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import coil.load
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
    private val onDownloadStateChanged: (Int, Boolean) -> Unit
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
        private val imageView: ImageView = itemView.findViewById(R.id.mediaImage)
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
        private val handler = Handler(Looper.getMainLooper())
        private var mediaPlayer: MediaPlayer? = null
        private var isReverseMode = false
        private var reverseHandler: Handler? = null
        private var reverseRunnable: Runnable? = null
        private var controlsVisible = true

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
            hideAllIndicators()

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
            hideAllIndicators()

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
            // Remove default MediaController - using custom controls
            videoView.setMediaController(null)

            videoView.setOnPreparedListener { mp ->
                mediaPlayer = mp
                currentMediaPlayer = mp
                mp.isLooping = false
                videoView.start()
                
                // Setup custom controls
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
                        val currentPos = videoView.currentPosition
                        val seekAmount = VideoGestureHandler.SEEK_DURATION_MS
                        
                        val newPos = if (forward) {
                            minOf(currentPos + seekAmount, videoView.duration)
                        } else {
                            maxOf(currentPos - seekAmount, 0)
                        }
                        
                        videoView.seekTo(newPos)
                        showSeekIndicator(forward)
                    }

                    override fun onLongPressStart(isRightSide: Boolean) {
                        if (isRightSide) {
                            // 2x forward speed
                            setPlaybackSpeed(2.0f)
                            showSpeedIndicator("2x ▶▶")
                        } else {
                            // Reverse playback simulation
                            startReversePlayback()
                            showSpeedIndicator("◀◀ 2x")
                        }
                    }

                    override fun onLongPressEnd() {
                        stopReversePlayback()
                        setPlaybackSpeed(1.0f)
                        hideSpeedIndicator()
                    }

                    override fun onSingleTap() {
                        toggleControls()
                    }
                }
            )

            touchOverlay.setOnTouchListener { _, event ->
                gestureHandler?.onTouchEvent(event) ?: false
            }
        }
        
        private fun setupCustomControls() {
            val duration = videoView.duration
            seekBar.max = duration
            txtDuration.text = formatTime(duration)
            
            // Rewind button - 3 seconds
            btnRewind.setOnClickListener {
                val newPos = maxOf(videoView.currentPosition - 3000, 0)
                videoView.seekTo(newPos)
                showSeekIndicator(false)
            }
            
            // Forward button - 3 seconds
            btnForward.setOnClickListener {
                val newPos = minOf(videoView.currentPosition + 3000, videoView.duration)
                videoView.seekTo(newPos)
                showSeekIndicator(true)
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
                    if (videoView.isPlaying) {
                        seekBar.progress = videoView.currentPosition
                        txtCurrentTime.text = formatTime(videoView.currentPosition)
                    }
                    handler.postDelayed(this, 200)
                }
            }
            handler.post(updateRunnable)
        }
        
        private fun updatePlayPauseButton() {
            val iconRes = if (videoView.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            btnPlayPause.setImageResource(iconRes)
        }
        
        private fun toggleControls() {
            controlsVisible = !controlsVisible
            val targetAlpha = if (controlsVisible) 1f else 0f
            customControls.animate()
                .alpha(targetAlpha)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withStartAction { 
                    if (controlsVisible) customControls.visibility = View.VISIBLE 
                }
                .withEndAction {
                    if (!controlsVisible) customControls.visibility = View.GONE
                }
                .start()
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
        
        private fun startReversePlayback() {
            isReverseMode = true
            videoView.pause()
            
            reverseHandler = Handler(Looper.getMainLooper())
            reverseRunnable = object : Runnable {
                override fun run() {
                    if (isReverseMode && videoView.currentPosition > 0) {
                        // Seek backward by ~100ms for smooth reverse effect
                        val newPos = maxOf(videoView.currentPosition - 200, 0)
                        videoView.seekTo(newPos)
                        reverseHandler?.postDelayed(this, 50)
                    }
                }
            }
            reverseHandler?.post(reverseRunnable!!)
        }
        
        private fun stopReversePlayback() {
            isReverseMode = false
            reverseRunnable?.let { reverseHandler?.removeCallbacks(it) }
            reverseHandler = null
            reverseRunnable = null
            
            // Resume normal playback
            if (!videoView.isPlaying) {
                videoView.start()
            }
        }

        private fun showSeekIndicator(forward: Boolean) {
            val indicator = if (forward) rightSeekIndicator else leftSeekIndicator
            val otherIndicator = if (forward) leftSeekIndicator else rightSeekIndicator
            
            // Hide the other indicator
            otherIndicator.visibility = View.GONE
            
            // Show with animation
            indicator.visibility = View.VISIBLE
            indicator.alpha = 0f
            indicator.scaleX = 0.5f
            indicator.scaleY = 0.5f
            
            indicator.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    // Fade out after showing
                    handler.postDelayed({
                        indicator.animate()
                            .alpha(0f)
                            .scaleX(0.8f)
                            .scaleY(0.8f)
                            .setDuration(200)
                            .withEndAction { indicator.visibility = View.GONE }
                            .start()
                    }, 400)
                }
                .start()
        }

        private fun showSpeedIndicator(text: String) {
            speedIndicator.text = text
            speedIndicator.visibility = View.VISIBLE
            speedIndicator.alpha = 1f
        }

        private fun hideSpeedIndicator() {
            speedIndicator.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    speedIndicator.visibility = View.GONE
                    speedIndicator.alpha = 1f
                }
                .start()
        }

        private fun hideAllIndicators() {
            leftSeekIndicator.visibility = View.GONE
            rightSeekIndicator.visibility = View.GONE
            speedIndicator.visibility = View.GONE
        }

        fun cleanup() {
            gestureHandler?.cleanup()
            gestureHandler = null
            handler.removeCallbacksAndMessages(null)
            stopReversePlayback()
            videoView.stopPlayback()
            mediaPlayer = null
        }
    }
}
