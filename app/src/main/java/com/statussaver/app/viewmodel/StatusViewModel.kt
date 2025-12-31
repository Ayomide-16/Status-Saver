package com.statussaver.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.statussaver.app.data.database.BackedUpStatus
import com.statussaver.app.data.repository.StatusRepository
import com.statussaver.app.util.SAFHelper
import com.statussaver.app.worker.StatusBackupWorker
import kotlinx.coroutines.launch

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StatusRepository(application)
    
    val allBackups: LiveData<List<BackedUpStatus>> = repository.getAllBackups()
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _backupResult = MutableLiveData<Pair<Int, Int>?>()
    val backupResult: LiveData<Pair<Int, Int>?> = _backupResult
    
    private val _hasValidPermission = MutableLiveData(false)
    val hasValidPermission: LiveData<Boolean> = _hasValidPermission
    
    init {
        checkPermission()
    }
    
    /**
     * Check if we have valid SAF permission
     */
    fun checkPermission() {
        _hasValidPermission.value = SAFHelper.hasValidPermission(getApplication())
    }
    
    /**
     * Trigger a manual backup
     */
    fun triggerBackup() {
        if (_isLoading.value == true) return
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = repository.performFullBackup()
                _backupResult.value = result
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Delete a backup
     */
    fun deleteBackup(status: BackedUpStatus) {
        viewModelScope.launch {
            repository.deleteBackup(status)
        }
    }
    
    /**
     * Schedule periodic backup
     */
    fun schedulePeriodicBackup() {
        StatusBackupWorker.schedulePeriodicBackup(getApplication())
    }
    
    /**
     * Run immediate backup via WorkManager
     */
    fun runImmediateBackup() {
        StatusBackupWorker.runImmediateBackup(getApplication())
    }
    
    /**
     * Clear backup result
     */
    fun clearBackupResult() {
        _backupResult.value = null
    }
}
