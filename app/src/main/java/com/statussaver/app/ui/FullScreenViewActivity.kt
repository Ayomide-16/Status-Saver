package com.statussaver.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import coil.load
import com.statussaver.app.R
import com.statussaver.app.data.database.FileType
import java.io.File

class FullScreenViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_TYPE = "file_type"
        const val EXTRA_FILE_NAME = "file_name"
    }

    private var filePath: String? = null
    private var fileType: FileType = FileType.IMAGE
    private var fileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_view)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        fileType = FileType.valueOf(intent.getStringExtra(EXTRA_FILE_TYPE) ?: FileType.IMAGE.name)
        fileName = intent.getStringExtra(EXTRA_FILE_NAME)

        title = fileName ?: "Status"

        if (filePath == null) {
            Toast.makeText(this, "Error loading file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(filePath!!)
        
        if (fileType == FileType.VIDEO) {
            setupVideoView(file)
        } else {
            setupImageView(file)
        }
    }

    private fun setupImageView(file: File) {
        val imageView = findViewById<ImageView>(R.id.fullScreenImage)
        val videoView = findViewById<VideoView>(R.id.fullScreenVideo)
        
        imageView.visibility = android.view.View.VISIBLE
        videoView.visibility = android.view.View.GONE
        
        imageView.load(file) {
            crossfade(true)
        }
    }

    private fun setupVideoView(file: File) {
        val imageView = findViewById<ImageView>(R.id.fullScreenImage)
        val videoView = findViewById<VideoView>(R.id.fullScreenVideo)
        
        imageView.visibility = android.view.View.GONE
        videoView.visibility = android.view.View.VISIBLE
        
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        
        videoView.setVideoURI(uri)
        
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            videoView.start()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_full_screen, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_share -> {
                shareFile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareFile() {
        val file = File(filePath ?: return)
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (fileType == FileType.VIDEO) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }
}
