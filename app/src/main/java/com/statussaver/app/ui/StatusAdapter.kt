package com.statussaver.app.ui

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
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
    private val onShareClick: ((StatusItem) -> Unit)? = null
) : ListAdapter<StatusAdapter.StatusItem, StatusAdapter.StatusViewHolder>(StatusDiffCallback()) {

    private var downloadedFilenames: Set<String> = emptySet()
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    
    // SelectionTracker integration
    var selectionTracker: SelectionTracker<Long>? = null
    
    init {
        setHasStableIds(true)
    }
    
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
    )
    
    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }
    
    fun getPositionForId(id: Long): Int {
        return currentList.indexOfFirst { it.id == id }
    }
    
    fun getItemForId(id: Long): StatusItem? {
        return currentList.find { it.id == id }
    }
    
    fun getSelectedItems(): List<StatusItem> {
        val tracker = selectionTracker ?: return emptyList()
        return tracker.selection.mapNotNull { id -> getItemForId(id) }
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
        val isSelected = selectionTracker?.isSelected(item.id) ?: false
        val isInSelectionMode = selectionTracker?.hasSelection() ?: false
        holder.bind(updatedItem, isInSelectionMode, isSelected)
    }

    inner class StatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.statusImage)
        private val videoIndicator: ImageView = itemView.findViewById(R.id.videoIndicator)
        private val btnDownload: FloatingActionButton = itemView.findViewById(R.id.btnDownload)
        private val btnShare: FloatingActionButton? = itemView.findViewById(R.id.btnShare)
        private val checkMark: ImageView = itemView.findViewById(R.id.checkMark)
        private val selectionCheckMark: ImageView? = itemView.findViewById(R.id.selectionCheckMark)
        private val cacheInfoLayout: LinearLayout = itemView.findViewById(R.id.cacheInfoLayout)
        private val txtCacheDate: TextView = itemView.findViewById(R.id.txtCacheDate)
        private val txtExpiryDate: TextView = itemView.findViewById(R.id.txtExpiryDate)
        private val selectionOverlay: View? = itemView.findViewById(R.id.selectionOverlay)
        private val cardView: com.google.android.material.card.MaterialCardView = 
            itemView.findViewById(R.id.statusCard)

        private var currentItem: StatusItem? = null

        fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> =
            object : ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition(): Int = bindingAdapterPosition
                override fun getSelectionKey(): Long? = currentItem?.id
            }

        fun bind(item: StatusItem, inSelectionMode: Boolean, isSelected: Boolean) {
            currentItem = item
            
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
            
            // Apply selection visual feedback
            applySelectionState(isSelected, inSelectionMode)
            
            // Handle button visibility
            if (inSelectionMode) {
                // Hide action buttons in selection mode
                btnDownload.visibility = View.GONE
                btnShare?.visibility = View.GONE
                checkMark.visibility = View.GONE
            } else {
                // Normal mode - show appropriate buttons
                if (item.source == StatusSource.SAVED) {
                    // Saved tab: show share button
                    btnDownload.visibility = View.GONE
                    checkMark.visibility = View.GONE
                    btnShare?.visibility = View.VISIBLE
                    btnShare?.setOnClickListener { onShareClick?.invoke(item) }
                } else if (item.isDownloaded) {
                    // Downloaded: show checkmark
                    btnDownload.visibility = View.GONE
                    checkMark.visibility = View.VISIBLE
                    btnShare?.visibility = View.GONE
                } else {
                    // Not downloaded: show download button
                    btnDownload.visibility = View.VISIBLE
                    checkMark.visibility = View.GONE
                    btnShare?.visibility = View.GONE
                    btnDownload.setOnClickListener { onDownloadClick(item) }
                }
                
                // Normal click opens fullscreen
                itemView.setOnClickListener { 
                    if (selectionTracker?.hasSelection() != true) {
                        onItemClick(item) 
                    }
                }
            }
        }
        
        private fun applySelectionState(isSelected: Boolean, inSelectionMode: Boolean) {
            // Animate scale
            val targetScale = if (isSelected) 0.95f else 1.0f
            itemView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator())
                .start()
            
            // Thumbnail alpha
            imageView.alpha = if (isSelected) 0.7f else 1.0f
            
            // Selection checkmark (top-right)
            selectionCheckMark?.let { checkmark ->
                if (inSelectionMode) {
                    checkmark.visibility = View.VISIBLE
                    if (isSelected) {
                        checkmark.setImageResource(R.drawable.ic_check_circle)
                        checkmark.alpha = 1f
                    } else {
                        checkmark.setImageResource(R.drawable.ic_circle_outline)
                        checkmark.alpha = 0.6f
                    }
                } else {
                    checkmark.visibility = View.GONE
                }
            }
            
            // Selection overlay
            selectionOverlay?.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // Card stroke/border
            val strokeWidth = if (isSelected) 4 else 0
            val strokeColor = if (isSelected) {
                cardView.context.getColor(R.color.app_green)
            } else {
                0
            }
            cardView.strokeWidth = strokeWidth
            cardView.strokeColor = strokeColor
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
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: StatusItem, newItem: StatusItem): Boolean {
            return oldItem == newItem
        }
    }
}
