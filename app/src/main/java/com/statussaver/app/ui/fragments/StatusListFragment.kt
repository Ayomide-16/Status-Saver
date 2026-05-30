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
    
    private var currentItems: List<StatusAdapter.StatusItem> = emptyList()
    
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
        setupSelectionTracker(savedInstanceState)
        setupSwipeRefresh()
        observeData()
        observeDownloadedState()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectionTracker?.onSaveInstanceState(outState)
    }
    
    override fun onResume() {
        super.onResume()
        (activity as? com.statussaver.app.MainActivity)?.setSelectionCallback(this)
        viewModel.loadDownloadedFilenames() // Refresh state
    }
    
    override fun onPause() {
        super.onPause()
        (activity as? com.statussaver.app.MainActivity)?.setSelectionCallback(null)
    }
    
    private fun observeDownloadedState() {
        viewModel.downloadedFilenames.observe(viewLifecycleOwner) { filenames ->
            if (currentItems.isNotEmpty()) {
                currentItems = currentItems.map { item ->
                    val isDownloaded = filenames.contains("${item.filename}|${item.source}") || item.source == StatusSource.SAVED
                    if (item.isDownloaded != isDownloaded) item.copy(isDownloaded = isDownloaded) else item
                }
                adapter.submitList(currentItems)
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
        val selectionId = "selection-${statusSource.name}-${fileType.name}"
        selectionTracker = SelectionTracker.Builder(
            selectionId,
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
        viewModel.refreshData(statusSource, fileType)
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
        val itemsToSave = adapter.getSelectedItems()
        if (itemsToSave.isEmpty()) return
        
        val ctx = context ?: return
        lifecycleScope.launch {
            val savedCount = withContext(Dispatchers.IO) {
                var successCount = 0
                for (item in itemsToSave) {
                    val success = if (statusSource == StatusSource.CACHED) {
                        val cachedStatus = repository.getCachedStatusByFilename(item.filename)
                        if (cachedStatus != null) {
                            repository.saveCachedStatus(cachedStatus)
                        } else {
                            repository.saveStatus(item.filename, item.uri, item.source)
                        }
                    } else {
                        repository.saveStatus(item.filename, item.uri, item.source)
                    }
                    if (success) successCount++
                }
                successCount
            }
            Toast.makeText(ctx, "$savedCount items saved", Toast.LENGTH_SHORT).show()
            selectionTracker?.clearSelection()
            viewModel.loadDownloadedFilenames()
        }
    }
    
    private fun shareSelectedItems() {
        val itemsToShare = adapter.getSelectedItems()
        if (itemsToShare.isEmpty()) return
        
        val ctx = context ?: return
        lifecycleScope.launch {
            try {
                val uris = withContext(Dispatchers.IO) {
                    itemsToShare.mapNotNull { item -> getShareableUri(ctx, item) }
                }
                
                if (uris.isEmpty()) {
                    Toast.makeText(ctx, "Unable to share files", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Share via"))
                selectionTracker?.clearSelection()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error sharing files", Toast.LENGTH_SHORT).show()
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
        val itemsToDelete = adapter.getSelectedItems()
        if (itemsToDelete.isEmpty()) return
        
        val ctx = context ?: return
        lifecycleScope.launch {
            val deletedCount = withContext(Dispatchers.IO) {
                var count = 0
                itemsToDelete.forEach { item ->
                    val success = when (statusSource) {
                        StatusSource.SAVED -> repository.deleteSavedStatus(item.id)
                        else -> repository.deleteCachedStatus(item.id)
                    }
                    if (success) count++
                }
                count
            }
            Toast.makeText(ctx, "$deletedCount items deleted", Toast.LENGTH_SHORT).show()
            selectionTracker?.clearSelection()
        }
    }
    
    // ========== Share Helpers ==========
    
    private suspend fun getShareableUri(context: android.content.Context, item: StatusAdapter.StatusItem): android.net.Uri? = withContext(Dispatchers.IO) {
        try {
            if (item.source == StatusSource.LIVE && item.uri.isNotEmpty()) {
                return@withContext android.net.Uri.parse(item.uri)
            }
            
            if (item.path.isNotEmpty() && !item.path.startsWith("content://")) {
                val file = java.io.File(item.path)
                if (file.exists() && file.canRead()) {
                    return@withContext androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }
            
            if (item.path.startsWith("content://")) {
                val contentUri = android.net.Uri.parse(item.path)
                val actualPath = getFilePathFromContentUri(context, contentUri)
                if (!actualPath.isNullOrEmpty()) {
                    val file = java.io.File(actualPath)
                    if (file.exists() && file.canRead()) {
                        return@withContext androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    }
                }
                return@withContext copyToCache(context, contentUri, item.filename)
            }
            
            if (item.uri.isNotEmpty()) {
                return@withContext android.net.Uri.parse(item.uri)
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getFilePathFromContentUri(context: android.content.Context, uri: android.net.Uri): String? {
        try {
            val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
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
    
    private fun copyToCache(context: android.content.Context, sourceUri: android.net.Uri, filename: String): android.net.Uri? {
        try {
            val cacheFile = java.io.File(context.cacheDir, filename)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists()) {
                return androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
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
        val ctx = context ?: return
        lifecycleScope.launch {
            try {
                val shareUri = withContext(Dispatchers.IO) {
                    getShareableUri(ctx, item)
                }
                
                if (shareUri == null) {
                    Toast.makeText(ctx, "Unable to share this file", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = if (item.fileType == FileType.VIDEO) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Share via"))
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error sharing file", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun downloadStatus(item: StatusAdapter.StatusItem) {
        val ctx = context ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (item.source == StatusSource.CACHED) {
                    val cachedStatus = repository.getCachedStatusByFilename(item.filename)
                    if (cachedStatus != null) {
                        repository.saveCachedStatus(cachedStatus)
                    } else {
                        repository.saveStatus(item.filename, item.uri, item.source)
                    }
                } else {
                    repository.saveStatus(item.filename, item.uri, item.source)
                }
            }
            if (result) {
                Toast.makeText(ctx, "Saved!", Toast.LENGTH_SHORT).show()
                viewModel.loadDownloadedFilenames()
            } else {
                Toast.makeText(ctx, "Failed to save", Toast.LENGTH_SHORT).show()
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
        if (position < 0) return
        
        val intent = Intent(requireContext(), FullScreenViewActivity::class.java).apply {
            putExtra(FullScreenViewActivity.EXTRA_CURRENT_POSITION, position)
            putParcelableArrayListExtra(FullScreenViewActivity.EXTRA_MEDIA_ITEMS, ArrayList(items))
        }
        startActivity(intent)
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        
        if (isEmpty) {
            binding.emptyText.text = when (statusSource) {
                StatusSource.LIVE -> "No statuses found"
                StatusSource.SAVED -> "No saved statuses"
                StatusSource.CACHED -> "No recent statuses"
            }
        }
    }
    
    // ========== Observe Data ==========
    
    private fun observeData() {
        viewModel.isLoading(statusSource, fileType).observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }
        
        when (statusSource) {
            StatusSource.LIVE -> observeLiveStatuses()
            StatusSource.SAVED -> observeSavedStatuses()
            StatusSource.CACHED -> observeCachedStatuses()
        }
    }
    
    private fun observeLiveStatuses() {
        viewModel.getLiveStatuses(fileType).observe(viewLifecycleOwner) { statuses ->
            try {
                val downloadedFilenames = viewModel.downloadedFilenames.value ?: emptySet()
                currentItems = statuses.mapNotNull { file ->
                    StatusAdapter.StatusItem(
                        id = java.util.UUID.nameUUIDFromBytes(file.filename.toByteArray()).mostSignificantBits,
                        filename = file.filename,
                        path = file.path,
                        uri = file.uri,
                        fileType = file.fileType,
                        source = StatusSource.LIVE,
                        isDownloaded = downloadedFilenames.contains("${file.filename}|${StatusSource.LIVE}")
                    )
                }
                adapter.submitList(currentItems)
                updateEmptyState(currentItems.isEmpty())
            } catch (e: Exception) {
                updateEmptyState(true)
            }
        }
    }
    
    private fun observeSavedStatuses() {
        viewModel.getSavedStatuses(fileType).observe(viewLifecycleOwner) { statuses ->
            try {
                currentItems = statuses.mapNotNull { status ->
                    if (status.localPath.isEmpty() && status.originalUri.isEmpty()) {
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
                adapter.submitList(currentItems)
                updateEmptyState(currentItems.isEmpty())
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
                val downloadedFilenames = viewModel.downloadedFilenames.value ?: emptySet()
                
                currentItems = uniqueStatuses.mapNotNull { status ->
                    if (status.localPath.isEmpty()) {
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
                        isDownloaded = downloadedFilenames.contains("${status.filename}|${status.source}"),
                        cachedAt = status.savedAt,
                        expiresAt = expiresAt
                    )
                }
                adapter.submitList(currentItems)
                updateEmptyState(currentItems.isEmpty())
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
