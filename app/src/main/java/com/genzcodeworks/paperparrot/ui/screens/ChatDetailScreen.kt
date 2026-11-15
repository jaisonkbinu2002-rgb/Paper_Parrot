package com.genzcodeworks.paperparrot.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.emoji2.emojipicker.EmojiPickerView
import com.genzcodeworks.paperparrot.data.FirestoreLiveRepository
import com.genzcodeworks.paperparrot.data.LiveMessage
import com.genzcodeworks.paperparrot.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ChatMessageUI(
    val id: String,
    val senderName: String = "",
    val text: String = "",
    val time: String,
    val isMe: Boolean,
    val photoBase64List: List<String> = emptyList(),
    val photoColors: List<Color> = emptyList(),
    val sharedContactName: String? = null,
    val sharedContactPhone: String? = null,
    val voiceDuration: String? = null,
    val voiceBase64: String? = null,
    val documentName: String? = null,
    val documentSizeLabel: String? = null,
    val documentMimeType: String? = null,
    val documentBase64: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chat: ChatPreview,
    currentUser: UserProfile,
    liveRepo: FirestoreLiveRepository,
    enterSendsMessage: Boolean = true,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var messageText by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<ChatMessageUI?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecordingVoice = true
        } else {
            Toast.makeText(context, "Microphone permission is required for Voice Notes", Toast.LENGTH_SHORT).show()
        }
    }

    val roomId = remember(currentUser.uid, chat.id, chat.isGroup) {
        liveRepo.resolveRoomId(currentUser.uid, chat.id, chat.isGroup)
    }

    val liveFirestoreMessages by liveRepo.observeChatMessages(roomId).collectAsState(initial = emptyList())
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val localMessages = remember { mutableStateListOf<ChatMessageUI>() }

    val displayedMessages: List<ChatMessageUI> = remember(liveFirestoreMessages, localMessages.size) {
        if (liveFirestoreMessages.isNotEmpty()) {
            liveFirestoreMessages.map { liveMsg: LiveMessage ->
                ChatMessageUI(
                    id = liveMsg.id,
                    senderName = liveMsg.senderName,
                    text = liveMsg.text,
                    time = timeFormatter.format(Date(liveMsg.timestamp)),
                    isMe = liveMsg.senderId == currentUser.uid,
                    photoBase64List = liveMsg.photoBase64List,
                    sharedContactName = liveMsg.sharedContactName,
                    sharedContactPhone = liveMsg.sharedContactPhone,
                    voiceDuration = liveMsg.voiceDuration,
                    voiceBase64 = liveMsg.voiceBase64,
                    documentName = liveMsg.documentName,
                    documentSizeLabel = liveMsg.documentSizeLabel,
                    documentMimeType = liveMsg.documentMimeType,
                    documentBase64 = liveMsg.documentBase64,
                    latitude = liveMsg.latitude,
                    longitude = liveMsg.longitude
                )
            }
        } else {
            localMessages
        }
    }

    val multiplePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isUploadingMedia = true
                val compressedList = uris.mapNotNull { uri ->
                    liveRepo.compressUriToBase64(context, uri)
                }
                if (compressedList.isNotEmpty()) {
                    val caption = messageText.trim()
                    localMessages.add(
                        ChatMessageUI(
                            id = System.currentTimeMillis().toString(),
                            senderName = currentUser.name,
                            text = caption,
                            time = timeFormatter.format(Date()),
                            isMe = true,
                            photoBase64List = compressedList
                        )
                    )
                    liveRepo.sendChatMessage(
                        roomId = roomId,
                        currentUser = currentUser,
                        chatPartner = chat,
                        text = caption,
                        photoBase64List = compressedList
                    )
                    messageText = ""
                }
                isUploadingMedia = false
            }
        }
    }

    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        contactUri?.let { uri ->
            val pair = liveRepo.readContactFromUri(context, uri)
            if (pair != null) {
                localMessages.add(
                    ChatMessageUI(
                        id = System.currentTimeMillis().toString(),
                        senderName = currentUser.name,
                        time = timeFormatter.format(Date()),
                        isMe = true,
                        sharedContactName = pair.first,
                        sharedContactPhone = pair.second
                    )
                )
                liveRepo.sendChatMessage(
                    roomId = roomId,
                    currentUser = currentUser,
                    chatPartner = chat,
                    sharedContactName = pair.first,
                    sharedContactPhone = pair.second
                )
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { docUri: Uri? ->
        docUri?.let { uri ->
            coroutineScope.launch {
                isUploadingMedia = true
                val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
                val docTriple = liveRepo.readDocumentFromUri(context, uri)
                if (docTriple?.first == "TOO_LARGE") {
                    Toast.makeText(context, "Please select a document under 550 KB", Toast.LENGTH_LONG).show()
                } else if (docTriple != null) {
                    val (docName, sizeLabel, docB64) = docTriple
                    localMessages.add(
                        ChatMessageUI(
                            id = System.currentTimeMillis().toString(),
                            senderName = currentUser.name,
                            time = timeFormatter.format(Date()),
                            isMe = true,
                            documentName = docName,
                            documentSizeLabel = sizeLabel,
                            documentMimeType = mimeType,
                            documentBase64 = docB64
                        )
                    )
                    liveRepo.sendChatMessage(
                        roomId = roomId,
                        currentUser = currentUser,
                        chatPartner = chat,
                        documentName = docName,
                        documentSizeLabel = sizeLabel,
                        documentMimeType = mimeType,
                        documentBase64 = docB64
                    )
                }
                isUploadingMedia = false
            }
        }
    }

    val sendCurrentLocation = {
        val coords = liveRepo.getCurrentDeviceLocation(context)
        val lat = coords?.first ?: 9.8959
        val lng = coords?.second ?: 76.7184
        localMessages.add(
            ChatMessageUI(
                id = System.currentTimeMillis().toString(),
                senderName = currentUser.name,
                time = timeFormatter.format(Date()),
                isMe = true,
                latitude = lat,
                longitude = lng
            )
        )
        liveRepo.sendChatMessage(
            roomId = roomId,
            currentUser = currentUser,
            chatPartner = chat,
            latitude = lat,
            longitude = lng
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            sendCurrentLocation()
        } else {
            Toast.makeText(context, "Location permission is required to share location", Toast.LENGTH_SHORT).show()
        }
    }

    val sendTextMessage = {
        val cleanText = messageText.trim()
        if (cleanText.isNotEmpty()) {
            val replyId = replyingToMessage?.id
            val replyName = if (replyingToMessage?.isMe == true) "You" else (replyingToMessage?.senderName.takeIf { !it.isNullOrBlank() } ?: chat.name)
            val replyPreviewText = replyingToMessage?.text?.take(30) ?: "Attachment"

            localMessages.add(
                ChatMessageUI(
                    id = System.currentTimeMillis().toString(),
                    senderName = currentUser.name,
                    text = cleanText,
                    time = timeFormatter.format(Date()),
                    isMe = true,
                    replyToMessageId = replyId,
                    replyToSenderName = replyName,
                    replyToText = replyPreviewText
                )
            )
            liveRepo.sendChatMessage(
                roomId = roomId,
                currentUser = currentUser,
                chatPartner = chat,
                text = cleanText
            )
            messageText = ""
            replyingToMessage = null
        }
    }

    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(displayedMessages.size, imeBottom, showEmojiPicker) {
        if (displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (isRecordingVoice) Modifier.blur(10.dp) else Modifier)
        ) {
            // --- TOP BAR (Clean Name & Online Dot) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = chat.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (chat.isOnline) OnlineGreen else AwayYellow)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (chat.isOnline) "Online" else "Offline",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                IconButton(onClick = { /* More Options */ }) {
                    Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = "More", tint = Color.White)
                }
            }

            // --- ROUNDED CHAT BODY SHEET ---
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    if (isUploadingMedia) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ParrotPurple)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        contentPadding = PaddingValues(top = 20.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom)
                    ) {
                        if (displayedMessages.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Say hi to ${chat.name}! 🦜",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(displayedMessages, key = { it.id }) { msg ->
                                ChatBubbleItem(
                                    chat = chat,
                                    msg = msg,
                                    senderColor = chat.avatarColor,
                                    senderInitial = chat.name.firstOrNull()?.toString() ?: "F",
                                    onReplyClick = { clickedMsg -> replyingToMessage = clickedMsg }
                                )
                            }
                        }
                    }

                    // Attachment Menu (+ Icon)
                    if (showAttachmentMenu) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    showAttachmentMenu = false
                                    multiplePhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                label = { Text("Photos") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = ParrotPurple, modifier = Modifier.size(18.dp)) }
                            )
                            AssistChip(
                                onClick = {
                                    showAttachmentMenu = false
                                    documentPicker.launch(arrayOf("application/pdf", "text/*", "application/*"))
                                },
                                label = { Text("Document") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = ParrotPurple, modifier = Modifier.size(18.dp)) }
                            )
                            AssistChip(
                                onClick = {
                                    showAttachmentMenu = false
                                    val hasLocPerm = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED ||
                                            ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.ACCESS_COARSE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED

                                    if (hasLocPerm) {
                                        sendCurrentLocation()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                label = { Text("Location") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = ParrotPurple, modifier = Modifier.size(18.dp)) }
                            )
                            AssistChip(
                                onClick = {
                                    showAttachmentMenu = false
                                    contactPicker.launch(null)
                                },
                                label = { Text("Contact") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = ParrotPurple, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }

                    // Reply Banner
                    if (replyingToMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(36.dp)
                                        .background(ParrotPurple, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Replying to ${if (replyingToMessage!!.isMe) "You" else (replyingToMessage!!.senderName.takeIf { !it.isBlank() } ?: chat.name)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ParrotPurple,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            replyingToMessage!!.text.isNotBlank() -> replyingToMessage!!.text
                                            replyingToMessage!!.photoBase64List.isNotEmpty() -> "Photo attachment"
                                            replyingToMessage!!.documentName != null -> "Document: ${replyingToMessage!!.documentName}"
                                            replyingToMessage!!.latitude != null -> "Current Location"
                                            replyingToMessage!!.sharedContactName != null -> "Contact: ${replyingToMessage!!.sharedContactName}"
                                            else -> "Voice note"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { replyingToMessage = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Reply", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Bottom Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Attach", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = messageText,
                                    onValueChange = { messageText = it },
                                    placeholder = { Text("Type Message", style = MaterialTheme.typography.bodyMedium) },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(imeAction = if (enterSendsMessage) ImeAction.Send else ImeAction.Default),
                                    keyboardActions = KeyboardActions(onSend = { if (enterSendsMessage) sendTextMessage() }),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    maxLines = 3
                                )

                                IconButton(onClick = {
                                    if (!showEmojiPicker) keyboardController?.hide()
                                    showEmojiPicker = !showEmojiPicker
                                }) {
                                    Icon(
                                        imageVector = if (showEmojiPicker) Icons.Default.Keyboard else Icons.Default.SentimentSatisfiedAlt,
                                        contentDescription = "Emoji",
                                        tint = if (showEmojiPicker) ParrotPurple else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        FloatingActionButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    sendTextMessage()
                                } else {
                                    showEmojiPicker = false
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        isRecordingVoice = true
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            shape = CircleShape,
                            containerColor = ParrotPurple,
                            contentColor = Color.White,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Icon(
                                imageVector = if (messageText.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                contentDescription = "Send"
                            )
                        }
                    }

                    if (showEmojiPicker) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(270.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    EmojiPickerView(ctx).apply {
                                        setOnEmojiPickedListener { emojiViewItem -> messageText += emojiViewItem.emoji }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isRecordingVoice) {
            VoiceNoteOverlay(
                onCancel = { isRecordingVoice = false },
                onSendVoice = { durationFormatted, recordedBase64 ->
                    localMessages.add(
                        ChatMessageUI(
                            id = System.currentTimeMillis().toString(),
                            senderName = currentUser.name,
                            time = timeFormatter.format(Date()),
                            isMe = true,
                            voiceDuration = durationFormatted,
                            voiceBase64 = recordedBase64
                        )
                    )
                    liveRepo.sendChatMessage(
                        roomId = roomId,
                        currentUser = currentUser,
                        chatPartner = chat,
                        voiceDuration = durationFormatted,
                        voiceBase64 = recordedBase64
                    )
                    isRecordingVoice = false
                }
            )
        }
    }
}

fun isJumboEmojiMessage(msg: ChatMessageUI): Boolean {
    val trimmed = msg.text.trim()
    if (trimmed.isEmpty() ||
        msg.photoBase64List.isNotEmpty() ||
        msg.photoColors.isNotEmpty() ||
        msg.sharedContactName != null ||
        msg.voiceDuration != null ||
        msg.documentName != null ||
        msg.latitude != null
    ) {
        return false
    }
    val hasStandardChars = trimmed.any { it.isLetterOrDigit() || it.code in 33..126 }
    if (hasStandardChars) return false

    val noSpaces = trimmed.replace(" ", "")
    val visibleLength = noSpaces.codePointCount(0, noSpaces.length)
    return visibleLength in 1..6
}

private fun formatCoordinate(value: Double): String {
    val rounded = (value * 10000.0).roundToInt() / 10000.0
    return rounded.toString()
}

@Composable
fun ChatBubbleItem(
    chat: ChatPreview,
    msg: ChatMessageUI,
    senderColor: Color,
    senderInitial: String,
    onReplyClick: (ChatMessageUI) -> Unit
) {
    val context = LocalContext.current
    val isJumboEmoji = remember(msg) { isJumboEmojiMessage(msg) }
    var isPlayingVoice by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activePlayer?.release()
            activePlayer = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReplyClick(msg) },
        horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start
        ) {
            if (!msg.isMe) {
                Box(modifier = Modifier.size(38.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(senderColor)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = senderInitial, fontWeight = FontWeight.Bold, color = ParrotCharcoal)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                            .align(Alignment.BottomEnd)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            if (isJumboEmoji) {
                Text(
                    text = msg.text.trim(),
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (msg.isMe) 18.dp else 4.dp,
                        bottomEnd = if (msg.isMe) 4.dp else 18.dp
                    ),
                    color = if (msg.isMe) ParrotPurple else MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 1.dp,
                    modifier = Modifier.widthIn(max = 270.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // --- SENDER NAME IN GROUP CHATS ---
                        if (chat.isGroup && !msg.isMe) {
                            Text(
                                text = if (msg.senderName.isNotBlank()) msg.senderName else "Friend",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ParrotYellow
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Quoted Reply Preview Box
                        if (msg.replyToText != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(modifier = Modifier.padding(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(28.dp)
                                            .background(if (msg.isMe) ParrotYellow else ParrotPurple, RoundedCornerShape(1.5.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = msg.replyToSenderName ?: "Original Message",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.isMe) ParrotYellow else ParrotPurple
                                        )
                                        Text(
                                            text = msg.replyToText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (msg.isMe) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Shared Gallery Photos
                        if (msg.photoBase64List.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    msg.photoBase64List.take(2).forEach { b64 ->
                                        val bmp = remember(b64) {
                                            try {
                                                val bytes = Base64.decode(b64, Base64.DEFAULT)
                                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                            } catch (e: Exception) { null }
                                        }
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp,
                                                contentDescription = "Photo",
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(if (msg.photoBase64List.size == 1) 180.dp else 100.dp)
                                                    .clip(RoundedCornerShape(10.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                                if (msg.photoBase64List.size > 2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        msg.photoBase64List.drop(2).take(2).forEach { b64 ->
                                            val bmp = remember(b64) {
                                                try {
                                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                                } catch (e: Exception) { null }
                                            }
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp,
                                                    contentDescription = "Photo",
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(100.dp)
                                                        .clip(RoundedCornerShape(10.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (msg.text.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Shared Contact Card
                        if (msg.sharedContactName != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = if (msg.isMe) Color.White else ParrotPurple,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = msg.sharedContactName,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.isMe) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = msg.sharedContactPhone ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (msg.isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Shared Document Card
                        if (msg.documentName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.1f))
                                    .clickable {
                                        if (!msg.documentBase64.isNullOrBlank()) {
                                            try {
                                                val bytes = Base64.decode(msg.documentBase64, Base64.DEFAULT)
                                                val safeFile = File(context.cacheDir, msg.documentName)
                                                FileOutputStream(safeFile).use { it.write(bytes) }
                                                val fileUri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    safeFile
                                                )
                                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(fileUri, msg.documentMimeType ?: "application/pdf")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(openIntent, "Open Document"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Document",
                                    tint = if (msg.isMe) ParrotYellow else ParrotPurple,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = msg.documentName,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${msg.documentSizeLabel ?: "File"} • Tap to open",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (msg.isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Current Location Card
                        if (msg.latitude != null && msg.longitude != null) {
                            val uriHandler = LocalUriHandler.current
                            val mapsUrl = "https://www.google.com/maps/search/?api=1&query=${msg.latitude},${msg.longitude}"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.1f))
                                    .clickable { uriHandler.openUri(mapsUrl) }
                                    .padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (msg.isMe) ParrotYellow else ParrotPurple.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Location",
                                        tint = if (msg.isMe) ParrotCharcoal else ParrotPurple
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Current Location",
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.isMe) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tap to open in Google Maps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (msg.isMe) ParrotYellow else ParrotPurple
                                    )
                                }
                            }
                        }

                        // Voice Note Bubble
                        if (msg.voiceDuration != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    if (isPlayingVoice) {
                                        activePlayer?.stop()
                                        activePlayer?.release()
                                        activePlayer = null
                                        isPlayingVoice = false
                                    } else if (!msg.voiceBase64.isNullOrBlank()) {
                                        try {
                                            val audioBytes = Base64.decode(msg.voiceBase64, Base64.DEFAULT)
                                            val tempFile = File(context.cacheDir, "play_${msg.id}.m4a")
                                            FileOutputStream(tempFile).use { it.write(audioBytes) }

                                            val player = MediaPlayer().apply {
                                                setDataSource(tempFile.absolutePath)
                                                prepare()
                                                start()
                                                setOnCompletionListener {
                                                    isPlayingVoice = false
                                                    release()
                                                    activePlayer = null
                                                }
                                            }
                                            activePlayer = player
                                            isPlayingVoice = true
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Unable to play audio", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Record a new voice note to test playback!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (msg.isMe) Color.White.copy(alpha = 0.2f) else ParrotPurple.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingVoice) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Stop",
                                        tint = if (msg.isMe) Color.White else ParrotPurple
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isPlayingVoice) "Playing..." else "Voice Note",
                                        color = if (msg.isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = msg.voiceDuration,
                                        color = if (msg.isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        // Text + Auto Link Detection
                        if (msg.text.isNotEmpty()) {
                            val uriHandler = LocalUriHandler.current
                            val textColor = if (msg.isMe) Color.White else MaterialTheme.colorScheme.onSurface
                            val linkColor = if (msg.isMe) ParrotYellow else ParrotPurple
                            val firstUrl = remember(msg.text) {
                                msg.text.split(" ").firstOrNull {
                                    it.startsWith("http://") || it.startsWith("https://")
                                }
                            }

                            val styledText = buildAnnotatedString {
                                val words = msg.text.split(" ")
                                words.forEachIndexed { index, word ->
                                    if (word.startsWith("http://") || word.startsWith("https://")) {
                                        withStyle(
                                            SpanStyle(
                                                color = linkColor,
                                                textDecoration = TextDecoration.Underline,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append(word)
                                        }
                                    } else {
                                        withStyle(SpanStyle(color = textColor)) {
                                            append(word)
                                        }
                                    }
                                    if (index < words.lastIndex) append(" ")
                                }
                            }

                            Text(
                                text = styledText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = if (firstUrl != null) {
                                    Modifier.clickable { uriHandler.openUri(firstUrl) }
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = msg.time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = if (msg.isMe) 0.dp else 48.dp)
        )
    }
}

@Composable
fun VoiceNoteOverlay(
    onCancel: () -> Unit,
    onSendVoice: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var seconds by remember { mutableIntStateOf(0) }
    val audioFile = remember { File(context.cacheDir, "recorded_voice_note.m4a") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }

    val liveAmplitudes = remember {
        mutableStateListOf<Float>().apply {
            repeat(40) { add(0.03f) }
        }
    }

    DisposableEffect(Unit) {
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            if (audioFile.exists()) audioFile.delete()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(16000)
            mediaRecorder.setAudioEncodingBitRate(24000)
            mediaRecorder.setOutputFile(audioFile.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            try {
                recorder?.stop()
            } catch (_: Exception) { }
            recorder?.release()
            recorder = null
        }
    }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(60L)
            tick++
            if (tick % 16 == 0) {
                seconds++
                if (seconds >= 30) {
                    try {
                        recorder?.stop()
                        recorder?.release()
                        recorder = null
                    } catch (_: Exception) { }
                    val b64 = if (audioFile.exists()) {
                        Base64.encodeToString(audioFile.readBytes(), Base64.DEFAULT)
                    } else null
                    onSendVoice("00:30", b64)
                    break
                }
            }

            val rawAmp = try {
                recorder?.maxAmplitude ?: 0
            } catch (_: Exception) {
                0
            }
            val normalized = (rawAmp / 14000f).coerceIn(0.03f, 1f)
            if (liveAmplitudes.isNotEmpty()) {
                liveAmplitudes.removeAt(0)
                liveAmplitudes.add(normalized)
            }
        }
    }

    val formattedTime = remember(seconds) {
        val mins = (seconds / 60).toString().padStart(2, '0')
        val secs = (seconds % 60).toString().padStart(2, '0')
        "$mins:$secs"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 28.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row {
                    Text(text = "Voice ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Note", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Canvas(modifier = Modifier.fillMaxWidth().height(75.dp)) {
                    val purplePath = Path()
                    val greenPath = Path()
                    val midY = size.height / 2f
                    val count = liveAmplitudes.size
                    val stepX = size.width / (count - 1).coerceAtLeast(1)

                    for (i in 0 until count) {
                        val x = i * stepX
                        val amp = liveAmplitudes[i]
                        val direction = if (i % 2 == 0) 1f else -1f
                        val yPurple = midY + (direction * amp * (midY * 0.9f))
                        val yGreen = midY - (direction * amp * (midY * 0.6f))

                        if (i == 0) {
                            purplePath.moveTo(x, yPurple)
                            greenPath.moveTo(x, yGreen)
                        } else {
                            purplePath.lineTo(x, yPurple)
                            greenPath.lineTo(x, yGreen)
                        }
                    }

                    drawPath(path = purplePath, color = ParrotPurple, style = Stroke(width = 3.dp.toPx()))
                    drawPath(path = greenPath, color = OnlineGreen, style = Stroke(width = 2.dp.toPx()))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = formattedTime, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        try {
                            recorder?.stop()
                            recorder?.release()
                            recorder = null
                            if (audioFile.exists()) audioFile.delete()
                        } catch (_: Exception) { }
                        onCancel()
                    }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    Box(
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(ParrotPurple.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(ParrotPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "Mic", tint = Color.White)
                        }
                    }

                    IconButton(onClick = {
                        try {
                            recorder?.stop()
                        } catch (_: Exception) { }
                        recorder?.release()
                        recorder = null

                        val encodedAudio = if (audioFile.exists() && audioFile.length() > 0) {
                            Base64.encodeToString(audioFile.readBytes(), Base64.DEFAULT)
                        } else null

                        onSendVoice(formattedTime, encodedAudio)
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}