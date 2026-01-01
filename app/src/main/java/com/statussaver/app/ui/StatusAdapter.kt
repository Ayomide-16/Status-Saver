package com.statussaver.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.decode.VideoFrameDecoder
import com.statussaver.app.R
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusSource
import java.io.File

class StatusAdapter(
    private val onItemClick: (StatusItem) -> Unit,
    private val onDownloadClick: (StatusItem) -> Unit
) : ListAdapter<StatusAdapter.StatusItem, StatusAdapter.StatusViewHolder>(StatusDiffCallback()) {

    private var downloadedFilenames: Set<String> = emptySet()
    
    data class StatusItem(
        val id: Long,
        val filename: String,
        val path: String,
        val uri: String,
        val fileType: FileType,
        val source: StatusSource,
        var isDownloaded: Boolean = false
    )
    
    fun updateDownloadedState(filenames: Set<String>) {
        downloadedFilenames = filenames
        // Refresh items to update download state
        currentList.forEachIndexed { index, item ->
            val newState = filenames.contains(item.filename)
            if (item.isDownloaded != newState) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_status, parent, false)
        return StatusViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        val item = getItem(position)
        // Update download state from latest downloaded filenames
        val updatedItem = item.copy(isDownloaded = downloadedFilenames.contains(item.filename) || item.source == StatusSource.SAVED)
        holder.bind(updatedItem)
    }

    inner class StatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.statusImage)
        private val videoIndicator: ImageView = itemView.findViewById(R.id.videoIndicator)
        private val btnDownload: ImageView = itemView.findViewById(R.id.btnDownload)
        private val checkMark: ImageView = itemView.findViewById(R.id.checkMark)

        fun bind(item: StatusItem) {
            // Load thumbnail
            when (item.source) {
                StatusSource.LIVE -> loadFromUri(item)
                else -> loadFromFile(item)
            }
            
            // Show video indicator for videos
            videoIndicator.visibility = if (item.fileType == FileType.VIDEO) View.VISIBLE else View.GONE
            
            // Show download button or checkmark
            if (item.isDownloaded || item.source == StatusSource.SAVED) {
                btnDownload.visibility = View.GONE
                checkMark.visibility = View.VISIBLE
            } else {
                btnDownload.visibility = View.VISIBLE
                checkMark.visibility = View.GONE
                btnDownload.setOnClickListener { onDownloadClick(item) }
            }
            
            itemView.setOnClickListener { onItemClick(item) }
        }
        
        private fun loadFromUri(item: StatusItem) {
            val uri = android.net.Uri.parse(item.uri)
            if (item.fileType == FileType.VIDEO) {
                imageView.load(uri) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder)
                    error(R.drawable.placeholder)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                }
            } else {
                imageView.load(uri) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder)
                    error(R.drawable.placeholder)
                }
            }
        }
        
        private fun loadFromFile(item: StatusItem) {
            val file = File(item.path)
            if (item.fileType == FileType.VIDEO) {
                imageView.load(file) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder)
                    error(R.drawable.placeholder)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                }
            } else {
                imageView.load(file) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder)
                    error(R.drawable.placeholder)
                }
            }
        }
    }

    class StatusDiffCallback : DiffUtil.ItemCallback<StatusItem>() {
        override fun areItemsTheSame(oldItem: StatusItem, newItem: StatusItem): Boolean {
            return oldItem.filename == newItem.filename && oldItem.source == newItem.source
        }

        override fun areContentsTheSame(oldItem: StatusItem, newItem: StatusItem): Boolean {
            return oldItem == newItem
        }
    }
}
