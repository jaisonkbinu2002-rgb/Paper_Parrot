package com.genzcodeworks.paperparrot.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.genzcodeworks.paperparrot.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.genzcodeworks.paperparrot.ui.theme.*
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlin.math.max
import kotlin.math.min

// Firebase Web Client ID (client_type 3 from google-services.json)
private const val WEB_CLIENT_ID = "468438062839-agtb55kb4nar8cs6vn33pi5h8qrsgkj7.apps.googleusercontent.com"

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val memberTag: String = "",
    val email: String = "",
    val phone: String = "",
    val googlePhotoUrl: String = "",
    val customPhotoBase64: String? = null,
    val isProfileComplete: Boolean = false
)

// --- 1. REAL GOOGLE SIGN-IN / SIGN-UP SCREEN ---
@Composable
fun GoogleSignInScreen(
    onGoogleAuthSuccess: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF5E43D8),
                        Color(0xFF3B269B),
                        Color(0xFF271773)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- TOP BRAND HEADER (Transparent background-removed logo top-left, Title & Tagline) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 62.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Top-Left Transparent Parrot Head Logo
                Image(
                    painter = painterResource(id = R.drawable.app_icon_removebg),
                    contentDescription = "Paper Parrot Logo",
                    modifier = Modifier.size(110.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Title: Paper Parrot
                Text(
                    text = "Paper Parrot",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Tagline: Perfect Private Messenger
                Text(
                    text = "The Perfect Private Messenger",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp
                )
            }

            // --- BOTTOM AUTH CARD & CREDITS ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Modern Auth Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Welcome Friends!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sign in or sign up instantly with your Google account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            enabled = !isLoading,
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    try {
                                        if (WEB_CLIENT_ID.startsWith("YOUR_")) {
                                            val tag = "@hareesh_" + (100..999).random()
                                            onGoogleAuthSuccess(
                                                UserProfile(
                                                    uid = "demo_hareesh_uid",
                                                    name = "Hareesh Marar",
                                                    memberTag = tag,
                                                    email = "hareesh@genzcodeworks.com",
                                                    phone = "",
                                                    isProfileComplete = false
                                                )
                                            )
                                        } else {
                                            val credentialManager = CredentialManager.create(context)
                                            val googleIdOption = GetGoogleIdOption.Builder()
                                                .setFilterByAuthorizedAccounts(false)
                                                .setServerClientId(WEB_CLIENT_ID)
                                                .build()
                                            val request = GetCredentialRequest.Builder()
                                                .addCredentialOption(googleIdOption)
                                                .build()

                                            val result = credentialManager.getCredential(context, request)
                                            val credential = result.credential

                                            if (credential is CustomCredential &&
                                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                            ) {
                                                val googleIdCred = GoogleIdTokenCredential.createFrom(credential.data)
                                                val firebaseCred = GoogleAuthProvider.getCredential(googleIdCred.idToken, null)
                                                val authResult = FirebaseAuth.getInstance()
                                                    .signInWithCredential(firebaseCred)
                                                    .await()
                                                val fbUser = authResult.user!!

                                                val db = FirebaseFirestore.getInstance()
                                                val doc = try {
                                                    db.collection("users").document(fbUser.uid).get().await()
                                                } catch (_: Exception) {
                                                    try {
                                                        db.collection("users").document(fbUser.uid).get(com.google.firebase.firestore.Source.CACHE).await()
                                                    } catch (_: Exception) {
                                                        null
                                                    }
                                                }

                                                if (doc != null && doc.exists()) {
                                                    val existingProfile = doc.toObject(UserProfile::class.java)
                                                    if (existingProfile != null) {
                                                        if (existingProfile.customPhotoBase64 == null &&
                                                            existingProfile.googlePhotoUrl.isNotBlank()
                                                        ) {
                                                            val downloadedAvatar = fetchGooglePhotoAsBase64(existingProfile.googlePhotoUrl)
                                                            onGoogleAuthSuccess(existingProfile.copy(customPhotoBase64 = downloadedAvatar))
                                                        } else {
                                                            onGoogleAuthSuccess(existingProfile)
                                                        }
                                                    }
                                                } else {
                                                    val rawName = fbUser.displayName
                                                        ?: googleIdCred.displayName
                                                        ?: "Hareesh Marar"
                                                    val defaultTag = "@" + rawName.lowercase().replace(" ", "_")
                                                    val photoUriString = (fbUser.photoUrl ?: googleIdCred.profilePictureUri)?.toString()

                                                    val googleAvatarBase64 = fetchGooglePhotoAsBase64(photoUriString)

                                                    onGoogleAuthSuccess(
                                                        UserProfile(
                                                            uid = fbUser.uid,
                                                            name = rawName,
                                                            memberTag = defaultTag,
                                                            email = fbUser.email ?: googleIdCred.id,
                                                            phone = fbUser.phoneNumber ?: "",
                                                            googlePhotoUrl = photoUriString ?: "",
                                                            customPhotoBase64 = googleAvatarBase64,
                                                            isProfileComplete = false
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Sign-in note: ${e.localizedMessage}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ParrotPurple,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = "Google",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continue with Google",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Credits Line
                Text(
                    text = "crafted with care by GenZ codeworks.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

// --- 2. CREATE / EDIT PROFILE SCREEN (WITH WORKING 1:1 CROP + ROTATE) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteOrEditProfileScreen(
    initialProfile: UserProfile,
    isEditingExisting: Boolean = false,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    onSaveProfile: (UserProfile) -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialProfile.name) }
    var memberTag by remember {
        mutableStateOf(
            if (initialProfile.memberTag.isNotBlank()) initialProfile.memberTag
            else "@" + initialProfile.name.lowercase().replace(" ", "_")
        )
    }
    var phone by remember { mutableStateOf(initialProfile.phone) }
    var customBase64 by remember { mutableStateOf(initialProfile.customPhotoBase64) }
    var bitmapToCrop by remember { mutableStateOf<Bitmap?>(null) }

    // 1. Photo Picker Launcher
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            bitmapToCrop = BitmapFactory.decodeStream(stream)
        }
    }

    // 2. Google Play Services Phone Number Hint Picker
    val phoneHintLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            val hintedPhone = Identity.getSignInClient(context).getPhoneNumberFromIntent(result.data)
            if (!hintedPhone.isNullOrBlank()) {
                phone = hintedPhone
            }
        } catch (_: Exception) { }
    }

    val triggerPhoneHint = {
        val request = GetPhoneNumberHintIntentRequest.builder().build()
        Identity.getSignInClient(context)
            .getPhoneNumberHintIntent(request)
            .addOnSuccessListener { pendingIntent ->
                phoneHintLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
            }
            .addOnFailureListener {
                Toast.makeText(context, "Type your contact number manually", Toast.LENGTH_SHORT).show()
            }
    }

    val isPreview = LocalInspectionMode.current
    LaunchedEffect(Unit) {
        if (!isPreview && phone.isBlank()) {
            triggerPhoneHint()
        }
    }

    // Show 1:1 Square Cropper + Rotate Overlay when an image is picked
    if (bitmapToCrop != null) {
        SquareCropperDialog(
            bitmap = bitmapToCrop!!,
            onCancel = { bitmapToCrop = null },
            onCropSuccess = { croppedBase64 ->
                customBase64 = croppedBase64
                bitmapToCrop = null
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Purple Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
                Text(
                    text = if (isEditingExisting) "Edit Profile" else "Complete Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "Auto-filled from your Google Account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

        // Rounded Bottom Sheet Form
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
                    .imePadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1:1 Avatar Picker
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clickable {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(ParrotYellow)
                            .border(3.dp, ParrotPurple, CircleShape)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        if (customBase64 != null) {
                            val bmp = remember(customBase64) {
                                val bytes = Base64.decode(customBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }
                            bmp?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            Text(
                                text = name.firstOrNull()?.uppercase() ?: "H",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = ParrotCharcoal
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ParrotPurple)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap photo to select, rotate & crop 1:1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Editable Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name (Editable)") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Editable Member Tag
                OutlinedTextField(
                    value = memberTag,
                    onValueChange = {
                        val cleaned = it.lowercase().replace(" ", "_")
                        memberTag = if (cleaned.startsWith("@")) cleaned else "@$cleaned"
                    },
                    label = { Text("Member Tag (Editable)") },
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Read-Only Google Email
                OutlinedTextField(
                    value = initialProfile.email,
                    onValueChange = {},
                    enabled = false,
                    label = { Text("Google Email (Verified)") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Contact Number + 1-Tap Google Auto-Fill Icon Button
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = { triggerPhoneHint() }) {
                            Text("Auto-fill", color = ParrotPurple, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Dark / Light Theme Switch inside Profile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dark Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme() }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Complete / Save Profile Button
                Button(
                    onClick = {
                        if (name.isNotBlank() && memberTag.length > 1) {
                            val completedProfile = initialProfile.copy(
                                name = name.trim(),
                                memberTag = memberTag.trim(),
                                phone = phone.trim(),
                                customPhotoBase64 = customBase64,
                                isProfileComplete = true
                            )
                            if (!WEB_CLIENT_ID.startsWith("YOUR_")) {
                                FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(completedProfile.uid)
                                    .set(completedProfile)
                            }
                            onSaveProfile(completedProfile)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ParrotPurple,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEditingExisting) "Save Changes" else "Complete Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// --- 3. WORKING 1:1 SQUARE IMAGE CROPPER WITH ROTATE & ZOOM ---
@Composable
fun SquareCropperDialog(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onCropSuccess: (String) -> Unit
) {
    // Holds the currently rotated bitmap so rotation is crisp and immediate
    var workingBitmap by remember(bitmap) { mutableStateOf(bitmap) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val cropBoxDp = 300.dp
    val cropBoxPx = with(LocalDensity.current) { cropBoxDp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Top Instructions
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Crop & Rotate 1:1",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pinch to zoom, drag to move, or use rotate buttons",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Interactive 1:1 Crop Box (300dp x 300dp)
        Box(
            modifier = Modifier
                .size(cropBoxDp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A1A))
                .pointerInput(workingBitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        // Limit pan so the user can't drag the photo completely out of the box
                        val maxOffset = (cropBoxPx * (newScale - 1f)) / 2f + (cropBoxPx * 0.35f)
                        val newOffsetX = (offset.x + pan.x).coerceIn(-maxOffset, maxOffset)
                        val newOffsetY = (offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                        scale = newScale
                        offset = Offset(newOffsetX, newOffsetY)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = workingBitmap.asImageBitmap(),
                contentDescription = "Crop 1:1",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Crop
            )

            // 1:1 Square Grid & Yellow Border Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val third = size.width / 3f
                // Rule-of-thirds guide lines
                drawLine(Color.White.copy(alpha = 0.35f), Offset(third, 0f), Offset(third, size.height), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.35f), Offset(third * 2, 0f), Offset(third * 2, size.height), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, third), Offset(size.width, third), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, third * 2), Offset(size.width, third * 2), strokeWidth = 1.dp.toPx())

                // Outer 1:1 Yellow Frame
                drawRect(
                    color = ParrotYellow,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Bottom Controls: Rotate Bar + Cancel / Crop Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rotate Left (-90°), Reset, Rotate Right (+90°) Pill Bar
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = {
                            workingBitmap = rotateBitmap(workingBitmap, -90f)
                            scale = 1f
                            offset = Offset.Zero
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = "Rotate Left 90°",
                            tint = Color.White
                        )
                    }

                    TextButton(
                        onClick = {
                            workingBitmap = bitmap
                            scale = 1f
                            offset = Offset.Zero
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = ParrotYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset", color = ParrotYellow, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            workingBitmap = rotateBitmap(workingBitmap, 90f)
                            scale = 1f
                            offset = Offset.Zero
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate Right 90°",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cancel & Save 1:1 Crop Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel", color = Color.White)
                }
                Button(
                    onClick = {
                        val base64 = cropTransformedSquareToBase64(
                            source = workingBitmap,
                            boxSizePx = cropBoxPx,
                            userScale = scale,
                            userOffset = offset
                        )
                        onCropSuccess(base64)
                    },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ParrotYellow,
                        contentColor = ParrotCharcoal
                    )
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crop 1:1 & Use", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Rotates a Bitmap by 90 or -90 degrees
fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

// Renders the exact zoomed, dragged, and rotated 1:1 square view into a 300x300 Base64 JPEG
fun cropTransformedSquareToBase64(
    source: Bitmap,
    boxSizePx: Float,
    userScale: Float,
    userOffset: Offset
): String {
    val outputSize = 300
    val outputBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(outputBitmap)

    // Ratio between our 300px output bitmap and the on-screen 300.dp crop box
    val viewportToOutput = outputSize / boxSizePx

    // ContentScale.Crop base scale so the shorter side fills the 300px square
    val baseCoverScale = max(
        outputSize.toFloat() / source.width.toFloat(),
        outputSize.toFloat() / source.height.toFloat()
    )

    val matrix = Matrix().apply {
        // 1. Center bitmap at origin
        postTranslate(-source.width / 2f, -source.height / 2f)
        // 2. Apply ContentScale.Crop base scale + user pinch zoom
        val totalScale = baseCoverScale * userScale
        postScale(totalScale, totalScale)
        // 3. Move to center of output 300x300 square + user finger drag offset
        postTranslate(
            outputSize / 2f + (userOffset.x * viewportToOutput),
            outputSize / 2f + (userOffset.y * viewportToOutput)
        )
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, matrix, paint)

    val out = ByteArrayOutputStream()
    outputBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
    return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
}

// Center-crops Google Account avatar URLs into 1:1 Base64
fun cropBitmapCenter1to1Base64(source: Bitmap): String {
    val size = min(source.width, source.height)
    val x = max(0, (source.width - size) / 2)
    val y = max(0, (source.height - size) / 2)
    val square = Bitmap.createBitmap(source, x, y, size, size)
    val scaled = Bitmap.createScaledBitmap(square, 300, 300, true)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
    return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
}

// Downloads the user's Google Avatar URL in the background and converts it to Base64
suspend fun fetchGooglePhotoAsBase64(photoUrl: String?): String? {
    if (photoUrl.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            val highResUrl = photoUrl.replace("s96-c", "s400-c")
            val stream = URL(highResUrl).openStream()
            val bitmap = BitmapFactory.decodeStream(stream)
            if (bitmap != null) cropBitmapCenter1to1Base64(bitmap) else null
        } catch (e: Exception) {
            null
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GoogleSignInScreenPreview() {
    PaperParrotTheme {
        GoogleSignInScreen(onGoogleAuthSuccess = {})
    }
}

@Preview(showBackground = true)
@Composable
fun CompleteOrEditProfileScreenPreview() {
    PaperParrotTheme {
        CompleteOrEditProfileScreen(
            initialProfile = UserProfile(
                uid = "demo_1",
                name = "Alex Morgan",
                memberTag = "@alex",
                email = "alex@example.com",
                isProfileComplete = false
            ),
            isEditingExisting = false,
            isDarkTheme = false,
            onToggleTheme = {},
            onSaveProfile = {}
        )
    }
}