package com.statussaver.app.ui

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
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

        private var gestureHandler: VideoGestureHandler? = null
        private var mediaController: MediaController? = null
        private val handler = Handler(Looper.getMainLooper())
        private var mediaPlayer: MediaPlayer? = null

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

            // Setup media controller
            mediaController = MediaController(itemView.context).apply {
                setAnchorView(videoView)
            }
            videoView.setMediaController(mediaController)

            videoView.setOnPreparedListener { mp ->
                mediaPlayer = mp
                currentMediaPlayer = mp
                mp.isLooping = false
                videoView.start()
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

                    override fun onSpeedChange(speed: Float) {
                        setPlaybackSpeed(speed)
                        showSpeedIndicator(speed)
                    }

                    override fun onSpeedReset() {
                        setPlaybackSpeed(1.0f)
                        hideSpeedIndicator()
                    }

                    override fun onSingleTap() {
                        // Toggle media controller visibility
                        mediaController?.let {
                            if (it.isShowing) {
                                it.hide()
                            } else {
                                it.show(3000)
                            }
                        }
                    }
                }
            )

            touchOverlay.setOnTouchListener { _, event ->
                gestureHandler?.onTouchEvent(event) ?: false
            }
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

        private fun showSeekIndicator(forward: Boolean) {
            val indicator = if (forward) rightSeekIndicator else leftSeekIndicator
            indicator.visibility = View.VISIBLE
            indicator.alpha = 1f
            
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                indicator.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction { indicator.visibility = View.GONE }
                    .start()
            }, 500)
        }

        private fun showSpeedIndicator(speed: Float) {
            speedIndicator.text = "${speed}x"
            speedIndicator.visibility = View.VISIBLE
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
            videoView.stopPlayback()
            mediaController = null
            mediaPlayer = null
        }
    }
}
