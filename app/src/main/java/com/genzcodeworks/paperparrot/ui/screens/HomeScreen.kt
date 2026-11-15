package com.genzcodeworks.paperparrot.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.genzcodeworks.paperparrot.data.FeedPost
import com.genzcodeworks.paperparrot.data.FirestoreLiveRepository
import com.genzcodeworks.paperparrot.ui.theme.*
import kotlinx.coroutines.launch

data class ContactItem(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val avatarColor: Color,
    val avatarBase64: String? = null,
    val googlePhotoUrl: String? = null
)

data class ChatPreview(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isGroup: Boolean = false,
    val isOnline: Boolean = true,
    val avatarColor: Color = Color(0xFFFF8A65)
)

@Composable
fun HomeScreen(
    currentUser: UserProfile,
    liveRepo: FirestoreLiveRepository,
    onSettingsClick: () -> Unit,
    onOpenChat: (ChatPreview) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showSearchUsersDialog by remember { mutableStateOf(false) }
    var newFeedPostText by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val liveUsers by liveRepo.observeUsers(currentUser.uid).collectAsState(initial = emptyList())
    val liveChats by liveRepo.observeChats(currentUser.uid).collectAsState(initial = emptyList())
    val livePosts by liveRepo.observeFeedPosts().collectAsState(initial = emptyList())

    val localCreatedGroups = remember { mutableStateListOf<ChatPreview>() }
    val chats = remember(liveChats, localCreatedGroups.size, searchQuery) {
        val combined = (localCreatedGroups + liveChats).distinctBy { it.id }
        if (searchQuery.isBlank()) combined
        else combined.filter { it.name.contains(searchQuery, ignoreCase = true) || it.lastMessage.contains(searchQuery, ignoreCase = true) }
    }

    val onlineContacts = remember(liveUsers, searchQuery) {
        liveUsers.filter { it.isOnline && (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)) }
    }

    LaunchedEffect(liveChats) {
        val activeChatWithNewMsg = liveChats.maxByOrNull { it.time }
        if (activeChatWithNewMsg != null && activeChatWithNewMsg.unreadCount > 0) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "💬 New message in ${activeChatWithNewMsg.name}: ${activeChatWithNewMsg.lastMessage}",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupChatDialog(
            contacts = liveUsers,
            onDismiss = { showCreateGroupDialog = false },
            onCreateGroup = { groupName, selectedMembers ->
                val newGroup = liveRepo.createGroupChat(currentUser, groupName, selectedMembers)
                localCreatedGroups.add(0, newGroup)
                showCreateGroupDialog = false
                selectedTab = 1
                onOpenChat(newGroup)
            }
        )
    }

    if (showSearchUsersDialog) {
        SearchUsersDialog(
            currentUser = currentUser,
            liveRepo = liveRepo,
            onDismiss = { showSearchUsersDialog = false },
            onStartChat = { chatPreview ->
                showSearchUsersDialog = false
                onOpenChat(chatPreview)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (selectedTab != 2) showCreateGroupDialog = true },
                containerColor = ParrotPurple,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (selectedTab == 1) Icons.Default.GroupAdd else Icons.Default.Add,
                    contentDescription = "Create"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // Top Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Hello,", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = currentUser.name, color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                    LoggedInUserAvatar(user = currentUser, onClick = onSettingsClick)
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Online (${onlineContacts.size})", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))

                if (onlineContacts.isEmpty()) {
                    Text(text = "No other users online yet", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(onlineContacts, key = { it.id }) { contact ->
                            Box(
                                modifier = Modifier.clickable {
                                    onOpenChat(
                                        ChatPreview(
                                            id = contact.id,
                                            name = contact.name,
                                            lastMessage = "Tap to chat live",
                                            time = "Now",
                                            isOnline = contact.isOnline,
                                            avatarColor = contact.avatarColor
                                        )
                                    )
                                }
                            ) {
                                ContactAvatarItem(contact)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rounded Bottom Sheet
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isSearchActive) ParrotPurple else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        FilterPill(title = "Direct Message", count = chats.count { !it.isGroup }, isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
                        FilterPill(title = "Group", count = chats.count { it.isGroup }, isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
                        FilterPill(title = "Feed", count = livePosts.size.coerceAtLeast(1), isSelected = selectedTab == 2, onClick = { selectedTab = 2 })
                    }

                    AnimatedVisibility(visible = isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search chats or posts...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedTab == 2) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = newFeedPostText,
                                        onValueChange = { newFeedPostText = it },
                                        placeholder = { Text("Share an update with everyone... 🦜") },
                                        modifier = Modifier.weight(1f),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        maxLines = 3
                                    )
                                    IconButton(onClick = {
                                        if (newFeedPostText.isNotBlank()) {
                                            liveRepo.createFeedPost(currentUser, newFeedPostText.trim())
                                            newFeedPostText = ""
                                        }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post", tint = ParrotPurple)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                if (livePosts.isEmpty()) {
                                    item {
                                        FeedPostCard(
                                            post = FeedPost(id = "welcome", authorName = currentUser.name, authorTag = currentUser.memberTag, content = "Welcome to Paper Parrot! Post your first live update above 🦜✨", likesCount = 1),
                                            onLike = {}
                                        )
                                    }
                                } else {
                                    items(livePosts, key = { it.id }) { post ->
                                        FeedPostCard(post = post, onLike = { liveRepo.likeFeedPost(post.id, post.likesCount) })
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            onClick = { showSearchUsersDialog = true },
                            shape = RoundedCornerShape(18.dp),
                            color = ParrotYellow.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PersonSearch, contentDescription = null, tint = ParrotCharcoal)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(text = "Find & Connect with People", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ParrotCharcoal)
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = ParrotCharcoal)
                            }
                        }

                        if (selectedTab == 1) {
                            Surface(
                                onClick = { showCreateGroupDialog = true },
                                shape = RoundedCornerShape(18.dp),
                                color = ParrotPurple.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = ParrotPurple)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(text = "Create New Group Chat", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ParrotPurple)
                                    }
                                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = ParrotPurple)
                                }
                            }
                        }

                        val filteredChats = when (selectedTab) {
                            0 -> chats.filter { !it.isGroup }
                            1 -> chats.filter { it.isGroup }
                            else -> chats
                        }

                        if (filteredChats.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No chats found for \"$searchQuery\""
                                    else if (selectedTab == 1) "No group chats yet. Tap + to create one!"
                                    else "No active chats yet. Tap 'Find & Connect with People' above!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(filteredChats, key = { it.id }) { chat ->
                                    ChatRowItem(chat = chat, onClick = { onOpenChat(chat) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchUsersDialog(
    currentUser: UserProfile,
    liveRepo: FirestoreLiveRepository,
    onDismiss: () -> Unit,
    onStartChat: (ChatPreview) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Find People 🦜", style = MaterialTheme.typography.titleMedium, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { newQuery ->
                        query = newQuery
                        if (newQuery.isNotBlank()) {
                            coroutineScope.launch {
                                isLoading = true
                                searchResults = liveRepo.searchUsers(newQuery, currentUser.uid)
                                isLoading = false
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    label = { Text("Search by name or @tag") },
                    placeholder = { Text("e.g. Hareesh or @hareesh") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ParrotPurple)
                    }
                } else if (query.isNotBlank() && searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No users found matching \"$query\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(searchResults, key = { it.id }) { user ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onStartChat(
                                        ChatPreview(id = user.id, name = user.name, lastMessage = "Say hello!", time = "Now", isOnline = user.isOnline, avatarColor = user.avatarColor)
                                    )
                                    onDismiss()
                                }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(user.avatarColor), contentAlignment = Alignment.Center) {
                                        Text(text = user.name.firstOrNull()?.toString() ?: "U", fontWeight = FontWeight.Bold, color = ParrotCharcoal)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = user.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(text = if (user.isOnline) "Online" else "Offline", style = MaterialTheme.typography.labelSmall, color = if (user.isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = {
                                        onStartChat(
                                            ChatPreview(id = user.id, name = user.name, lastMessage = "Say hello!", time = "Now", isOnline = user.isOnline, avatarColor = user.avatarColor)
                                        )
                                        onDismiss()
                                    }) {
                                        Icon(Icons.Default.Chat, contentDescription = "Start Chat", tint = ParrotPurple)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateGroupChatDialog(
    contacts: List<ContactItem>,
    onDismiss: () -> Unit,
    onCreateGroup: (String, List<ContactItem>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "New Group Chat 🦜", style = MaterialTheme.typography.titleMedium, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    placeholder = { Text("e.g. Weekend Squad") },
                    leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Select Friends (${selectedIds.size} selected)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ParrotPurple)
                Spacer(modifier = Modifier.height(8.dp))

                if (contacts.isEmpty()) {
                    Text(text = "No other registered users found in Firestore yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(contacts, key = { it.id }) { friend ->
                            val isSelected = selectedIds.contains(friend.id)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) ParrotPurple.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (isSelected) selectedIds.remove(friend.id) else selectedIds.add(friend.id)
                                }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(friend.avatarColor), contentAlignment = Alignment.Center) {
                                        Text(text = friend.name.firstOrNull()?.toString() ?: "F", fontWeight = FontWeight.Bold, color = ParrotCharcoal)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = friend.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(text = if (friend.isOnline) "Online" else "Offline", style = MaterialTheme.typography.labelSmall, color = if (friend.isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) { if (!selectedIds.contains(friend.id)) selectedIds.add(friend.id) }
                                            else { selectedIds.remove(friend.id) }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = ParrotPurple)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    enabled = groupName.isNotBlank() && selectedIds.isNotEmpty(),
                    onClick = {
                        val chosenFriends = contacts.filter { selectedIds.contains(it.id) }
                        onCreateGroup(groupName.trim(), chosenFriends)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ParrotPurple, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Group (${selectedIds.size})", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FeedPostCard(post: FeedPost, onLike: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(ParrotYellow), contentAlignment = Alignment.Center) {
                    Text(text = post.authorName.firstOrNull()?.uppercase() ?: "P", fontWeight = FontWeight.Bold, color = ParrotCharcoal)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = post.authorName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = post.authorTag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = post.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onLike() }) {
                Icon(imageVector = Icons.Default.Favorite, contentDescription = "Like", tint = UnreadPink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "${post.likesCount} Likes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LoggedInUserAvatar(user: UserProfile, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(ParrotYellow).border(2.dp, Color.White, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (user.customPhotoBase64 != null) {
            val bmp = remember(user.customPhotoBase64) {
                try {
                    val bytes = Base64.decode(user.customPhotoBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (_: Exception) { null }
            }
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(text = user.name.firstOrNull()?.uppercase() ?: "H", color = ParrotCharcoal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        } else {
            Text(text = user.name.firstOrNull()?.uppercase() ?: "H", color = ParrotCharcoal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
fun ContactAvatarItem(contact: ContactItem) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(60.dp)) {
            Box(
                modifier = Modifier.size(58.dp).clip(CircleShape).background(contact.avatarColor).border(2.dp, Color.White, CircleShape).align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                if (!contact.avatarBase64.isNullOrBlank()) {
                    val bmp = remember(contact.avatarBase64) {
                        try {
                            val bytes = Base64.decode(contact.avatarBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (_: Exception) { null }
                    }
                    if (bmp != null) {
                        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(text = contact.name.first().toString(), color = ParrotCharcoal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                } else {
                    Text(text = contact.name.first().toString(), color = ParrotCharcoal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            Box(
                modifier = Modifier.size(16.dp).clip(CircleShape).background(OnlineGreen).border(2.dp, Color.White, CircleShape).align(Alignment.BottomEnd)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = contact.name.substringBefore(" "), color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FilterPill(title: String, count: Int, isSelected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = if (isSelected) ParrotYellow else MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, color = if (isSelected) ParrotCharcoal else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(if (isSelected) ParrotCharcoal else Color.Transparent), contentAlignment = Alignment.Center) {
                Text(text = count.toString(), color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ChatRowItem(chat: ChatPreview, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(54.dp)) {
            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(chat.avatarColor).align(Alignment.Center), contentAlignment = Alignment.Center) {
                Text(text = chat.name.first().toString(), color = ParrotCharcoal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(if (chat.isOnline) OnlineGreen else AwayYellow).align(Alignment.BottomEnd))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = chat.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = chat.lastMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(text = chat.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            if (chat.unreadCount > 0) {
                Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(UnreadPink), contentAlignment = Alignment.Center) {
                    Text(text = chat.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Icon(imageVector = Icons.Default.DoneAll, contentDescription = "Read", tint = ParrotPurple, modifier = Modifier.size(18.dp))
            }
        }
    }
}