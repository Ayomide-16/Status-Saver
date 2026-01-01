package com.statussaver.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.statussaver.app.R

object ThemeManager {
    private const val PREFS_THEME = "theme_prefs"
    private const val KEY_THEME = "selected_theme"
    
    const val THEME_GREEN = "green"
    const val THEME_DARK = "dark"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
    }
    
    /**
     * Get the current theme preference
     */
    fun getCurrentTheme(context: Context): String {
        return getPrefs(context).getString(KEY_THEME, THEME_GREEN) ?: THEME_GREEN
    }
    
    /**
     * Save theme preference
     */
    fun setTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_THEME, theme).apply()
    }
    
    /**
     * Toggle between themes
     */
    fun toggleTheme(context: Context): String {
        val current = getCurrentTheme(context)
        val newTheme = if (current == THEME_GREEN) THEME_DARK else THEME_GREEN
        setTheme(context, newTheme)
        return newTheme
    }
    
    /**
     * Apply the current theme to an activity
     * Must be called BEFORE setContentView()
     */
    fun applyTheme(activity: AppCompatActivity) {
        val theme = getCurrentTheme(activity)
        when (theme) {
            THEME_GREEN -> activity.setTheme(R.style.Theme_StatusSaver_Green)
            THEME_DARK -> activity.setTheme(R.style.Theme_StatusSaver_Dark)
        }
    }
    
    /**
     * Check if dark theme is active
     */
    fun isDarkTheme(context: Context): Boolean {
        return getCurrentTheme(context) == THEME_DARK
    }
    
    /**
     * Get theme resource ID
     */
    fun getThemeResId(context: Context): Int {
        return when (getCurrentTheme(context)) {
            THEME_GREEN -> R.style.Theme_StatusSaver_Green
            THEME_DARK -> R.style.Theme_StatusSaver_Dark
            else -> R.style.Theme_StatusSaver_Green
        }
    }
}
