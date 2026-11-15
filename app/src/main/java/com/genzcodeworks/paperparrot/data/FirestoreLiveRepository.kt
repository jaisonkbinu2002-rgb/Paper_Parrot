package com.genzcodeworks.paperparrot.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.graphics.Color
import com.genzcodeworks.paperparrot.ui.screens.ChatPreview
import com.genzcodeworks.paperparrot.ui.screens.ContactItem
import com.genzcodeworks.paperparrot.ui.screens.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class FeedPost(
    val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorTag: String = "",
    val authorAvatarBase64: String? = null,
    val content: String = "",
    val timestamp: Long = 0L,
    val likesCount: Int = 0
)

data class LiveMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val photoBase64List: List<String> = emptyList(),
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
    val locationAddress: String? = null
)

class FirestoreLiveRepository {
    private val db = FirebaseFirestore.getInstance()

    private val avatarColors = listOf(
        Color(0xFFFFB74D),
        Color(0xFFA1887F),
        Color(0xFFFF8A65),
        Color(0xFF90CAF9),
        Color(0xFFCE93D8),
        Color(0xFF81C784)
    )

    fun resolveRoomId(currentUid: String, targetId: String, isGroup: Boolean): String {
        if (isGroup || targetId.contains("_")) return targetId
        return if (currentUid < targetId) "${currentUid}_${targetId}" else "${targetId}_${currentUid}"
    }

    // 1. Update User Profile & Online Presence
    fun syncUserAndPresence(user: UserProfile, isOnline: Boolean) {
        if (user.uid.isBlank() || user.uid.startsWith("demo_")) return
        val data = mapOf(
            "uid" to user.uid,
            "name" to user.name,
            "memberTag" to user.memberTag,
            "email" to user.email,
            "phone" to user.phone,
            "googlePhotoUrl" to user.googlePhotoUrl,
            "customPhotoBase64" to user.customPhotoBase64,
            "isProfileComplete" to true,
            "isOnline" to isOnline,
            "lastSeen" to System.currentTimeMillis()
        )
        db.collection("users").document(user.uid).set(data, SetOptions.merge())
    }

    // 2. Observe All Registered Users Live (Including Avatars)
    fun observeUsers(currentUid: String): Flow<List<ContactItem>> = callbackFlow {
        val listener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val users = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: doc.id
                    if (uid == currentUid) return@mapNotNull null
                    val name = doc.getString("name") ?: "Friend"
                    val isOnline = doc.getBoolean("isOnline") ?: true
                    val customPhoto = doc.getString("customPhotoBase64")
                    val googlePhoto = doc.getString("googlePhotoUrl")
                    val color = avatarColors[abs(uid.hashCode()) % avatarColors.size]
                    ContactItem(
                        id = uid,
                        name = name,
                        isOnline = isOnline,
                        avatarColor = color,
                        avatarBase64 = customPhoto,
                        googlePhotoUrl = googlePhoto
                    )
                }
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    // Search Users for Find & Connect feature
    suspend fun searchUsers(query: String, currentUid: String): List<ContactItem> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = db.collection("users").get().await()
                val lowercaseQuery = query.lowercase().trim()
                snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: doc.id
                    if (uid == currentUid) return@mapNotNull null
                    val name = doc.getString("name") ?: "User"
                    val tag = doc.getString("memberTag") ?: ""
                    val isOnline = doc.getBoolean("isOnline") ?: false
                    val customPhoto = doc.getString("customPhotoBase64")
                    val googlePhoto = doc.getString("googlePhotoUrl")

                    if (name.lowercase().contains(lowercaseQuery) || tag.lowercase().contains(lowercaseQuery)) {
                        val color = avatarColors[abs(uid.hashCode()) % avatarColors.size]
                        ContactItem(
                            id = uid,
                            name = "$name ($tag)",
                            isOnline = isOnline,
                            avatarColor = color,
                            avatarBase64 = customPhoto,
                            googlePhotoUrl = googlePhoto
                        )
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // 3. Observe Live Chats
    fun observeChats(currentUid: String): Flow<List<ChatPreview>> = callbackFlow {
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val listener = db.collection("chats")
            .whereArrayContains("participants", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { doc ->
                    val isGroup = doc.getBoolean("isGroup") ?: false
                    @Suppress("UNCHECKED_CAST")
                    val participantNames = doc.get("participantNames") as? Map<String, String>
                    val otherName = participantNames?.entries?.firstOrNull { it.key != currentUid }?.value
                    val title = if (isGroup) {
                        doc.getString("groupName") ?: doc.getString("title") ?: "Group Chat"
                    } else {
                        otherName ?: doc.getString("title") ?: "Chat"
                    }
                    val lastMsg = doc.getString("lastMessage") ?: "Tap to chat"
                    val lastUpdated = doc.getLong("lastUpdated") ?: System.currentTimeMillis()
                    val isPinned = doc.getBoolean("isPinned") ?: false
                    val color = avatarColors[abs(doc.id.hashCode()) % avatarColors.size]
                    ChatPreview(
                        id = doc.id,
                        name = title,
                        lastMessage = lastMsg,
                        time = timeFmt.format(Date(lastUpdated)),
                        unreadCount = 0,
                        isPinned = isPinned,
                        isGroup = isGroup,
                        isOnline = true,
                        avatarColor = color
                    )
                }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // 4. Create Group Chat
    fun createGroupChat(
        creator: UserProfile,
        groupName: String,
        selectedFriends: List<ContactItem>
    ): ChatPreview {
        val docRef = db.collection("chats").document()
        val now = System.currentTimeMillis()

        val participantIds = (selectedFriends.map { it.id } + creator.uid).distinct()
        val participantNames = mutableMapOf(creator.uid to creator.name).apply {
            selectedFriends.forEach { put(it.id, it.name) }
        }

        val groupMeta = mapOf(
            "id" to docRef.id,
            "groupName" to groupName.trim(),
            "title" to groupName.trim(),
            "isGroup" to true,
            "isPinned" to false,
            "participants" to participantIds,
            "participantNames" to participantNames,
            "createdBy" to creator.uid,
            "lastMessage" to "\${creator.name} created group \"\$groupName\"",
            "lastUpdated" to now
        )

        docRef.set(groupMeta)

        val color = avatarColors[abs(docRef.id.hashCode()) % avatarColors.size]
        return ChatPreview(
            id = docRef.id,
            name = groupName.trim(),
            lastMessage = "\${creator.name} created group \"\$groupName\"",
            time = "Just now",
            unreadCount = 0,
            isPinned = false,
            isGroup = true,
            isOnline = true,
            avatarColor = color
        )
    }

    // 5. Observe Messages
    fun observeChatMessages(roomId: String): Flow<List<LiveMessage>> = callbackFlow {
        val listener = db.collection("chats")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val msgs = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(LiveMessage::class.java)?.copy(id = doc.id)
                }
                trySend(msgs)
            }
        awaitClose { listener.remove() }
    }

    // 6. Send Message
    fun sendChatMessage(
        roomId: String,
        currentUser: UserProfile,
        chatPartner: ChatPreview,
        text: String = "",
        photoBase64List: List<String> = emptyList(),
        sharedContactName: String? = null,
        sharedContactPhone: String? = null,
        voiceDuration: String? = null,
        voiceBase64: String? = null,
        documentName: String? = null,
        documentSizeLabel: String? = null,
        documentMimeType: String? = null,
        documentBase64: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAddress: String? = null
    ) {
        val msgRef = db.collection("chats").document(roomId).collection("messages").document()
        val now = System.currentTimeMillis()

        val message = LiveMessage(
            id = msgRef.id,
            senderId = currentUser.uid,
            senderName = currentUser.name,
            text = text,
            timestamp = now,
            photoBase64List = photoBase64List,
            sharedContactName = sharedContactName,
            sharedContactPhone = sharedContactPhone,
            voiceDuration = voiceDuration,
            voiceBase64 = voiceBase64,
            documentName = documentName,
            documentSizeLabel = documentSizeLabel,
            documentMimeType = documentMimeType,
            documentBase64 = documentBase64,
            latitude = latitude,
            longitude = longitude,
            locationAddress = locationAddress
        )

        msgRef.set(message)

        val rawPreview = when {
            photoBase64List.isNotEmpty() -> "📷 Shared \${photoBase64List.size} photo(s)"
            documentName != null -> "📄 Document: \$documentName"
            latitude != null && longitude != null -> "📍 Shared Location"
            sharedContactName != null -> "👤 Contact: \$sharedContactName"
            voiceDuration != null -> "🎤 Voice Note (\$voiceDuration)"
            else -> text
        }

        val previewText = if (chatPartner.isGroup) {
            "${currentUser.name}:$rawPreview"
        } else {
            rawPreview
        }

        val chatMeta = mutableMapOf<String, Any>(
            "title" to chatPartner.name,
            "isGroup" to chatPartner.isGroup,
            "lastMessage" to previewText,
            "lastUpdated" to now
        )

        if (chatPartner.isGroup) {
            chatMeta["groupName"] = chatPartner.name
        } else {
            val participants = if (roomId.contains("_")) {
                roomId.split("_")
            } else {
                listOf(currentUser.uid, chatPartner.id).distinct()
            }
            chatMeta["participants"] = participants
            chatMeta["participantNames"] = mapOf(
                currentUser.uid to currentUser.name,
                chatPartner.id to chatPartner.name
            )
        }

        db.collection("chats").document(roomId).set(chatMeta, SetOptions.merge())
    }

    // 7. Compress Photo
    suspend fun compressUriToBase64(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val original = BitmapFactory.decodeStream(stream) ?: return@withContext null
                val maxDim = 550f
                val ratio = minOf(maxDim / original.width, maxDim / original.height, 1f)
                val width = (original.width * ratio).toInt().coerceAtLeast(1)
                val height = (original.height * ratio).toInt().coerceAtLeast(1)
                val resized = Bitmap.createScaledBitmap(original, width, height, true)
                val out = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 65, out)
                Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
            } catch (e: Exception) {
                null
            }
        }
    }

    // 8. Read Contact
    fun readContactFromUri(context: Context, contactUri: Uri): Pair<String, String>? {
        return try {
            var name = "Contact"
            var contactId = ""
            context.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: "Contact"
                    if (idIdx >= 0) contactId = cursor.getString(idIdx) ?: ""
                }
            }
            var phone = "Tap to view"
            if (contactId.isNotEmpty()) {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    "\${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { pCursor ->
                    if (pCursor.moveToFirst()) {
                        val numIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numIdx >= 0) phone = pCursor.getString(numIdx) ?: ""
                    }
                }
            }
            Pair(name, phone)
        } catch (e: Exception) {
            Pair("Shared Friend", "Contact Card")
        }
    }

    // 9. Read Document
    suspend fun readDocumentFromUri(context: Context, uri: Uri): Triple<String, String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                var fileName = "Document.pdf"
                var fileSize = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: "Document"
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }
                if (fileSize > 550 * 1024) return@withContext Triple("TOO_LARGE", "", "")

                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext null
                if (bytes.size > 550 * 1024) return@withContext Triple("TOO_LARGE", "", "")

                val sizeKb = (bytes.size / 1024).coerceAtLeast(1)
                val sizeLabel = "\$sizeKb KB"
                val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                Triple(fileName, sizeLabel, base64)
            } catch (e: Exception) {
                null
            }
        }
    }

    // 10. Get Location
    @SuppressLint("MissingPermission")
    fun getCurrentDeviceLocation(context: Context): Pair<Double, Double>? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            }
            bestLocation?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }

    // 11. Feed
    fun observeFeedPosts(): Flow<List<FeedPost>> = callbackFlow {
        val listener = db.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val posts = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(FeedPost::class.java)?.copy(id = doc.id)
                }
                trySend(posts)
            }
        awaitClose { listener.remove() }
    }

    fun createFeedPost(user: UserProfile, text: String) {
        val docRef = db.collection("posts").document()
        val post = FeedPost(
            id = docRef.id,
            authorUid = user.uid,
            authorName = user.name,
            authorTag = user.memberTag,
            authorAvatarBase64 = user.customPhotoBase64,
            content = text,
            timestamp = System.currentTimeMillis(),
            likesCount = 0
        )
        docRef.set(post)
    }

    fun likeFeedPost(postId: String, currentLikes: Int) {
        db.collection("posts").document(postId).update("likesCount", currentLikes + 1)
    }
}