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
        
        // Theme toggle button in toolbar
        binding.btnThemeToggle.setOnClickListener {
            toggleTheme()
        }
        
        // Update theme icon based on current theme
        updateThemeIcon()
    }

    private fun updateThemeIcon() {
        val iconRes = if (ThemeManager.isDarkTheme(this)) {
            R.drawable.ic_light_mode
        } else {
            R.drawable.ic_dark_mode
        }
        binding.btnThemeToggle.setImageResource(iconRes)
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
        
        // Set Auto-Save checkbox state
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val autoSaveEnabled = prefs.getBoolean(Constants.KEY_AUTO_SAVE_ENABLED, false)
        menu.findItem(R.id.action_autosave)?.isChecked = autoSaveEnabled
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                viewModel.refreshLiveStatuses()
                Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_autosave -> {
                toggleAutoSave(item)
                true
            }
            R.id.action_cache_duration -> {
                showCacheDurationDialog()
                true
            }
            R.id.action_folder_paths -> {
                showFolderPathsDialog()
                true
            }
            R.id.action_how_to_use -> {
                showHowToUseDialog()
                true
            }
            R.id.action_privacy_policy -> {
                showPrivacyPolicyDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleAutoSave(item: MenuItem) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val currentState = prefs.getBoolean(Constants.KEY_AUTO_SAVE_ENABLED, false)
        val newState = !currentState
        
        prefs.edit().putBoolean(Constants.KEY_AUTO_SAVE_ENABLED, newState).apply()
        item.isChecked = newState
        
        val message = if (newState) {
            "Auto-Save enabled: All new statuses will be saved automatically"
        } else {
            "Auto-Save disabled"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showCacheDurationDialog() {
        val currentDays = Constants.getRetentionDays(this)
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_cache_duration, null)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekBarDays)
        val txtDays = dialogView.findViewById<android.widget.TextView>(R.id.txtDays)
        
        seekBar.max = Constants.MAX_RETENTION_DAYS - Constants.MIN_RETENTION_DAYS
        seekBar.progress = currentDays - Constants.MIN_RETENTION_DAYS
        txtDays.text = "$currentDays days"
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val days = progress + Constants.MIN_RETENTION_DAYS
                txtDays.text = "$days days"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        AlertDialog.Builder(this)
            .setTitle("Cache Duration")
            .setMessage("Set how long cached statuses should be kept before automatic deletion.")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newDays = seekBar.progress + Constants.MIN_RETENTION_DAYS
                Constants.setRetentionDays(this, newDays)
                Toast.makeText(this, "Cache duration set to $newDays days", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFolderPathsDialog() {
        val whatsAppPath = repository.getWhatsAppStatusUri()
        val cachePath = repository.getCacheDirectory().absolutePath
        val savedPath = repository.getSavedDirectory().absolutePath
        
        val message = """
            <b>WhatsApp Status Folder:</b><br/>
            <small>$whatsAppPath</small><br/><br/>
            
            <b>Cached Status Folder:</b><br/>
            <small>$cachePath</small><br/><br/>
            
            <b>Saved Status Folder:</b><br/>
            <small>$savedPath</small>
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("Folder Paths")
            .setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy Paths") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val allPaths = """
                    WhatsApp: $whatsAppPath
                    Cache: $cachePath
                    Saved: $savedPath
                """.trimIndent()
                clipboard.setPrimaryClip(ClipData.newPlainText("Folder Paths", allPaths))
                Toast.makeText(this, "Paths copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showHowToUseDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.how_to_use_title)
            .setMessage(Html.fromHtml(getString(R.string.how_to_use_content), Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("Got it!", null)
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_policy_title)
            .setMessage(Html.fromHtml(getString(R.string.privacy_policy_content), Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toggleTheme() {
        ThemeManager.toggleTheme(this)
        recreate()
    }
}
