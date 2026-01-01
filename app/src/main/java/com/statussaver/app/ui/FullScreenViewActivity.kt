package com.statussaver.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
    }

    private var filePath: String? = null
    private var fileUri: String? = null
    private var fileName: String? = null
    private var fileType: FileType = FileType.IMAGE
    private var source: StatusSource = StatusSource.LIVE
    private var isDownloaded: Boolean = false

    private lateinit var imageView: ImageView
    private lateinit var videoView: VideoView
    private lateinit var fabDownload: FloatingActionButton
    private lateinit var fabShare: FloatingActionButton

    private lateinit var repository: StatusRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_view)

        repository = StatusRepository(this)

        // Get extras
        filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        fileUri = intent.getStringExtra(EXTRA_FILE_URI)
        fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        fileType = FileType.valueOf(intent.getStringExtra(EXTRA_FILE_TYPE) ?: FileType.IMAGE.name)
        source = StatusSource.valueOf(intent.getStringExtra(EXTRA_SOURCE) ?: StatusSource.LIVE.name)
        isDownloaded = intent.getBooleanExtra(EXTRA_IS_DOWNLOADED, false)

        // Find views
        imageView = findViewById(R.id.fullScreenImage)
        videoView = findViewById(R.id.fullScreenVideo)
        fabDownload = findViewById(R.id.fabDownload)
        fabShare = findViewById(R.id.fabShare)

        setupContent()
        setupButtons()
    }

    private fun setupContent() {
        if (fileType == FileType.VIDEO) {
            setupVideo()
        } else {
            setupImage()
        }
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

    private fun setupButtons() {
        // Update download button state
        updateDownloadButton()

        fabDownload.setOnClickListener {
            if (!isDownloaded) {
                downloadStatus()
            }
        }

        fabShare.setOnClickListener {
            shareStatus()
        }
    }

    private fun updateDownloadButton() {
        if (isDownloaded || source == StatusSource.SAVED) {
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
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                repository.saveStatus(fileName ?: "", fileUri ?: "")
            }

            if (result) {
                isDownloaded = true
                updateDownloadButton()
                Toast.makeText(this@FullScreenViewActivity, "Saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@FullScreenViewActivity, "Failed to save", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareStatus() {
        val shareUri = when (source) {
            StatusSource.LIVE -> Uri.parse(fileUri)
            else -> {
                val file = File(filePath ?: return)
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (fileType == FileType.VIDEO) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    override fun onPause() {
        super.onPause()
        if (fileType == FileType.VIDEO) {
            videoView.pause()
        }
    }
}
