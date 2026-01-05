package com.statussaver.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.statussaver.app.R
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusSource
import com.statussaver.app.data.repository.StatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FullScreenViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_FILE_TYPE = "file_type"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_IS_DOWNLOADED = "is_downloaded"
        
        // New extras for swipe navigation
        const val EXTRA_MEDIA_ITEMS = "media_items"
        const val EXTRA_CURRENT_POSITION = "current_position"
        
        private const val FAB_AUTO_HIDE_DELAY_MS = 2000L
    }

    // Single item mode properties
    private var filePath: String? = null
    private var fileUri: String? = null
    private var fileName: String? = null
    private var fileType: FileType = FileType.IMAGE
    private var source: StatusSource = StatusSource.LIVE
    private var isDownloaded: Boolean = false

    // ViewPager mode properties
    private var mediaItems: ArrayList<MediaItem>? = null
    private var currentPosition: Int = 0

    // Views
    private lateinit var viewPager: ViewPager2
    private lateinit var imageView: ImageView
    private lateinit var videoView: VideoView
    private lateinit var fabDownload: FloatingActionButton
    private lateinit var fabShare: FloatingActionButton
    private lateinit var pageIndicator: TextView

    private lateinit var repository: StatusRepository
    private var adapter: FullScreenMediaAdapter? = null
    
    // Auto-hide mechanism
    private val hideHandler = Handler(Looper.getMainLooper())
    private var fabsVisible = true
    private val hideRunnable = Runnable { hideFabs() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_view)

        repository = StatusRepository(this)

        // Find views
        viewPager = findViewById(R.id.viewPager)
        imageView = findViewById(R.id.fullScreenImage)
        videoView = findViewById(R.id.fullScreenVideo)
        fabDownload = findViewById(R.id.fabDownload)
        fabShare = findViewById(R.id.fabShare)
        pageIndicator = findViewById(R.id.pageIndicator)

        // Check for new multi-item mode
        @Suppress("DEPRECATION")
        mediaItems = intent.getParcelableArrayListExtra(EXTRA_MEDIA_ITEMS)
        currentPosition = intent.getIntExtra(EXTRA_CURRENT_POSITION, 0)

        if (mediaItems != null && mediaItems!!.isNotEmpty()) {
            setupViewPagerMode()
        } else {
            // Fallback to single item mode for backward compatibility
            setupSingleItemMode()
        }

        setupButtons()
    }

    private fun setupViewPagerMode() {
        val items = mediaItems ?: return
        
        viewPager.visibility = View.VISIBLE
        imageView.visibility = View.GONE
        videoView.visibility = View.GONE

        adapter = FullScreenMediaAdapter(
            items = items,
            onDownloadStateChanged = { position, downloaded ->
                items[position].isDownloaded = downloaded
                updateDownloadButton()
            },
            onControlsVisibilityChanged = { visible ->
                // Sync FAB visibility with video controls
                if (visible) {
                    showFabs()
                } else {
                    hideFabs()
                }
            }
        )

        viewPager.adapter = adapter
        viewPager.setCurrentItem(currentPosition, false)

        // Show page indicator if more than 1 item
        if (items.size > 1) {
            pageIndicator.visibility = View.VISIBLE
            updatePageIndicator()
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                
                // Pause previous video
                adapter?.pauseCurrentVideo()
                
                currentPosition = position
                updatePageIndicator()
                updateDownloadButton()
                
                // Show FABs when swiping and schedule auto-hide for videos
                showFabs()
                scheduleAutoHideIfVideo()
            }
        })

        updateDownloadButton()
        
        // Schedule auto-hide if starting on a video
        scheduleAutoHideIfVideo()
    }
    
    private fun scheduleAutoHideIfVideo() {
        val currentItem = getCurrentItem()
        if (currentItem?.fileType == FileType.VIDEO) {
            hideHandler.removeCallbacks(hideRunnable)
            hideHandler.postDelayed(hideRunnable, FAB_AUTO_HIDE_DELAY_MS)
        }
    }
    
    private fun showFabs() {
        hideHandler.removeCallbacks(hideRunnable)
        
        if (!fabsVisible) {
            fabsVisible = true
            fabDownload.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withStartAction { fabDownload.visibility = View.VISIBLE }
                .start()
            fabShare.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withStartAction { fabShare.visibility = View.VISIBLE }
                .start()
            pageIndicator.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
        
        // Always reschedule auto-hide for videos
        scheduleAutoHideIfVideo()
    }
    
    private fun hideFabs() {
        // Only hide for videos
        val currentItem = getCurrentItem()
        if (currentItem?.fileType == FileType.VIDEO) {
            fabsVisible = false
            fabDownload.animate()
                .alpha(0f)
                .translationY(100f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { fabDownload.visibility = View.GONE }
                .start()
            fabShare.animate()
                .alpha(0f)
                .translationY(100f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { fabShare.visibility = View.GONE }
                .start()
            pageIndicator.animate()
                .alpha(0f)
                .setDuration(200)
                .start()
        }
    }

    private fun setupSingleItemMode() {
        // Get extras (backward compatibility)
        filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        fileUri = intent.getStringExtra(EXTRA_FILE_URI)
        fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        fileType = FileType.valueOf(intent.getStringExtra(EXTRA_FILE_TYPE) ?: FileType.IMAGE.name)
        source = StatusSource.valueOf(intent.getStringExtra(EXTRA_SOURCE) ?: StatusSource.LIVE.name)
        isDownloaded = intent.getBooleanExtra(EXTRA_IS_DOWNLOADED, false)

        viewPager.visibility = View.GONE
        pageIndicator.visibility = View.GONE

        if (fileType == FileType.VIDEO) {
            setupVideo()
        } else {
            setupImage()
        }

        updateDownloadButton()
    }

    private fun setupImage() {
        imageView.visibility = View.VISIBLE
        videoView.visibility = View.GONE

        when (source) {
            StatusSource.LIVE -> {
                val uri = Uri.parse(fileUri)
                imageView.load(uri) {
                    crossfade(true)
                }
            }
            else -> {
                val file = File(filePath ?: return)
                imageView.load(file) {
                    crossfade(true)
                }
            }
        }
    }

    private fun setupVideo() {
        imageView.visibility = View.GONE
        videoView.visibility = View.VISIBLE

        val uri = when (source) {
            StatusSource.LIVE -> Uri.parse(fileUri)
            else -> {
                val file = File(filePath ?: return)
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            }
        }

        videoView.setVideoURI(uri)

        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            videoView.start()
        }
    }

    private fun updatePageIndicator() {
        val items = mediaItems ?: return
        pageIndicator.text = "${currentPosition + 1} / ${items.size}"
    }

    private fun setupButtons() {
        fabDownload.setOnClickListener {
            showFabs() // Keep visible when interacting
            if (!getCurrentDownloadState()) {
                downloadStatus()
            }
        }

        fabShare.setOnClickListener {
            showFabs() // Keep visible when interacting
            shareStatus()
        }
    }

    private fun getCurrentItem(): MediaItem? {
        return mediaItems?.getOrNull(currentPosition)
    }

    private fun getCurrentDownloadState(): Boolean {
        return if (mediaItems != null) {
            getCurrentItem()?.let { it.isDownloaded || it.source == StatusSource.SAVED } ?: false
        } else {
            isDownloaded || source == StatusSource.SAVED
        }
    }

    private fun updateDownloadButton() {
        if (getCurrentDownloadState()) {
            fabDownload.setImageResource(R.drawable.ic_check)
            fabDownload.isEnabled = false
            fabDownload.alpha = 0.8f
        } else {
            fabDownload.setImageResource(R.drawable.ic_download)
            fabDownload.isEnabled = true
            fabDownload.alpha = 1.0f
        }
    }

    private fun downloadStatus() {
        val currentItem = getCurrentItem()
        val itemFileName = currentItem?.filename ?: fileName ?: return
        val itemUri = currentItem?.uri ?: fileUri ?: return

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                repository.saveStatus(itemFileName, itemUri)
            }

            if (result) {
                if (currentItem != null) {
                    currentItem.isDownloaded = true
                } else {
                    isDownloaded = true
                }
                updateDownloadButton()
                Toast.makeText(this@FullScreenViewActivity, "Saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@FullScreenViewActivity, "Failed to save", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareStatus() {
        val currentItem = getCurrentItem()
        
        val shareUri = if (currentItem != null) {
            when (currentItem.source) {
                StatusSource.LIVE -> Uri.parse(currentItem.uri)
                else -> {
                    val file = File(currentItem.path)
                    FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                }
            }
        } else {
            when (source) {
                StatusSource.LIVE -> Uri.parse(fileUri)
                else -> {
                    val file = File(filePath ?: return)
                    FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                }
            }
        }

        val itemType = currentItem?.fileType ?: fileType
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (itemType == FileType.VIDEO) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    override fun onPause() {
        super.onPause()
        hideHandler.removeCallbacks(hideRunnable)
        if (mediaItems != null) {
            adapter?.pauseCurrentVideo()
        } else if (fileType == FileType.VIDEO) {
            videoView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacksAndMessages(null)
        adapter?.releaseCurrentVideo()
    }
}
