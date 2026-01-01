package com.statussaver.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusSource
import com.statussaver.app.databinding.FragmentStatusSectionBinding

/**
 * Base fragment for each main section (Live, Saved, Cached)
 * Contains tabs for Images and Videos
 */
class StatusSectionFragment : Fragment() {
    
    private var _binding: FragmentStatusSectionBinding? = null
    private val binding get() = _binding!!
    
    private var statusSource: StatusSource = StatusSource.LIVE
    
    companion object {
        private const val ARG_SOURCE = "source"
        
        fun newInstance(source: StatusSource): StatusSectionFragment {
            return StatusSectionFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SOURCE, source.name)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_SOURCE)?.let {
            statusSource = StatusSource.valueOf(it)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusSectionBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
    }
    
    private fun setupViewPager() {
        val adapter = MediaTypePagerAdapter(this, statusSource)
        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Images"
                1 -> "Videos"
                else -> ""
            }
        }.attach()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * Adapter for Images/Videos tabs
     */
    private class MediaTypePagerAdapter(
        fragment: Fragment,
        private val source: StatusSource
    ) : FragmentStateAdapter(fragment) {
        
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            val fileType = when (position) {
                0 -> FileType.IMAGE
                1 -> FileType.VIDEO
                else -> FileType.IMAGE
            }
            return StatusListFragment.newInstance(source, fileType)
        }
    }
}
