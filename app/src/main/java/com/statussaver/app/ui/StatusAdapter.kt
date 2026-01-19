package com.statussaver.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.decode.VideoFrameDecoder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.statussaver.app.R
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusAdapter(
    private val showCacheInfo: Boolean = false,
    private val onItemClick: (StatusItem) -> Unit,
    private val onDownloadClick: (StatusItem) -> Unit,
    private val onShareClick: ((StatusItem) -> Unit)? = null,
    private val onLongClick: ((StatusItem) -> Boolean)? = null,
    private val onSelectionChanged: ((Set<StatusItem>) -> Unit)? = null
) : ListAdapter<StatusAdapter.StatusItem, StatusAdapter.StatusViewHolder>(StatusDiffCallback()) {

    private var downloadedFilenames: Set<String> = emptySet()
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    
    // Selection mode state
    private var selectionMode = false
    private val selectedItems = mutableSetOf<String>() // Track by filename+source
    
    data class StatusItem(
        val id: Long,
        val filename: String,
        val path: String,
        val uri: String,
        val fileType: FileType,
        val source: StatusSource,
        var isDownloaded: Boolean = false,
        val cachedAt: Long = 0L,
        val expiresAt: Long = 0L
    ) {
        val selectionKey: String get() = "${filename}_${source.name}"
    }
    
    fun updateDownloadedState(filenames: Set<String>) {
        downloadedFilenames = filenames
        currentList.forEachIndexed { index, item ->
            val newState = filenames.contains(item.filename) || item.source == StatusSource.SAVED
            if (item.isDownloaded != newState) {
                notifyItemChanged(index)
            }
        }
    }
    
    // ========== Selection Mode Methods ==========
    
    fun isInSelectionMode(): Boolean = selectionMode
    
    fun enterSelectionMode() {
        if (!selectionMode) {
            selectionMode = true
            notifyDataSetChanged()
        }
    }
    
    fun exitSelectionMode() {
        if (selectionMode) {
            selectionMode = false
            selectedItems.clear()
            notifyDataSetChanged()
            onSelectionChanged?.invoke(emptySet())
        }
    }
    
    fun toggleSelection(item: StatusItem) {
        val key = item.selectionKey
        if (selectedItems.contains(key)) {
            selectedItems.remove(key)
        } else {
            selectedItems.add(key)
        }
        notifyItemChanged(currentList.indexOfFirst { it.selectionKey == key })
        notifySelectionChanged()
    }
    
    fun selectAll() {
        currentList.forEach { selectedItems.add(it.selectionKey) }
        notifyDataSetChanged()
        notifySelectionChanged()
    }
    
    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
        notifySelectionChanged()
    }
    
    fun getSelectedItems(): List<StatusItem> {
        return currentList.filter { selectedItems.contains(it.selectionKey) }
    }
    
    fun getSelectedCount(): Int = selectedItems.size
    
    private fun notifySelectionChanged() {
        onSelectionChanged?.invoke(getSelectedItems().toSet())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_status, parent, false)
        return StatusViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        val item = getItem(position)
        val updatedItem = item.copy(
            isDownloaded = downloadedFilenames.contains(item.filename) || item.source == StatusSource.SAVED
        )
        holder.bind(updatedItem, selectionMode, selectedItems.contains(item.selectionKey))
    }

    inner class StatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.statusImage)
        private val videoIndicator: ImageView = itemView.findViewById(R.id.videoIndicator)
        private val btnDownload: FloatingActionButton = itemView.findViewById(R.id.btnDownload)
        private val btnShare: FloatingActionButton? = itemView.findViewById(R.id.btnShare)
        private val checkMark: ImageView = itemView.findViewById(R.id.checkMark)
        private val selectionCheckBox: CheckBox? = itemView.findViewById(R.id.selectionCheckBox)
        private val cacheInfoLayout: LinearLayout = itemView.findViewById(R.id.cacheInfoLayout)
        private val txtCacheDate: TextView = itemView.findViewById(R.id.txtCacheDate)
        private val txtExpiryDate: TextView = itemView.findViewById(R.id.txtExpiryDate)
        private val selectionOverlay: View? = itemView.findViewById(R.id.selectionOverlay)

        fun bind(item: StatusItem, inSelectionMode: Boolean, isSelected: Boolean) {
            // Load thumbnail
            when (item.source) {
                StatusSource.LIVE -> loadFromUri(item)
                else -> loadFromFile(item)
            }
            
            // Show video indicator for videos
            videoIndicator.visibility = if (item.fileType == FileType.VIDEO) View.VISIBLE else View.GONE
            
            // Show cache info for cached items
            if (showCacheInfo && item.cachedAt > 0) {
                cacheInfoLayout.visibility = View.VISIBLE
                txtCacheDate.text = "Cached: ${dateFormat.format(Date(item.cachedAt))}"
                if (item.expiresAt > 0) {
                    txtExpiryDate.text = "Expires: ${dateFormat.format(Date(item.expiresAt))}"
                    txtExpiryDate.visibility = View.VISIBLE
                } else {
                    txtExpiryDate.visibility = View.GONE
                }
            } else {
                cacheInfoLayout.visibility = View.GONE
            }
            
            // Handle selection mode UI
            if (inSelectionMode) {
                // Show selection checkbox
                selectionCheckBox?.visibility = View.VISIBLE
                selectionCheckBox?.isChecked = isSelected
                selectionOverlay?.visibility = if (isSelected) View.VISIBLE else View.GONE
                
                // Hide action buttons in selection mode
                btnDownload.visibility = View.GONE
                btnShare?.visibility = View.GONE
                checkMark.visibility = View.GONE
                
                // Click toggles selection
                itemView.setOnClickListener { toggleSelection(item) }
                itemView.setOnLongClickListener { true } // Consume long click
            } else {
                // Normal mode
                selectionCheckBox?.visibility = View.GONE
                selectionOverlay?.visibility = View.GONE
                
                // For Saved tab: show share button instead of checkmark
                if (item.source == StatusSource.SAVED) {
                    btnDownload.visibility = View.GONE
                    checkMark.visibility = View.GONE
                    btnShare?.visibility = View.VISIBLE
                    btnShare?.setOnClickListener { onShareClick?.invoke(item) }
                } else if (item.isDownloaded) {
                    // For other tabs with downloaded items
                    btnDownload.visibility = View.GONE
                    checkMark.visibility = View.VISIBLE
                    btnShare?.visibility = View.GONE
                } else {
                    // Not downloaded
                    btnDownload.visibility = View.VISIBLE
                    checkMark.visibility = View.GONE
                    btnShare?.visibility = View.GONE
                    btnDownload.setOnClickListener { onDownloadClick(item) }
                }
                
                itemView.setOnClickListener { onItemClick(item) }
                itemView.setOnLongClickListener { 
                    onLongClick?.invoke(item) ?: false
                }
            }
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
