package com.statussaver.app.ui

import com.statussaver.app.data.database.StatusSource

/**
 * Callback interface for selection mode communication between fragments and MainActivity
 */
interface SelectionCallback {
    /**
     * Called when selection mode is entered
     */
    fun onEnterSelectionMode(source: StatusSource)
    
    /**
     * Called when selection count changes
     */
    fun onSelectionChanged(count: Int, source: StatusSource)
    
    /**
     * Called when selection mode is exited
     */
    fun onExitSelectionMode()
    
    /**
     * Called by MainActivity when Save action is clicked
     */
    fun onSaveSelectedClicked()
    
    /**
     * Called by MainActivity when Share action is clicked
     */
    fun onShareSelectedClicked()
    
    /**
     * Called by MainActivity when Delete action is clicked
     */
    fun onDeleteSelectedClicked()
    
    /**
     * Called by MainActivity when Select All is clicked
     */
    fun onSelectAllClicked()
    
    /**
     * Called by MainActivity when Close/Cancel is clicked
     */
    fun onCancelSelectionClicked()
}
