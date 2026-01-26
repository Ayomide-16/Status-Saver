package com.statussaver.app.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.GridLayoutManager
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusSource
import com.statussaver.app.data.repository.StatusRepository
import com.statussaver.app.databinding.FragmentStatusListBinding
import com.statussaver.app.ui.FullScreenViewActivity
import com.statussaver.app.ui.MediaItem
import com.statussaver.app.ui.StatusAdapter
import com.statussaver.app.ui.selection.StatusItemDetailsLookup
import com.statussaver.app.ui.selection.StatusItemKeyProvider
import com.statussaver.app.viewmodel.StatusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment for displaying status list (Images or Videos)
 * Uses AndroidX RecyclerView Selection for professional multi-select
 */
class StatusListFragment : Fragment(), com.statussaver.app.ui.SelectionCallback {
    
    private var _binding: FragmentStatusListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: StatusViewModel by activityViewModels()
    private lateinit var adapter: StatusAdapter
    private lateinit var repository: StatusRepository
    private var selectionTracker: SelectionTracker<Long>? = null
    
    private var statusSource: StatusSource = StatusSource.LIVE
    private var fileType: FileType = FileType.IMAGE
    
    companion object {
        private const val ARG_SOURCE = "source"
        private const val ARG_FILE_TYPE = "file_type"
        private const val SELECTION_ID = "status-selection"
        
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
        setupSelectionTracker(savedInstanceState)
        setupSwipeRefresh()
        observeData()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectionTracker?.onSaveInstanceState(outState)
    }
    
    override fun onResume() {
        super.onResume()
        (activity as? com.statussaver.app.MainActivity)?.setSelectionCallback(this)
        refreshDownloadStates()
    }
    
    override fun onPause() {
        super.onPause()
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
            onItemClick = { item -> 
                // Only open fullscreen if not in selection mode
                if (selectionTracker?.hasSelection() != true) {
                    openFullScreen(item)
                }
            },
            onDownloadClick = { item -> downloadStatus(item) },
            onShareClick = { item -> shareStatus(item) }
        )
        
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@StatusListFragment.adapter
            setHasFixedSize(true)
        }
    }
    
    private fun setupSelectionTracker(savedInstanceState: Bundle?) {
        selectionTracker = SelectionTracker.Builder(
            SELECTION_ID,
            binding.recyclerView,
            StatusItemKeyProvider(adapter),
            StatusItemDetailsLookup(binding.recyclerView),
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything()
        ).build()
        
        adapter.selectionTracker = selectionTracker
        
        // Add selection observer to update UI
        selectionTracker?.addObserver(object : SelectionTracker.SelectionObserver<Long>() {
            override fun onSelectionChanged() {
                val count = selectionTracker?.selection?.size() ?: 0
                val mainActivity = activity as? com.statussaver.app.MainActivity
                
                if (count > 0) {
                    mainActivity?.enterSelectionMode(statusSource)
                    mainActivity?.updateSelectionCount(count)
                } else {
                    mainActivity?.exitSelectionMode()
                }
            }
        })
        
        // Restore selection state
        savedInstanceState?.let {
            selectionTracker?.onRestoreInstanceState(it)
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
        viewModel.refreshStatuses()
        binding.swipeRefresh.isRefreshing = false
    }
    
    // ========== SelectionCallback Implementation ==========
    
    override fun onEnterSelectionMode(source: StatusSource) {
        // Already handled by tracker observer
    }
    
    override fun onSelectionChanged(count: Int, source: StatusSource) {
        // Already handled by tracker observer
    }
    
    override fun onExitSelectionMode() {
        selectionTracker?.clearSelection()
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
        // Select all items
        val allIds = adapter.currentList.map { it.id }
        selectionTracker?.setItemsSelected(allIds, true)
    }
    
    override fun onCancelSelectionClicked() {
        selectionTracker?.clearSelection()
    }
    
    // ========== Selection Actions ==========
    
    private fun saveSelectedItems() {
        val items = adapter.getSelectedItems()
        if (items.isEmpty()) return
        
        lifecycleScope.launch {
            var savedCount = 0
            items.forEach { item ->
                val success = withContext(Dispatchers.IO) {
                    repository.saveStatus(item.filename, item.uri)
                }
                if (success) savedCount++
            }
            Toast.makeText(requireContext(), "$savedCount items saved", Toast.LENGTH_SHORT).show()
            selectionTracker?.clearSelection()
            refreshDownloadStates()
        }
    }
    
    private fun shareSelectedItems() {
        val items = adapter.getSelectedItems()
        if (items.isEmpty()) return
        
        lifecycleScope.launch {
            try {
                val uris = withContext(Dispatchers.IO) {
                    items.mapNotNull { item -> getShareableUri(item) }
                }
                
                if (uris.isEmpty()) {
                    Toast.makeText(requireContext(), "Unable to share files", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = if (items.all { it.fileType == FileType.IMAGE }) "image/*" 
                           else if (items.all { it.fileType == FileType.VIDEO }) "video/*"
                           else "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Share via"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error sharing files", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun confirmDeleteSelectedItems() {
        val items = adapter.getSelectedItems()
        if (items.isEmpty()) return
        
        val message = when (statusSource) {
            StatusSource.SAVED -> "Permanently delete ${items.size} saved status(es)?"
            else -> "Remove ${items.size} item(s) from cache?"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Delete")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ -> deleteSelectedItems() }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteSelectedItems() {
        val items = adapter.getSelectedItems()
        if (items.isEmpty()) return
        
        lifecycleScope.launch {
            var deletedCount = 0
            items.forEach { item ->
                val success = withContext(Dispatchers.IO) {
                    when (statusSource) {
                        StatusSource.SAVED -> repository.deleteSavedStatus(item.id)
                        else -> repository.deleteCachedStatus(item.id)
                    }
                }
                if (success) deletedCount++
            }
            Toast.makeText(requireContext(), "$deletedCount items deleted", Toast.LENGTH_SHORT).show()
            selectionTracker?.clearSelection()
        }
    }
    
    // ========== Share Helpers ==========
    
    private suspend fun getShareableUri(item: StatusAdapter.StatusItem): android.net.Uri? = withContext(Dispatchers.IO) {
        try {
            if (item.source == StatusSource.LIVE && item.uri.isNotEmpty()) {
                return@withContext android.net.Uri.parse(item.uri)
            }
            
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
            
            if (item.path.startsWith("content://")) {
                val contentUri = android.net.Uri.parse(item.path)
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
                return@withContext copyToCache(contentUri, item.filename)
            }
            
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
    
    // ========== Single Item Actions ==========
    
    private fun shareStatus(item: StatusAdapter.StatusItem) {
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
    
    private fun downloadStatus(item: StatusAdapter.StatusItem) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.saveStatus(item.filename, item.uri)
            }
            if (result) {
                Toast.makeText(requireContext(), "Saved!", Toast.LENGTH_SHORT).show()
                refreshDownloadStates()
            } else {
                Toast.makeText(requireContext(), "Failed to save", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openFullScreen(item: StatusAdapter.StatusItem) {
        val items = adapter.currentList.map { statusItem ->
            MediaItem(
                id = statusItem.id,
                filename = statusItem.filename,
                path = statusItem.path,
                uri = statusItem.uri,
                fileType = statusItem.fileType,
                source = statusItem.source,
                isDownloaded = statusItem.isDownloaded
            )
        }
        
        val position = adapter.currentList.indexOfFirst { it.id == item.id }
        
        val intent = Intent(requireContext(), FullScreenViewActivity::class.java).apply {
            putParcelableArrayListExtra(FullScreenViewActivity.EXTRA_MEDIA_ITEMS, ArrayList(items))
            putExtra(FullScreenViewActivity.EXTRA_CURRENT_POSITION, position)
        }
        startActivity(intent)
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    // ========== Observe Data ==========
    
    private fun observeData() {
        when (statusSource) {
            StatusSource.LIVE -> observeLiveStatuses()
            StatusSource.SAVED -> observeSavedStatuses()
            StatusSource.CACHED -> observeCachedStatuses()
        }
    }
    
    private fun observeLiveStatuses() {
        viewModel.getLiveStatuses(fileType).observe(viewLifecycleOwner) { statuses ->
            try {
                lifecycleScope.launch {
                    val downloadedFilenames = withContext(Dispatchers.IO) {
                        repository.getAllDownloadedFilenames()
                    }
                    
                    val items = statuses.mapNotNull { (filename, uri, type) ->
                        StatusAdapter.StatusItem(
                            id = filename.hashCode().toLong(),
                            filename = filename,
                            path = "",
                            uri = uri.toString(),
                            fileType = type,
                            source = StatusSource.LIVE,
                            isDownloaded = downloadedFilenames.contains(filename)
                        )
                    }
                    adapter.submitList(items)
                    updateEmptyState(items.isEmpty())
                }
            } catch (e: Exception) {
                updateEmptyState(true)
            }
        }
    }
    
    private fun observeSavedStatuses() {
        viewModel.getSavedStatuses(fileType).observe(viewLifecycleOwner) { statuses ->
            try {
                val items = statuses.mapNotNull { status ->
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
                        expiresAt = 0L
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
                
                val uniqueStatuses = statuses.distinctBy { it.filename }
                
                lifecycleScope.launch {
                    val downloadedFilenames = withContext(Dispatchers.IO) {
                        repository.getAllDownloadedFilenames()
                    }
                    
                    val items = uniqueStatuses.mapNotNull { status ->
                        if (status.localPath.isNullOrEmpty()) {
                            return@mapNotNull null
                        }
                        
                        val expiresAt = status.savedAt + retentionMs
                        
                        StatusAdapter.StatusItem(
                            id = status.id,
                            filename = status.filename,
                            path = status.localPath,
                            uri = status.originalUri ?: "",
                            fileType = status.fileType,
                            source = StatusSource.CACHED,
                            isDownloaded = downloadedFilenames.contains(status.filename),
                            cachedAt = status.savedAt,
                            expiresAt = expiresAt
                        )
                    }
                    adapter.submitList(items)
                    updateEmptyState(items.isEmpty())
                }
            } catch (e: Exception) {
                updateEmptyState(true)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
