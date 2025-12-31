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
import com.statussaver.app.data.database.BackedUpStatus
import com.statussaver.app.data.database.FileType
import java.io.File

class StatusAdapter(
    private val onItemClick: (BackedUpStatus) -> Unit,
    private val onItemLongClick: (BackedUpStatus) -> Boolean
) : ListAdapter<BackedUpStatus, StatusAdapter.StatusViewHolder>(StatusDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_status, parent, false)
        return StatusViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StatusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.statusImage)
        private val videoIndicator: ImageView = itemView.findViewById(R.id.videoIndicator)

        fun bind(status: BackedUpStatus) {
            val file = File(status.backupPath)
            
            if (status.fileType == FileType.VIDEO) {
                videoIndicator.visibility = View.VISIBLE
                imageView.load(file) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder)
                    error(R.drawable.placeholder)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                }
            } else {
                videoIndicator.visibility = View.GONE
                imageView.load(file) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder)
                    error(R.drawable.placeholder)
                }
            }

            itemView.setOnClickListener { onItemClick(status) }
            itemView.setOnLongClickListener { onItemLongClick(status) }
        }
    }

    class StatusDiffCallback : DiffUtil.ItemCallback<BackedUpStatus>() {
        override fun areItemsTheSame(oldItem: BackedUpStatus, newItem: BackedUpStatus): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BackedUpStatus, newItem: BackedUpStatus): Boolean {
            return oldItem == newItem
        }
    }
}
