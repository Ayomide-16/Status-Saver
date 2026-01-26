package com.statussaver.app.ui.selection

import android.view.MotionEvent
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView
import com.statussaver.app.ui.StatusAdapter

/**
 * Maps touch events to items in the RecyclerView.
 * Required by SelectionTracker to determine which item was touched.
 */
class StatusItemDetailsLookup(
    private val recyclerView: RecyclerView
) : ItemDetailsLookup<Long>() {
    
    override fun getItemDetails(e: MotionEvent): ItemDetails<Long>? {
        val view = recyclerView.findChildViewUnder(e.x, e.y) ?: return null
        val holder = recyclerView.getChildViewHolder(view)
        
        return if (holder is StatusAdapter.StatusViewHolder) {
            holder.getItemDetails()
        } else {
            null
        }
    }
}
