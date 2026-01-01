package com.statussaver.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.statussaver.app.data.database.StatusSource
import com.statussaver.app.databinding.ActivityMainBinding
import com.statussaver.app.service.StatusMonitorService
import com.statussaver.app.ui.fragments.StatusSectionFragment
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

        setSupportActionBar(binding.toolbar)
        
        setupBottomNavigation()
        setupClickListeners()
        observeViewModel()
        
        checkInitialSetup()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_live -> StatusSectionFragment.newInstance(StatusSource.LIVE)
                R.id.nav_saved -> StatusSectionFragment.newInstance(StatusSource.SAVED)
                R.id.nav_cached -> StatusSectionFragment.newInstance(StatusSource.CACHED)
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun setupClickListeners() {
        binding.btnGrantAccess.setOnClickListener {
            launchSafPicker()
        }
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
        val prefs = getSharedPreferences(com.statussaver.app.util.Constants.PREFS_NAME, MODE_PRIVATE)
        val permissionGranted = prefs.getBoolean(com.statussaver.app.util.Constants.KEY_PERMISSION_GRANTED, false)
        
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
        val prefs = getSharedPreferences(com.statussaver.app.util.Constants.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(com.statussaver.app.util.Constants.KEY_PERMISSION_GRANTED, true).apply()
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
            "Navigate to: Android → media → com.whatsapp → WhatsApp → Media → .Statuses",
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
            val prefs = getSharedPreferences(com.statussaver.app.util.Constants.PREFS_NAME, MODE_PRIVATE)
            prefs.edit().putBoolean(com.statussaver.app.util.Constants.KEY_FOLDER_SELECTED, true).apply()
            
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
                true
            }
            R.id.action_theme -> {
                toggleTheme()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleTheme() {
        val newTheme = ThemeManager.toggleTheme(this)
        val themeName = if (newTheme == ThemeManager.THEME_DARK) "Dark" else "WhatsApp Green"
        Toast.makeText(this, "Switched to $themeName theme", Toast.LENGTH_SHORT).show()
        recreate()
    }
}
