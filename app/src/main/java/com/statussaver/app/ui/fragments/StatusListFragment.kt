package com.statussaver.app.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusSource
import com.statussaver.app.data.repository.StatusRepository
import com.statussaver.app.databinding.FragmentStatusListBinding
import com.statussaver.app.ui.FullScreenViewActivity
import com.statussaver.app.ui.MediaItem
import com.statussaver.app.ui.StatusAdapter
import com.statussaver.app.viewmodel.StatusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment for displaying status list (Images or Videos)
 */
class StatusListFragment : Fragment(), com.statussaver.app.ui.SelectionCallback {
    
    private var _binding: FragmentStatusListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: StatusViewModel by activityViewModels()
    private lateinit var adapter: StatusAdapter
    private lateinit var repository: StatusRepository
    
    private var statusSource: StatusSource = StatusSource.LIVE
    private var fileType: FileType = FileType.IMAGE
    
    companion object {
        private const val ARG_SOURCE = "source"
        private const val ARG_FILE_TYPE = "file_type"
        
        fun newInstance(source: StatusSource, fileType: FileType): StatusListFragment {
            return StatusListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SOURCE, source.name)
                    putString(ARG_FILE_TYPE, fileType.name)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            statusSource = StatusSource.valueOf(it.getString(ARG_SOURCE, StatusSource.LIVE.name))
            fileType = FileType.valueOf(it.getString(ARG_FILE_TYPE, FileType.IMAGE.name))
        }
        repository = StatusRepository(requireContext())
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        observeData()
    }
    
    override fun onResume() {
        super.onResume()
        // Register as selection callback with MainActivity
        (activity as? com.statussaver.app.MainActivity)?.setSelectionCallback(this)
        // Refresh download states when returning from fullscreen view
        refreshDownloadStates()
    }
    
    override fun onPause() {
        super.onPause()
        // Unregister callback when fragment is not visible
        (activity as? com.statussaver.app.MainActivity)?.setSelectionCallback(null)
    }
    
    private fun refreshDownloadStates() {
        lifecycleScope.launch {
            try {
                val downloadedFilenames = withContext(Dispatchers.IO) {
                    repository.getAllDownloadedFilenames()
                }
                adapter.updateDownloadedState(downloadedFilenames)
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }
    
    private fun setupRecyclerView() {
        adapter = StatusAdapter(
            showCacheInfo = (statusSource == StatusSource.CACHED),
            onItemClick = { item -> openFullScreen(item) },
            onDownloadClick = { item -> downloadStatus(item) },
            onShareClick = { item -> shareStatus(item) },
            onLongClick = { item -> 
                adapter.enterSelectionMode()
                adapter.toggleSelection(item)
                true
            },
            onSelectionChanged = { selectedItems ->
                updateSelectionUI(selectedItems)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@StatusListFragment.adapter
            setHasFixedSize(true)
        }
    }
    
    private fun shareStatus(item: StatusAdapter.StatusItem) {
        // Share directly instead of opening fullscreen view
        lifecycleScope.launch {
            try {
                val shareUri = withContext(Dispatchers.IO) {
                    getShareableUri(item)
                }
                
                if (shareUri == null) {
                    Toast.makeText(requireContext(), "Unable to share this file", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = if (item.fileType == FileType.VIDEO) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Share via"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error sharing file", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private suspend fun getShareableUri(item: StatusAdapter.StatusItem): android.net.Uri? = withContext(Dispatchers.IO) {
        try {
            // For live statuses, use SAF URI directly
            if (item.source == StatusSource.LIVE && item.uri.isNotEmpty()) {
                return@withContext android.net.Uri.parse(item.uri)
            }
            
            // For file paths
            if (item.path.isNotEmpty() && !item.path.startsWith("content://")) {
                val file = java.io.File(item.path)
                if (file.exists() && file.canRead()) {
                    return@withContext androidx.core.content.FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        file
                    )
                }
            }
            
            // For content:// URIs (MediaStore) - try to get actual file path
            if (item.path.startsWith("content://")) {
                val contentUri = android.net.Uri.parse(item.path)
                
                // Try to get actual file path
                val actualPath = getFilePathFromContentUri(contentUri)
                if (!actualPath.isNullOrEmpty()) {
                    val file = java.io.File(actualPath)
                    if (file.exists() && file.canRead()) {
                        return@withContext androidx.core.content.FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            file
                        )
                    }
                }
                
                // Fallback: copy to cache and share
                return@withContext copyToCache(contentUri, item.filename)
            }
            
            // Last resort: try the original URI
            if (item.uri.isNotEmpty()) {
                return@withContext android.net.Uri.parse(item.uri)
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getFilePathFromContentUri(uri: android.net.Uri): String? {
        try {
            val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
            requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
    
    private fun copyToCache(sourceUri: android.net.Uri, filename: String): android.net.Uri? {
        try {
            val cacheFile = java.io.File(requireContext().cacheDir, filename)
            requireContext().contentResolver.openInputStream(sourceUri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists()) {
                return androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    cacheFile
                )
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
    
    private fun updateSelectionUI(selectedItems: Set<StatusAdapter.StatusItem>) {
        val count = selectedItems.size
        val mainActivity = activity as? com.statussaver.app.MainActivity
        
        if (count > 0) {
            // Enter selection mode in MainActivity
            if (!adapter.isInSelectionMode()) {
                mainActivity?.enterSelectionMode(statusSource)
            }
            mainActivity?.updateSelectionCount(count)
        } else {
            // Exit selection mode
            mainActivity?.exitSelectionMode()
        }
    }
    
    // ========== SelectionCallback Implementation ==========
    
    override fun onEnterSelectionMode(source: StatusSource) {
        adapter.enterSelectionMode()
    }
    
    override fun onSelectionChanged(count: Int, source: StatusSource) {
        // Handled by updateSelectionUI
    }
    
    override fun onExitSelectionMode() {
        adapter.exitSelectionMode()
    }
    
    override fun onSaveSelectedClicked() {
        saveSelectedItems()
    }
    
    override fun onShareSelectedClicked() {
        shareSelectedItems()
    }
    
    override fun onDeleteSelectedClicked() {
        confirmDeleteSelectedItems()
    }
    
    override fun onSelectAllClicked() {
        adapter.selectAll()
    }
    
    override fun onCancelSelectionClicked() {
        adapter.exitSelectionMode()
    }
    
    private fun setupSelectionActionBar() {
        binding.btnCancelSelection.setOnClickListener {
            adapter.exitSelectionMode()
        }
        
        binding.btnSaveAll.setOnClickListener {
            saveSelectedItems()
        }
        
        binding.btnShareAll.setOnClickListener {
            shareSelectedItems()
        }
        
        binding.btnDeleteAll.setOnClickListener {
            confirmDeleteSelectedItems()
        }
    }
    
    private fun saveSelectedItems() {
        val items = adapter.getSelectedItems()
        if (items.isEmpty()) return
        
        items.forEach { item ->
            downloadStatus(item)
        }
        Toast.makeText(requireContext(), "Saving ${items.size} items...", Toast.LENGTH_SHORT).show()
        adapter.exitSelectionMode()
    }
    
    private fun shareSelectedItems() {
        val items = adapter.getSelectedItems()
        if (items.isEmpty()) return
        
        if (items.size == 1) {
            // Single item - use regular share
            shareStatus(items.first())
        } else {
            // Multiple items - create share intent with multiple files
            Toast.makeText(requireContext(), "Sharing ${items.size} items...", Toast.LENGTH_SHORT).show()
            // For now, share one by one (multi-share requires more complex handling)
            shareStatus(items.first())
        }
        adapter.exitSelectionMode()
    }
    
    private fun confirmDeleteSelectedItems() {
        val items = adapter.getSelectedItems()
        if (items.isEmpty()) return
        
        val count = items.size
        val message = if (statusSource == StatusSource.SAVED) {
            "Permanently delete $count saved status(es)? This cannot be undone."
        } else {
            "Remove $count status(es) from cache?"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Selected")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedItems(items)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteSelectedItems(items: List<StatusAdapter.StatusItem>) {
        lifecycleScope.launch {
            var deleted = 0
            items.forEach { item ->
                try {
                    when (item.source) {
                        StatusSource.SAVED -> {
                            // Delete from MediaStore/file system
                            val success = withContext(Dispatchers.IO) {
                                repository.deleteSavedStatus(item.id)
                            }
                            if (success) deleted++
                        }
                        StatusSource.CACHED -> {
                            // Delete from cache
                            val success = withContext(Dispatchers.IO) {
                                repository.deleteCachedStatus(item.id)
                            }
                            if (success) deleted++
                        }
                        else -> {
                            // Live statuses can't be deleted
                        }
                    }
                } catch (e: Exception) {
                    // Continue with other items
                }
            }
            
            Toast.makeText(requireContext(), "Deleted $deleted item(s)", Toast.LENGTH_SHORT).show()
            adapter.exitSelectionMode()
            refreshData()
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(
            com.statussaver.app.R.color.app_green
        )
        binding.swipeRefresh.setOnRefreshListener {
            refreshData()
        }
    }
    
    private fun refreshData() {
        viewModel.refreshData(statusSource, fileType)
        binding.swipeRefresh.isRefreshing = false
    }
    
    private fun observeData() {
        when (statusSource) {
            StatusSource.LIVE -> observeLiveStatuses()
            StatusSource.SAVED -> observeSavedStatuses()
            StatusSource.CACHED -> observeCachedStatuses()
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }
        
        viewModel.downloadedFilenames.observe(viewLifecycleOwner) { filenames ->
            adapter.updateDownloadedState(filenames)
        }
    }
    
    private fun observeLiveStatuses() {
        viewModel.getLiveStatuses(fileType).observe(viewLifecycleOwner) { statuses ->
            val items = statuses.map { 
                StatusAdapter.StatusItem(
                    id = it.filename.hashCode().toLong(),
                    filename = it.filename,
                    path = it.path,
                    uri = it.uri,
                    fileType = it.fileType,
                    source = StatusSource.LIVE,
                    isDownloaded = it.isDownloaded,
                    cachedAt = 0L,
                    expiresAt = 0L
                )
            }
            adapter.submitList(items)
            updateEmptyState(items.isEmpty())
        }
    }
    
    private fun observeSavedStatuses() {
        viewModel.getSavedStatuses(fileType).observe(viewLifecycleOwner) { statuses ->
            try {
                val items = statuses.mapNotNull { status ->
                    // Skip items with invalid paths
                    if (status.localPath.isNullOrEmpty() && status.originalUri.isNullOrEmpty()) {
                        return@mapNotNull null
                    }
                    
                    StatusAdapter.StatusItem(
                        id = status.id,
                        filename = status.filename,
                        path = status.localPath ?: "",
                        uri = status.originalUri ?: "",
                        fileType = status.fileType,
                        source = StatusSource.SAVED,
                        isDownloaded = true,
                        cachedAt = status.savedAt,
                        expiresAt = 0L // Saved files don't expire
                    )
                }
                adapter.submitList(items)
                updateEmptyState(items.isEmpty())
            } catch (e: Exception) {
                updateEmptyState(true)
            }
        }
    }
    
    private fun observeCachedStatuses() {
        viewModel.getCachedStatuses(fileType).observe(viewLifecycleOwner) { statuses ->
            try {
                val retentionDays = com.statussaver.app.util.Constants.getRetentionDays(requireContext())
                val retentionMs = retentionDays * 24L * 60L * 60L * 1000L
                
                // Remove duplicates by filename
                val uniqueStatuses = statuses.distinctBy { it.filename }
                
                val items = uniqueStatuses.mapNotNull { status ->
                    // Skip items with invalid paths
                    if (status.localPath.isNullOrEmpty()) {
                        return@mapNotNull null
                    }
                    
                    StatusAdapter.StatusItem(
                        id = status.id,
                        filename = status.filename,
                        path = status.localPath ?: "",
                        uri = status.originalUri ?: "",
                        fileType = status.fileType,
                        source = StatusSource.CACHED,
                        isDownloaded = viewModel.isDownloaded(status.filename),
                        cachedAt = status.savedAt,
                        expiresAt = status.savedAt + retentionMs
                    )
                }
                adapter.submitList(items)
                updateEmptyState(items.isEmpty())
            } catch (e: Exception) {
                updateEmptyState(true)
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun openFullScreen(item: StatusAdapter.StatusItem) {
        // Convert current list to MediaItem for swipe navigation
        val currentList = adapter.currentList
        val mediaItems = ArrayList(currentList.map { MediaItem.fromStatusItem(it) })
        val currentPosition = currentList.indexOfFirst { it.filename == item.filename && it.source == item.source }
        
        val intent = Intent(requireContext(), FullScreenViewActivity::class.java).apply {
            // Pass full list for swipe navigation
            putParcelableArrayListExtra(FullScreenViewActivity.EXTRA_MEDIA_ITEMS, mediaItems)
            putExtra(FullScreenViewActivity.EXTRA_CURRENT_POSITION, if (currentPosition >= 0) currentPosition else 0)
            
            // Keep legacy extras for backward compatibility
            putExtra(FullScreenViewActivity.EXTRA_FILE_PATH, item.path)
            putExtra(FullScreenViewActivity.EXTRA_FILE_URI, item.uri)
            putExtra(FullScreenViewActivity.EXTRA_FILE_NAME, item.filename)
            putExtra(FullScreenViewActivity.EXTRA_FILE_TYPE, item.fileType.name)
            putExtra(FullScreenViewActivity.EXTRA_SOURCE, item.source.name)
            putExtra(FullScreenViewActivity.EXTRA_IS_DOWNLOADED, item.isDownloaded)
        }
        startActivity(intent)
    }
    
    private fun downloadStatus(item: StatusAdapter.StatusItem) {
        if (item.isDownloaded) {
            Toast.makeText(requireContext(), "Already saved", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading
        Toast.makeText(requireContext(), "Saving...", Toast.LENGTH_SHORT).show()
        
        // Use ViewModel's saveStatus which properly handles MediaStore on Android 10+
        viewModel.saveStatus(item.filename, item.uri, item.source)
        
        // Observe the message for result
        viewModel.message.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        refreshData()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
