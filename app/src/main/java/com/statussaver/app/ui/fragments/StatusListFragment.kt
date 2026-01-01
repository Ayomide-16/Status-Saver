package com.statussaver.app.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusEntity
import com.statussaver.app.data.database.StatusSource
import com.statussaver.app.data.repository.StatusRepository
import com.statussaver.app.databinding.FragmentStatusListBinding
import com.statussaver.app.ui.FullScreenViewActivity
import com.statussaver.app.ui.StatusAdapter
import com.statussaver.app.viewmodel.StatusViewModel

/**
 * Fragment for displaying status list (Images or Videos)
 */
class StatusListFragment : Fragment() {
    
    private var _binding: FragmentStatusListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: StatusViewModel by activityViewModels()
    private lateinit var adapter: StatusAdapter
    
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
    
    private fun setupRecyclerView() {
        adapter = StatusAdapter(
            onItemClick = { item -> openFullScreen(item) },
            onDownloadClick = { item -> downloadStatus(item) }
        )
        
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = this@StatusListFragment.adapter
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData(statusSource, fileType)
        }
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
                    isDownloaded = it.isDownloaded
                )
            }
            adapter.submitList(items)
            updateEmptyState(items.isEmpty())
        }
    }
    
    private fun observeSavedStatuses() {
        viewModel.getSavedStatuses(fileType).observe(viewLifecycleOwner) { statuses ->
            val items = statuses.map {
                StatusAdapter.StatusItem(
                    id = it.id,
                    filename = it.filename,
                    path = it.localPath,
                    uri = it.originalUri,
                    fileType = it.fileType,
                    source = StatusSource.SAVED,
                    isDownloaded = true // Already saved
                )
            }
            adapter.submitList(items)
            updateEmptyState(items.isEmpty())
        }
    }
    
    private fun observeCachedStatuses() {
        viewModel.getCachedStatuses(fileType).observe(viewLifecycleOwner) { statuses ->
            val items = statuses.map {
                StatusAdapter.StatusItem(
                    id = it.id,
                    filename = it.filename,
                    path = it.localPath,
                    uri = it.originalUri,
                    fileType = it.fileType,
                    source = StatusSource.CACHED,
                    isDownloaded = viewModel.isDownloaded(it.filename)
                )
            }
            adapter.submitList(items)
            updateEmptyState(items.isEmpty())
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun openFullScreen(item: StatusAdapter.StatusItem) {
        val intent = Intent(requireContext(), FullScreenViewActivity::class.java).apply {
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
        
        viewModel.saveStatus(item.filename, item.uri, item.source)
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.refreshData(statusSource, fileType)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
