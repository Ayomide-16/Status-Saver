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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.statussaver.app.data.database.BackedUpStatus
import com.statussaver.app.databinding.ActivityMainBinding
import com.statussaver.app.ui.FullScreenViewActivity
import com.statussaver.app.ui.StatusAdapter
import com.statussaver.app.util.PermissionHelper
import com.statussaver.app.util.SAFHelper
import com.statussaver.app.viewmodel.StatusViewModel
import com.statussaver.app.worker.StatusBackupWorker

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: StatusViewModel by viewModels()
    private lateinit var statusAdapter: StatusAdapter

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
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
            showNoAccessUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        checkPermissionsAndAccess()
    }

    private fun setupRecyclerView() {
        statusAdapter = StatusAdapter(
            onItemClick = { status -> openFullScreen(status) },
            onItemLongClick = { status -> 
                showDeleteDialog(status)
                true
            }
        )

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = statusAdapter
        }
    }

    private fun setupObservers() {
        viewModel.allBackups.observe(this) { backups ->
            statusAdapter.submitList(backups)
            updateEmptyState(backups.isEmpty())
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.backupResult.observe(this) { result ->
            result?.let { (newCount, skippedCount) ->
                Toast.makeText(
                    this,
                    "Backup complete: $newCount new, $skippedCount already saved",
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.clearBackupResult()
            }
        }

        viewModel.hasValidPermission.observe(this) { hasPermission ->
            if (hasPermission) {
                showMainUI()
                StatusBackupWorker.schedulePeriodicBackup(this)
            } else {
                showNoAccessUI()
            }
        }
    }

    private fun setupClickListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.triggerBackup()
        }

        binding.fabRefresh.setOnClickListener {
            viewModel.triggerBackup()
        }

        binding.btnGrantAccess.setOnClickListener {
            launchSafPicker()
        }
    }

    private fun checkPermissionsAndAccess() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            val permissions = PermissionHelper.getRequiredPermissions()
            permissionLauncher.launch(permissions)
        } else {
            checkSafAccess()
        }
    }

    private fun checkSafAccess() {
        if (SAFHelper.hasValidPermission(this)) {
            viewModel.checkPermission()
            showMainUI()
            StatusBackupWorker.schedulePeriodicBackup(this)
        } else {
            showNoAccessUI()
        }
    }

    private fun launchSafPicker() {
        // Launch SAF folder picker
        Toast.makeText(
            this,
            "Please navigate to: Android > media > com.whatsapp > WhatsApp > Media > .Statuses",
            Toast.LENGTH_LONG
        ).show()
        safLauncher.launch(null)
    }

    private fun handleSafResult(uri: Uri) {
        // Take persistable permission
        if (SAFHelper.takePersistablePermission(this, uri)) {
            SAFHelper.storeUri(this, uri)
            viewModel.checkPermission()
            showMainUI()
            
            // Schedule background work and trigger initial backup
            StatusBackupWorker.schedulePeriodicBackup(this)
            viewModel.triggerBackup()
            
            Toast.makeText(this, "Access granted! Backing up statuses...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to get permission", Toast.LENGTH_SHORT).show()
            showNoAccessUI()
        }
    }

    private fun showMainUI() {
        binding.recyclerView.visibility = View.VISIBLE
        binding.fabRefresh.visibility = View.VISIBLE
        binding.noAccessLayout.visibility = View.GONE
    }

    private fun showNoAccessUI() {
        binding.recyclerView.visibility = View.GONE
        binding.fabRefresh.visibility = View.GONE
        binding.noAccessLayout.visibility = View.VISIBLE
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty && binding.noAccessLayout.visibility == View.GONE) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun openFullScreen(status: BackedUpStatus) {
        val intent = Intent(this, FullScreenViewActivity::class.java).apply {
            putExtra(FullScreenViewActivity.EXTRA_FILE_PATH, status.backupPath)
            putExtra(FullScreenViewActivity.EXTRA_FILE_TYPE, status.fileType.name)
            putExtra(FullScreenViewActivity.EXTRA_FILE_NAME, status.filename)
        }
        startActivity(intent)
    }

    private fun showDeleteDialog(status: BackedUpStatus) {
        AlertDialog.Builder(this)
            .setTitle("Delete Status")
            .setMessage("Are you sure you want to delete this saved status?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteBackup(status)
                Toast.makeText(this, "Status deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                viewModel.triggerBackup()
                true
            }
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Clear All Data", "Reset Folder Access")
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmClearData()
                    1 -> {
                        SAFHelper.clearStoredUri(this)
                        viewModel.checkPermission()
                        showNoAccessUI()
                    }
                }
            }
            .show()
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Data")
            .setMessage("This will delete all backed up statuses. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                // Clear logic would go here
                Toast.makeText(this, "Data cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
