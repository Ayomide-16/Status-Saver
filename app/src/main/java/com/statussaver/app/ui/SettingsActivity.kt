package com.statussaver.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.statussaver.app.R
import com.statussaver.app.util.Constants
import com.statussaver.app.util.ThemeManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        // General: Auto-Save
        val switchAutoSave = findViewById<MaterialSwitch>(R.id.switchAutoSave)
        switchAutoSave.isChecked = prefs.getBoolean(Constants.KEY_AUTO_SAVE_ENABLED, false)
        switchAutoSave.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Constants.KEY_AUTO_SAVE_ENABLED, isChecked).apply()
        }

        // General: Cache Duration
        val btnCacheDuration = findViewById<LinearLayout>(R.id.btnCacheDuration)
        val txtCacheDurationValue = findViewById<TextView>(R.id.txtCacheDurationValue)
        
        fun updateCacheDurationText() {
            val days = Constants.getRetentionDays(this)
            txtCacheDurationValue.text = "$days days"
        }
        updateCacheDurationText()

        btnCacheDuration.setOnClickListener {
            val currentDays = Constants.getRetentionDays(this)
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cache_duration, null)
            val txtDays = dialogView.findViewById<TextView>(R.id.txtDays)
            val seekBar = dialogView.findViewById<android.widget.SeekBar>(R.id.seekBarDays)

            val maxDays = Constants.MAX_RETENTION_DAYS - Constants.MIN_RETENTION_DAYS
            seekBar.max = if (maxDays > 0) maxDays else 1
            seekBar.progress = currentDays - Constants.MIN_RETENTION_DAYS
            txtDays.text = "$currentDays Days"

            seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val days = progress + Constants.MIN_RETENTION_DAYS
                    txtDays.text = "$days Days"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })

            AlertDialog.Builder(this, R.style.Theme_StatusSaver_Dialog)
                .setTitle("Cache Duration")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    val selectedDays = seekBar.progress + Constants.MIN_RETENTION_DAYS
                    Constants.setRetentionDays(this, selectedDays)
                    updateCacheDurationText()
                    Toast.makeText(this, "Cache duration set to $selectedDays days", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Appearance: Theme
        val switchTheme = findViewById<MaterialSwitch>(R.id.switchTheme)
        switchTheme.isChecked = ThemeManager.isDarkTheme(this)
        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            val newTheme = if (isChecked) ThemeManager.THEME_DARK else ThemeManager.THEME_GREEN
            ThemeManager.setTheme(this, newTheme)
            // Recreate activity to apply theme
            recreate()
        }

        // Storage & Data: Folder Paths
        val btnFolderPaths = findViewById<LinearLayout>(R.id.btnFolderPaths)
        btnFolderPaths.setOnClickListener {
            val message = "Cache Folder:\n${getExternalFilesDir(Constants.CACHE_FOLDER_NAME)?.absolutePath}\n\n" +
                          "Saved Folder:\n${getExternalFilesDir(Constants.SAVED_FOLDER_NAME)?.absolutePath}"
            
            AlertDialog.Builder(this, R.style.Theme_StatusSaver_Dialog)
                .setTitle("Folder Paths")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }

        // About: How to Use
        val btnHowToUse = findViewById<LinearLayout>(R.id.btnHowToUse)
        btnHowToUse.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_StatusSaver_Dialog)
                .setTitle("How to Use")
                .setMessage("1. Open WhatsApp and view statuses.\n" +
                            "2. Open this app to see the statuses you've viewed.\n" +
                            "3. Click the save icon to save them permanently to your device.\n" +
                            "4. View saved statuses in the 'Saved' tab.")
                .setPositiveButton("OK", null)
                .show()
        }

        // About: Privacy Policy
        val btnPrivacyPolicy = findViewById<LinearLayout>(R.id.btnPrivacyPolicy)
        btnPrivacyPolicy.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_StatusSaver_Dialog)
                .setTitle("Privacy Policy")
                .setMessage("This app only accesses WhatsApp statuses stored locally on your device. " +
                            "No data is sent to our servers. Your privacy is 100% guaranteed.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
