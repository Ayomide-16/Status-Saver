package com.statussaver.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.statussaver.app.data.database.StatusSource
import com.statussaver.app.data.repository.StatusRepository
import com.statussaver.app.databinding.ActivityMainBinding
import com.statussaver.app.service.StatusMonitorService
import com.statussaver.app.ui.fragments.StatusSectionFragment
import com.statussaver.app.util.Constants
import com.statussaver.app.util.PermissionHelper
import com.statussaver.app.util.SAFHelper
import com.statussaver.app.util.ThemeManager
import com.statussaver.app.viewmodel.StatusViewModel

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: StatusViewModel by viewModels()
    private lateinit var repository: StatusRepository

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            savePermissionGranted()
            checkSafAccess()
        } else {
            Toast.makeText(this, "Permissions required for app to work", Toast.LENGTH_LONG).show()
        }
    }

    // SAF folder picker launcher
    private val safLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            handleSafResult(uri)
        } else {
            Log.d(TAG, "SAF folder selection cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before setContentView
        ThemeManager.applyTheme(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StatusRepository(this)

        setSupportActionBar(binding.toolbar)
        
        setupBottomNavigation()
        setupClickListeners()
        setupSelectionToolbar()
        observeViewModel()
        
        checkInitialSetup()
    }
    
    // ========== Selection Mode State ==========
    private var isInSelectionMode = false
    private var currentSelectionSource: StatusSource? = null
    private var selectionCallback: com.statussaver.app.ui.SelectionCallback? = null
    
    fun setSelectionCallback(callback: com.statussaver.app.ui.SelectionCallback?) {
        selectionCallback = callback
    }
    
    fun enterSelectionMode(source: StatusSource) {
        isInSelectionMode = true
        currentSelectionSource = source
        
        binding.toolbar.visibility = View.GONE
        binding.selectionToolbar.visibility = View.VISIBLE
        
        // Hide save button for Saved tab
        binding.btnSaveSelected.visibility = if (source == StatusSource.SAVED) View.GONE else View.VISIBLE
    }
    
    fun updateSelectionCount(count: Int) {
        binding.txtSelectionCount.text = "$count selected"
    }
    
    fun exitSelectionMode() {
        isInSelectionMode = false
        currentSelectionSource = null
        
        binding.selectionToolbar.visibility = View.GONE
        binding.toolbar.visibility = View.VISIBLE
    }
    
    private fun setupSelectionToolbar() {
        binding.btnCloseSelection.setOnClickListener {
            selectionCallback?.onCancelSelectionClicked()
            exitSelectionMode()
        }
        
        binding.btnSelectAll.setOnClickListener {
            selectionCallback?.onSelectAllClicked()
        }
        
        binding.btnSaveSelected.setOnClickListener {
            selectionCallback?.onSaveSelectedClicked()
        }
        
        binding.btnShareSelected.setOnClickListener {
            selectionCallback?.onShareSelectedClicked()
        }
        
        binding.btnDeleteSelected.setOnClickListener {
            selectionCallback?.onDeleteSelectedClicked()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isInSelectionMode) {
            selectionCallback?.onCancelSelectionClicked()
            exitSelectionMode()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // P2-12: Clear selection when navigating away from a tab
            if (isInSelectionMode) {
                selectionCallback?.onCancelSelectionClicked()
                exitSelectionMode()
            }
            
            val tag = item.itemId.toString()
            var fragment = supportFragmentManager.findFragmentByTag(tag)
            val transaction = supportFragmentManager.beginTransaction()
            
            supportFragmentManager.fragments.forEach { transaction.hide(it) }
            
            if (fragment == null) {
                fragment = when (item.itemId) {
                    R.id.nav_live -> StatusSectionFragment.newInstance(StatusSource.LIVE)
                    R.id.nav_saved -> StatusSectionFragment.newInstance(StatusSource.SAVED)
                    R.id.nav_cached -> StatusSectionFragment.newInstance(StatusSource.CACHED)
                    else -> return@setOnItemSelectedListener false
                }
                transaction.add(R.id.fragmentContainer, fragment, tag)
            } else {
                transaction.show(fragment)
            }
            
            transaction.commit()
            true
        }
    }

    private fun setupClickListeners() {
        binding.btnGrantAccess.setOnClickListener {
            launchSafPicker()
        }
        
        // Theme is now managed via Settings or overflow menu
    }

    private fun observeViewModel() {
        viewModel.hasPermission.observe(this) { hasPermission ->
            if (hasPermission) {
                showMainUI()
            } else {
                showNoAccessUI()
            }
        }

        viewModel.message.observe(this) { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun checkInitialSetup() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val permissionGranted = prefs.getBoolean(Constants.KEY_PERMISSION_GRANTED, false)
        
        if (!permissionGranted && !PermissionHelper.hasAllPermissions(this)) {
            // First time - request permissions
            requestPermissions()
        } else {
            // Permissions already granted or handled
            checkSafAccess()
        }
    }

    private fun requestPermissions() {
        val permissions = PermissionHelper.getRequiredPermissions()
        permissionLauncher.launch(permissions)
    }

    private fun savePermissionGranted() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.KEY_PERMISSION_GRANTED, true).apply()
    }

    private fun checkSafAccess() {
        if (SAFHelper.hasValidPermission(this)) {
            viewModel.checkPermission()
            showMainUI()
            
            // Start background service
            StatusMonitorService.start(this)
            
            // Load default tab
            binding.bottomNavigation.selectedItemId = R.id.nav_live
        } else {
            showNoAccessUI()
        }
    }

    private fun launchSafPicker() {
        Toast.makeText(
            this,
            "Navigate to: Android \u2192 media \u2192 com.whatsapp \u2192 WhatsApp \u2192 Media \u2192 .Statuses",
            Toast.LENGTH_LONG
        ).show()
        safLauncher.launch(null)
    }

    private fun handleSafResult(uri: Uri) {
        if (SAFHelper.takePersistablePermission(this, uri)) {
            SAFHelper.storeUri(this, uri)
            viewModel.checkPermission()
            showMainUI()
            
            // Save folder selected state
            val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
            prefs.edit().putBoolean(Constants.KEY_FOLDER_SELECTED, true).apply()
            
            // Start background service
            StatusMonitorService.start(this)
            
            // Load default tab
            binding.bottomNavigation.selectedItemId = R.id.nav_live
            
            Toast.makeText(this, "Access granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to get permission", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMainUI() {
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.bottomNavigation.visibility = View.VISIBLE
        binding.noAccessLayout.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun showNoAccessUI() {
        binding.fragmentContainer.visibility = View.GONE
        binding.bottomNavigation.visibility = View.GONE
        binding.noAccessLayout.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                viewModel.refreshLiveStatuses()
                Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, com.statussaver.app.ui.SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    // Recreate activity if theme changed in SettingsActivity
    override fun onResume() {
        super.onResume()
        // If we want to check for theme changes, we can compare current theme with the one we started with.
        // For simplicity, we just rely on recreate() from SettingsActivity, but if we navigate back,
        // we might need to check. Since we recreate MainActivity when theme changes, let's keep it simple.
    }
}
