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
    private val onControlsVisibilityChanged: (Boolean) -> Unit = {},
    private val onZoomStateChanged: (Boolean) -> Unit = {}
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
        private var mediaPlayer: MediaPlayer? = null
        private var isReverseMode = false
        private var reverseRunnable: Runnable? = null
        private var controlsVisible = true
        private var savedPosition = 0
        private var seekBarUpdateRunnable: Runnable? = null

        private val hideControlsRunnable = Runnable {
            if (controlsVisible) {
                toggleControls(false)
            }
        }
        
        private val hideLeftAnimRunnable = Runnable {
            leftSeekIndicator.visibility = View.GONE
        }
        
        private val hideRightAnimRunnable = Runnable {
            rightSeekIndicator.visibility = View.GONE
        }
        
        // Video zoom state
        private var videoScaleFactor = 1.0f
        private val minScale = 1.0f
        private val maxScale = 3.0f
        private var scaleGestureDetector: ScaleGestureDetector? = null
        private var isZoomedIn = false

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
                            startFastForward()
                        } else {
                            startReversePlayback()
                        }
                    }

                    override fun onLongPressEnd() {
                        stopSpeedControl()
                    }

                    override fun onDoubleTapCenter() {
                        if (isZoomedIn) {
                            resetZoom()
                        } else {
                            togglePlayPause()
                        }
                    }

                    override fun onSingleTap() {
                        toggleControls()
                    }
                }
            )
            
            scaleGestureDetector = ScaleGestureDetector(
                itemView.context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val scaleDelta = (detector.scaleFactor - 1f) * 1.5f + 1f
                        videoScaleFactor *= scaleDelta
                        videoScaleFactor = videoScaleFactor.coerceIn(minScale, maxScale)
                        
                        videoView.scaleX = videoScaleFactor
                        videoView.scaleY = videoScaleFactor
                        
                        val wasZoomed = isZoomedIn
                        isZoomedIn = videoScaleFactor > 1.05f
                        if (wasZoomed != isZoomedIn) {
                            onZoomStateChanged(isZoomedIn)
                        }
                        return true
                    }
                    
                    override fun onScaleEnd(detector: ScaleGestureDetector) {
                        if (videoScaleFactor < 1.15f) {
                            videoView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(200)
                                .start()
                            videoScaleFactor = 1.0f
                            if (isZoomedIn) {
                                isZoomedIn = false
                                onZoomStateChanged(false)
                            }
                        }
                    }
                }
            )

            touchOverlay.setOnTouchListener { _, event ->
                scaleGestureDetector?.onTouchEvent(event)
                gestureHandler?.onTouchEvent(event) ?: false
            }
        }
        
        private fun performSeek(forward: Boolean) {
            try {
                val duration = videoView.duration
                if (duration <= 0) return
                
                val currentPos = videoView.currentPosition
                val seekAmount = 3000
                
                val newPos = if (forward) {
                    minOf(currentPos + seekAmount, duration)
                } else {
                    maxOf(currentPos - seekAmount, 0)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mediaPlayer != null) {
                    mediaPlayer?.seekTo(newPos.toLong(), MediaPlayer.SEEK_CLOSEST)
                } else {
                    videoView.seekTo(newPos)
                }
                
                showSeekAnimation(forward)
            } catch (e: Exception) {
                try {
                    videoView.seekTo(if (forward) videoView.currentPosition + 3000 else maxOf(videoView.currentPosition - 3000, 0))
                    showSeekAnimation(forward)
                } catch (_: Exception) { }
            }
        }
        
        private fun setupCustomControls() {
            val duration = videoView.duration
            seekBar.max = duration
            txtDuration.text = formatTime(duration)
            
            btnRewind.setOnClickListener { performSeek(false) }
            btnForward.setOnClickListener { performSeek(true) }
            
            btnPlayPause.setOnClickListener {
                togglePlayPause()
            }
            
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
            seekBarUpdateRunnable?.let { itemView.removeCallbacks(it) }
            
            seekBarUpdateRunnable = object : Runnable {
                override fun run() {
                    try {
                        if (videoView.isPlaying) {
                            seekBar.progress = videoView.currentPosition
                            txtCurrentTime.text = formatTime(videoView.currentPosition)
                        }
                        itemView.postDelayed(this, 200)
                    } catch (e: Exception) {}
                }
            }
            itemView.post(seekBarUpdateRunnable!!)
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
        
        private fun toggleControls(visible: Boolean? = null) {
            controlsVisible = visible ?: !controlsVisible
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
            
            onControlsVisibilityChanged(controlsVisible)
            
            itemView.removeCallbacks(hideControlsRunnable)
            if (controlsVisible) {
                itemView.postDelayed(hideControlsRunnable, 3000)
            }
        }
        
        private fun resetZoom() {
            if (isZoomedIn) {
                videoView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
                videoScaleFactor = 1.0f
                isZoomedIn = false
                onZoomStateChanged(false)
            }
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
                } catch (e: Exception) {}
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
            
            speedIndicator.text = "◀◀ 2x"
            speedIndicator.visibility = View.VISIBLE
            speedIndicator.alpha = 1f
            
            reverseRunnable = object : Runnable {
                override fun run() {
                    if (isReverseMode) {
                        try {
                            val current = videoView.currentPosition
                            if (current > 100) {
                                val newPos = maxOf(current - 150, 0)
                                videoView.seekTo(newPos)
                                itemView.postDelayed(this, 50)
                            } else {
                                isReverseMode = false
                            }
                        } catch (e: Exception) {
                            isReverseMode = false
                        }
                    }
                }
            }
            itemView.post(reverseRunnable!!)
        }
        
        private fun stopSpeedControl() {
            isReverseMode = false
            reverseRunnable?.let { itemView.removeCallbacks(it) }
            reverseRunnable = null
            
            setPlaybackSpeed(1.0f)
            
            if (!videoView.isPlaying) {
                videoView.start()
            }
            
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
            val indicator = if (forward) rightSeekIndicator else leftSeekIndicator
            val hideRunnable = if (forward) hideRightAnimRunnable else hideLeftAnimRunnable
            
            itemView.removeCallbacks(hideLeftAnimRunnable)
            itemView.removeCallbacks(hideRightAnimRunnable)
            
            leftSeekIndicator.clearAnimation()
            rightSeekIndicator.clearAnimation()
            leftSeekIndicator.visibility = View.GONE
            rightSeekIndicator.visibility = View.GONE
            
            indicator.alpha = 0f
            indicator.scaleX = 0.6f
            indicator.scaleY = 0.6f
            indicator.visibility = View.VISIBLE
            
            indicator.animate()
                .alpha(1f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(150)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction {
                    indicator.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    
                    itemView.postDelayed(hideRunnable, 350)
                }
                .start()
        }

        private fun resetIndicators() {
            itemView.removeCallbacks(hideLeftAnimRunnable)
            itemView.removeCallbacks(hideRightAnimRunnable)
            leftSeekIndicator.visibility = View.GONE
            rightSeekIndicator.visibility = View.GONE
            speedIndicator.visibility = View.GONE
            leftSeekIndicator.clearAnimation()
            rightSeekIndicator.clearAnimation()
        }

        fun cleanup() {
            gestureHandler?.cleanup()
            gestureHandler = null
            seekBarUpdateRunnable?.let { itemView.removeCallbacks(it) }
            seekBarUpdateRunnable = null
            itemView.removeCallbacks(hideControlsRunnable)
            itemView.removeCallbacks(hideLeftAnimRunnable)
            itemView.removeCallbacks(hideRightAnimRunnable)
            stopSpeedControl()
            videoView.stopPlayback()
            mediaPlayer = null
        }
    }
}
