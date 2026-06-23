package com.CampusGO.app

import android.app.Application

class CampusGOApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.init(this)

        // Load and apply theme on startup
        val prefs = getSharedPreferences("CampusGO_Prefs", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme", "light") ?: "light"
        val mode = when (savedTheme) {
            "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
    }
}
