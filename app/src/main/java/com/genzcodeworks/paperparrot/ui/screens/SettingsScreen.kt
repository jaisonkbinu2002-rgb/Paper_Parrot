package com.genzcodeworks.paperparrot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.genzcodeworks.paperparrot.data.AppSettings
import com.genzcodeworks.paperparrot.ui.theme.PaperParrotTheme
import com.genzcodeworks.paperparrot.ui.theme.ParrotPurple
import com.genzcodeworks.paperparrot.ui.theme.UnreadPink

@Composable
fun SettingsScreen(
    currentUser: UserProfile,
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onEditProfileClick: () -> Unit,
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- TOP PURPLE HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }

        // --- ROUNDED SETTINGS SHEET ---
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                // 1. Account Card (Tap to Edit Profile)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditProfileClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoggedInUserAvatar(user = currentUser, onClick = onEditProfileClick)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${currentUser.memberTag} • ${currentUser.email}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = ParrotPurple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. Appearance & Chat Behavior
                Text(
                    text = "Appearance & Chat Behavior",
                    style = MaterialTheme.typography.labelSmall,
                    color = ParrotPurple,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggleRow(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Theme",
                    subtitle = "Switch between Paper Parrot Light and Dark appearance",
                    checked = settings.isDarkTheme,
                    onCheckedChange = { onUpdateSettings(settings.copy(isDarkTheme = it)) }
                )

                SettingsToggleRow(
                    icon = Icons.Default.Send,
                    title = "Enter Key Sends Message",
                    subtitle = "Pressing Enter on keyboard sends chat message",
                    checked = settings.enterSendsMessage,
                    onCheckedChange = { onUpdateSettings(settings.copy(enterSendsMessage = it)) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Privacy & Live Presence
                Text(
                    text = "Privacy & Live Presence",
                    style = MaterialTheme.typography.labelSmall,
                    color = ParrotPurple,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggleRow(
                    icon = Icons.Default.Visibility,
                    title = "Show Online Status",
                    subtitle = "Let friends see when you're active in the Online list",
                    checked = settings.showOnlineStatus,
                    onCheckedChange = { onUpdateSettings(settings.copy(showOnlineStatus = it)) }
                )

                SettingsToggleRow(
                    icon = Icons.Default.DoneAll,
                    title = "Read Receipts",
                    subtitle = "Show double purple ticks when you read messages",
                    checked = settings.readReceipts,
                    onCheckedChange = { onUpdateSettings(settings.copy(readReceipts = it)) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 4. Media & Notifications
                Text(
                    text = "Media & Notifications",
                    style = MaterialTheme.typography.labelSmall,
                    color = ParrotPurple,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggleRow(
                    icon = Icons.Default.Photo,
                    title = "Auto-Download Photos",
                    subtitle = "Automatically load shared photos in chats & feed",
                    checked = settings.autoDownloadPhotos,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoDownloadPhotos = it)) }
                )

                SettingsToggleRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "In-App Notification Sounds",
                    subtitle = "Play tone for incoming live messages",
                    checked = settings.notificationSound,
                    onCheckedChange = { onUpdateSettings(settings.copy(notificationSound = it)) }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 5. Log Out Button
                OutlinedButton(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = UnreadPink
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log Out of Paper Parrot",
                        color = UnreadPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ParrotPurple,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    PaperParrotTheme {
        SettingsScreen(
            currentUser = UserProfile(
                uid = "demo_1",
                name = "Alex Morgan",
                memberTag = "@alex",
                email = "alex@example.com",
                isProfileComplete = true
            ),
            settings = AppSettings(),
            onUpdateSettings = {},
            onEditProfileClick = {},
            onBackClick = {},
            onLogoutClick = {}
        )
    }
}