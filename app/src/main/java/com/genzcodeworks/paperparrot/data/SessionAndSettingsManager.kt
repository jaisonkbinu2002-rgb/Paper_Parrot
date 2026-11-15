package com.genzcodeworks.paperparrot.data

import android.content.Context
import com.genzcodeworks.paperparrot.ui.screens.UserProfile

data class AppSettings(
    val isDarkTheme: Boolean = false,
    val enterSendsMessage: Boolean = true,
    val readReceipts: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val autoDownloadPhotos: Boolean = true,
    val notificationSound: Boolean = true
)

class SessionAndSettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("paper_parrot_prefs", Context.MODE_PRIVATE)

    // Save logged-in user locally so app opens straight to Home Screen like WhatsApp
    fun saveUserSession(user: UserProfile) {
        prefs.edit().apply {
            putString("uid", user.uid)
            putString("name", user.name)
            putString("memberTag", user.memberTag)
            putString("email", user.email)
            putString("phone", user.phone)
            putString("googlePhotoUrl", user.googlePhotoUrl)
            putString("customPhotoBase64", user.customPhotoBase64)
            putBoolean("isProfileComplete", user.isProfileComplete)
            apply()
        }
    }

    fun getSavedUserSession(): UserProfile? {
        val uid = prefs.getString("uid", null) ?: return null
        val isComplete = prefs.getBoolean("isProfileComplete", false)
        if (!isComplete) return null

        return UserProfile(
            uid = uid,
            name = prefs.getString("name", "Hareesh Marar") ?: "Hareesh Marar",
            memberTag = prefs.getString("memberTag", "@hareesh") ?: "@hareesh",
            email = prefs.getString("email", "") ?: "",
            phone = prefs.getString("phone", "") ?: "",
            googlePhotoUrl = prefs.getString("googlePhotoUrl", "") ?: "",
            customPhotoBase64 = prefs.getString("customPhotoBase64", null),
            isProfileComplete = true
        )
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().apply {
            putBoolean("dark_theme", settings.isDarkTheme)
            putBoolean("enter_sends", settings.enterSendsMessage)
            putBoolean("read_receipts", settings.readReceipts)
            putBoolean("online_status", settings.showOnlineStatus)
            putBoolean("auto_photos", settings.autoDownloadPhotos)
            putBoolean("notif_sound", settings.notificationSound)
            apply()
        }
    }

    fun loadSettings(): AppSettings {
        return AppSettings(
            isDarkTheme = prefs.getBoolean("dark_theme", false),
            enterSendsMessage = prefs.getBoolean("enter_sends", true),
            readReceipts = prefs.getBoolean("read_receipts", true),
            showOnlineStatus = prefs.getBoolean("online_status", true),
            autoDownloadPhotos = prefs.getBoolean("auto_photos", true),
            notificationSound = prefs.getBoolean("notif_sound", true)
        )
    }
}