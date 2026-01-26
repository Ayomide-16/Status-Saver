package com.statussaver.app.ui.selection

import androidx.recyclerview.selection.ItemKeyProvider
import com.statussaver.app.ui.StatusAdapter

/**
 * Provides stable unique keys for selection tracking.
 * Uses status item IDs which are stable and unique.
 */
class StatusItemKeyProvider(
    private val adapter: StatusAdapter
) : ItemKeyProvider<Long>(SCOPE_CACHED) {
    
    override fun getKey(position: Int): Long? {
        return if (position >= 0 && position < adapter.itemCount) {
            adapter.getItemId(position)
        } else {
            null
        }
    }
    
    override fun getPosition(key: Long): Int {
        return adapter.getPositionForId(key)
    }
}
