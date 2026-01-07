package com.statussaver.app.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusEntity
import com.statussaver.app.data.database.StatusSource
import com.statussaver.app.data.repository.StatusRepository
import com.statussaver.app.util.SAFHelper
import kotlinx.coroutines.launch

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StatusRepository(application)
    
    // ========== Loading State ==========
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // ========== Downloaded Filenames ==========
    private val _downloadedFilenames = MutableLiveData<Set<String>>(emptySet())
    val downloadedFilenames: LiveData<Set<String>> = _downloadedFilenames
    
    // ========== Toast Messages ==========
    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message
    
    // ========== Permission State ==========
    private val _hasPermission = MutableLiveData(false)
    val hasPermission: LiveData<Boolean> = _hasPermission
    
    // ========== Live Status Data ==========
    private val _liveImages = MutableLiveData<List<StatusRepository.StatusFile>>(emptyList())
    private val _liveVideos = MutableLiveData<List<StatusRepository.StatusFile>>(emptyList())
    
    init {
        checkPermission()
        loadDownloadedFilenames()
        cleanupDuplicates()
    }
    
    fun checkPermission() {
        _hasPermission.value = SAFHelper.hasValidPermission(getApplication())
    }
    
    fun loadDownloadedFilenames() {
        viewModelScope.launch {
            _downloadedFilenames.value = repository.getAllDownloadedFilenames()
        }
    }
    
    private fun cleanupDuplicates() {
        viewModelScope.launch {
            repository.removeDuplicates()
        }
    }
    
    // ========== Live Statuses ==========
    
    fun getLiveStatuses(fileType: FileType): LiveData<List<StatusRepository.StatusFile>> {
        // Trigger refresh on first access
        if (_liveImages.value?.isEmpty() == true || _liveVideos.value?.isEmpty() == true) {
            refreshLiveStatuses()
        }
        return when (fileType) {
            FileType.IMAGE -> _liveImages
            FileType.VIDEO -> _liveVideos
        }
    }
    
    fun refreshLiveStatuses() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allStatuses = repository.getLiveStatuses()
                _liveImages.value = allStatuses.filter { it.fileType == FileType.IMAGE }
                _liveVideos.value = allStatuses.filter { it.fileType == FileType.VIDEO }
            } catch (e: Exception) {
                _message.value = "Error refreshing: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // ========== Saved Statuses ==========
    
    fun getSavedStatuses(fileType: FileType): LiveData<List<StatusEntity>> {
        return repository.getSavedStatusesByType(fileType)
    }
    
    // ========== Cached Statuses ==========
    
    fun getCachedStatuses(fileType: FileType): LiveData<List<StatusEntity>> {
        return repository.getCachedStatusesByType(fileType)
    }
    
    // ========== Refresh Data ==========
    
    fun refreshData(source: StatusSource, fileType: FileType) {
        when (source) {
            StatusSource.LIVE -> refreshLiveStatuses()
            else -> {
                // Room LiveData auto-updates
                _isLoading.value = false
            }
        }
        loadDownloadedFilenames()
    }
    
    // ========== Save/Download Status ==========
    
    fun saveStatus(filename: String, uri: String, source: StatusSource) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = if (source == StatusSource.CACHED) {
                    // For cached files, use direct query instead of LiveData.value
                    val cachedStatus = repository.getCachedStatusByFilename(filename)
                    if (cachedStatus != null) {
                        repository.saveCachedStatus(cachedStatus)
                    } else {
                        // Fallback: try to save using the URI directly
                        repository.saveStatus(filename, uri)
                    }
                } else {
                    repository.saveStatus(filename, uri)
                }
                
                if (success) {
                    _message.value = "Saved successfully!"
                    loadDownloadedFilenames()
                } else {
                    _message.value = "Failed to save"
                }
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // ========== Delete Status ==========
    
    fun deleteStatus(status: StatusEntity) {
        viewModelScope.launch {
            repository.deleteStatus(status)
            loadDownloadedFilenames()
        }
    }
    
    // ========== Check Download State ==========
    
    fun isDownloaded(filename: String): Boolean {
        return _downloadedFilenames.value?.contains(filename) ?: false
    }
    
    // ========== Clear Message ==========
    
    fun clearMessage() {
        _message.value = null
    }
}
