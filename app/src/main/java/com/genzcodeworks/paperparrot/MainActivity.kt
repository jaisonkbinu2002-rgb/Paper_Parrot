package com.genzcodeworks.paperparrot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.genzcodeworks.paperparrot.data.FirestoreLiveRepository
import com.genzcodeworks.paperparrot.data.SessionAndSettingsManager
import com.genzcodeworks.paperparrot.ui.screens.*
import com.genzcodeworks.paperparrot.ui.theme.PaperParrotTheme
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private val sessionManager by lazy { SessionAndSettingsManager(this) }
    private val liveRepo = FirestoreLiveRepository()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var appSettings by remember { mutableStateOf(sessionManager.loadSettings()) }
            var currentUser by remember { mutableStateOf(sessionManager.getSavedUserSession()) }

            var showSettingsScreen by remember { mutableStateOf(false) }
            var isEditingProfile by remember { mutableStateOf(false) }
            var activeChat by remember { mutableStateOf<ChatPreview?>(null) }

            // Set online = true on open
            LaunchedEffect(currentUser, appSettings.showOnlineStatus) {
                currentUser?.let { user ->
                    if (user.isProfileComplete) {
                        liveRepo.syncUserAndPresence(user, isOnline = appSettings.showOnlineStatus)
                    }
                }
            }

            // Request Notification Permission on Android 13+
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // Handle permission result if needed
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            PaperParrotTheme(darkTheme = appSettings.isDarkTheme) {
                when {
                    currentUser == null -> {
                        GoogleSignInScreen(
                            onGoogleAuthSuccess = { fetchedUser ->
                                if (fetchedUser.isProfileComplete) {
                                    sessionManager.saveUserSession(fetchedUser)
                                }
                                currentUser = fetchedUser
                            }
                        )
                    }

                    !currentUser!!.isProfileComplete || isEditingProfile -> {
                        BackHandler(enabled = isEditingProfile) { isEditingProfile = false }
                        CompleteOrEditProfileScreen(
                            initialProfile = currentUser!!,
                            isEditingExisting = isEditingProfile,
                            isDarkTheme = appSettings.isDarkTheme,
                            onToggleTheme = {
                                val updated = appSettings.copy(isDarkTheme = !appSettings.isDarkTheme)
                                appSettings = updated
                                sessionManager.saveSettings(updated)
                            },
                            onSaveProfile = { completedProfile ->
                                sessionManager.saveUserSession(completedProfile)
                                liveRepo.syncUserAndPresence(completedProfile, appSettings.showOnlineStatus)
                                currentUser = completedProfile
                                isEditingProfile = false
                            },
                            onBackClick = if (isEditingProfile) {
                                { isEditingProfile = false }
                            } else null
                        )
                    }

                    showSettingsScreen -> {
                        BackHandler { showSettingsScreen = false }
                        SettingsScreen(
                            currentUser = currentUser!!,
                            settings = appSettings,
                            onUpdateSettings = { updated ->
                                appSettings = updated
                                sessionManager.saveSettings(updated)
                            },
                            onEditProfileClick = {
                                showSettingsScreen = false
                                isEditingProfile = true
                            },
                            onBackClick = { showSettingsScreen = false },
                            onLogoutClick = {
                                currentUser?.let { liveRepo.syncUserAndPresence(it, isOnline = false) }
                                FirebaseAuth.getInstance().signOut()
                                sessionManager.clearSession()
                                showSettingsScreen = false
                                currentUser = null
                            }
                        )
                    }

                    activeChat != null -> {
                        BackHandler { activeChat = null }
                        ChatDetailScreen(
                            chat = activeChat!!,
                            currentUser = currentUser!!,
                            liveRepo = liveRepo,
                            enterSendsMessage = appSettings.enterSendsMessage,
                            onBackClick = { activeChat = null }
                        )
                    }

                    else -> {
                        HomeScreen(
                            currentUser = currentUser!!,
                            liveRepo = liveRepo,
                            onSettingsClick = { showSettingsScreen = true },
                            onOpenChat = { clickedChat -> activeChat = clickedChat }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Instantly mark user offline when app goes to background or is closed
        val sessionUser = sessionManager.getSavedUserSession()
        if (sessionUser != null && sessionUser.isProfileComplete) {
            liveRepo.syncUserAndPresence(sessionUser, isOnline = false)
        }
    }
}