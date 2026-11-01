package com.example.csc490seniorproject.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.viewmodels.LandingScreenVM
import com.example.csc490seniorproject.viewmodels.ProfileScreenVM
import com.google.firebase.auth.FirebaseAuth
import com.example.csc490seniorproject.viewmodels.FriendStatus
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import com.example.csc490seniorproject.objects.Song
import com.google.firebase.firestore.FirebaseFirestore
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateMapOf
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.google.firebase.storage.FirebaseStorage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import com.google.firebase.firestore.SetOptions

private enum class ProfileTab { SONGS, FRIENDS, CHATS }
@Composable
fun ProfileScreen(viewModel: ProfileScreenVM, navController: NavController, username: String,profileID: String? = null) {
    var isEditing by remember(username) {
        mutableStateOf(false)
    }
    var editableUsername by remember(username) {
        mutableStateOf(username)
    }
    var aboutMe by remember(username) {
        mutableStateOf("This is the about me section. Users are free to type a brief description for other users to get to know them.")
    }
    var profilePictureUri by remember(username) {
        mutableStateOf<Uri?>(null)
    }

    val availableMusicTags = listOf(
        "Rock",
        "Pop",
        "Jazz",
        "Hip-Hop",
        "Country"
    )

    var selectedMusicTags by remember(username) {
        mutableStateOf(setOf<String>())
    }

    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val fredoka = remember {
        FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))
    }

    var currentUid by remember() {
        mutableStateOf( auth.currentUser?.uid)
    }



    val ProfilePurple = Color(0xFF5D36EB)
    val ProfileCardPurple = Color(0xFFBFB1FA)

    DisposableEffect(auth, profileID) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUid = it.currentUser?.uid
        }.also {
            auth.addAuthStateListener(it)
        }

        onDispose {
            listener?.let {
                auth.removeAuthStateListener(it)
            }
        }
    }
    val profileUid = profileID ?: viewModel.profileUid

    LaunchedEffect(username, currentUid) {
        viewModel.loadProfile(username, currentUid)

        val uid = if (profileID == profileUid) profileID else currentUid

        uid?.let { profileUid ->
            viewModel.getProfileDetails(profileUid) {
                aboutMe = viewModel.aboutMe
                editableUsername = viewModel.profileUsername
                profilePictureUri =
                    if (viewModel.profilePictureUrl.isNotBlank()) {
                        Uri.parse(viewModel.profilePictureUrl)
                    } else {
                        null
                    }
            }
        }
    }

    LaunchedEffect(profileUid) {
        val uid = profileUid ?: return@LaunchedEffect

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val savedTags = document.get("musicTags") as? List<*>

                selectedMusicTags = savedTags
                    .orEmpty()
                    .filterIsInstance<String>()
                    .filter { it in availableMusicTags }
                    .toSet()
            }
    }


    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            profilePictureUri = uri
            println("PROFILE: Selected image URI = $uri")

            val uid = currentUid

            if (uid != null) {
                viewModel.uploadProfilePicture(
                    uid = uid,
                    imageUri = uri
                )
            } else {
                println("PROFILE: Cannot upload image; currentUid is null")
            }
        } else {
            println("PROFILE: No image selected")
        }
    }

    val friendsList = viewModel.friendsList

    var selectedTab by remember(username, currentUid) {
        mutableStateOf(ProfileTab.SONGS)
    }

    val songsList = remember(username, currentUid) {
        mutableStateListOf<Song>()
    }


    val descriptions = remember(username, currentUid) {
        mutableStateMapOf<String, String>()
    }

    var songsLoading by remember(username, currentUid) {
        mutableStateOf(true)
    }

    var songsError by remember(username, currentUid) {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(profileUid, currentUid, username) {
        var active = true
        songsList.clear()
        descriptions.clear()
        songsError = null
        songsLoading = !profileUid.isNullOrBlank() && currentUid != null

        val listener = if (
            !profileUid.isNullOrBlank() && currentUid != null
        ) {
            db.collection("songs")
                .whereEqualTo("ownerId", profileUid)
                .addSnapshotListener { snapshot, exception ->
                    if (active) {
                        songsLoading = false

                        if (exception != null) {
                            songsError = exception.localizedMessage
                                ?: "Unable to load songs."
                        } else {
                            try {
                                val documents = snapshot?.documents.orEmpty()

                                val songs = documents.mapNotNull { document ->
                                    document.toObject(Song::class.java)
                                        ?.copy(id = document.id)
                                }

                                val savedDescriptions = documents.associate {
                                    it.id to (
                                            it.getString("description")
                                                ?.takeIf { text ->
                                                    text.isNotBlank()
                                                }
                                                ?: "No description provided."
                                            )
                                }

                                songsList.clear()
                                songsList.addAll(
                                    songs.sortedBy {
                                        it.name.lowercase(Locale.ROOT)
                                    }
                                )

                                descriptions.clear()
                                descriptions.putAll(savedDescriptions)
                                songsError = null
                            } catch (exception: Exception) {
                                songsError = exception.localizedMessage
                                    ?: "Unable to read song details."
                            }
                        }
                    }
                }
        } else {
            null
        }

        onDispose {
            active = false
            listener?.remove()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item{
            HorizontalDivider(
                color = Color.White,
                thickness = 2.dp
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(120.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        border = BorderStroke(2.dp, ProfilePurple),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable(enabled = isEditing) {
                                imagePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                    ) {
                        if (profilePictureUri != null) {
                            coil.compose.AsyncImage(
                                model = profilePictureUri,
                                contentDescription = "Profile picture",
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.default_pfp),
                                contentDescription = "Profile picture",
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    if (isEditing) {
                        Surface(
                            shape = CircleShape,
                            color = ProfilePurple,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit profile picture",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 120.dp)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editableUsername,
                            onValueChange = { editableUsername = it },
                            placeholder = { Text("Enter username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = editableUsername,
                                fontFamily = fredoka
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectedMusicTags.take(3).forEach { tag ->
                                    Surface(
                                        color = ProfileCardPurple,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            fontFamily = fredoka,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 5.dp
                                            )
                                        )
                                    }
                                }
                            }

                            if (selectedMusicTags.size > 3) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    selectedMusicTags.drop(3).forEach { tag ->
                                        Surface(
                                            color = ProfileCardPurple,
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                fontFamily = fredoka,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(
                                                    horizontal = 10.dp,
                                                    vertical = 5.dp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isEditing) {
                        Text(
                            text = "Music Preferences",
                            fontFamily = fredoka,
                            fontSize = 14.sp
                        )

                        availableMusicTags.chunked(2).forEach { rowTags ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowTags.forEach { tag ->
                                    FilterChip(
                                        selected = tag in selectedMusicTags,
                                        onClick = {
                                            selectedMusicTags =
                                                if (tag in selectedMusicTags) {
                                                    selectedMusicTags - tag
                                                } else {
                                                    selectedMusicTags + tag
                                                }
                                        },
                                        label = {
                                            Text(tag, fontFamily = fredoka)
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ProfilePurple,
                                            selectedLabelColor = Color.White,
                                            containerColor = Color.White,
                                            labelColor = Color.DarkGray))
                                }
                            }
                        }
                    }

                    if (
                        currentUid != null &&
                        profileUid != null &&
                        currentUid == profileUid
                    ) {
                        Button(
                            onClick = {
                                if (isEditing) {
                                    viewModel.profileUsername = editableUsername
                                    viewModel.aboutMe = aboutMe
                                    viewModel.profilePictureUrl =
                                        profilePictureUri?.toString() ?: ""

                                    profileUid?.let { uid ->
                                        viewModel.updateProfileDetails(uid)

                                        db.collection("users")
                                            .document(uid)
                                            .set(
                                                mapOf(
                                                    "musicTags" to availableMusicTags.filter {
                                                        it in selectedMusicTags
                                                    }
                                                ),
                                                SetOptions.merge()
                                            )
                                            .addOnSuccessListener {
                                                println("Music tags saved successfully")
                                            }
                                            .addOnFailureListener { exception ->
                                                println(
                                                    "Failed to save music tags: ${exception.message}"
                                                )
                                            }
                                    }
                                } else {
                                    profileUid?.let { uid ->
                                        viewModel.getProfileDetails(uid){
                                            aboutMe = viewModel.aboutMe
                                            editableUsername = viewModel.profileUsername
                                            profilePictureUri = if (viewModel.profilePictureUrl.isNotBlank()) {
                                                Uri.parse(viewModel.profilePictureUrl)
                                            } else {
                                                null
                                            }
                                        }

                                    }

                                }
                                isEditing = !isEditing
                                      },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ProfilePurple
                            )
                        ) {
                            Text("✎ Edit Profile", fontFamily = fredoka)
                        }
                    } else if (
                        currentUid != null && profileUid != null
                    ) {

                        val myUid = currentUid
                        val targetUid = profileUid

                        if (myUid != null) {
                            when (viewModel.friendStatus) {
                                FriendStatus.NONE -> {
                                    Button(
                                        onClick = {
                                            viewModel.sendFriendRequest(
                                                myUid,
                                                targetUid
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ProfilePurple
                                        )
                                    ) {
                                        Text("+ Add Friend", fontFamily = fredoka)
                                    }
                                }

                                FriendStatus.REQUEST_SENT -> {
                                    OutlinedButton(
                                        onClick = {},
                                        enabled = false
                                    ) {
                                        Text("Request Sent", fontFamily = fredoka)
                                    }
                                }

                                FriendStatus.REQUEST_RECEIVED -> {
                                    Column {
                                        Button(
                                            onClick = {
                                                viewModel.acceptFriendRequest(
                                                    myUid,
                                                    targetUid
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ProfilePurple
                                            )
                                        ) {
                                            Text("Accept", fontFamily = fredoka)
                                        }

                                        TextButton(
                                            onClick = {
                                                viewModel.declineFriendRequest(
                                                    myUid,
                                                    targetUid
                                                )
                                            }
                                        ) {
                                            Text("Decline", fontFamily = fredoka)
                                        }
                                    }
                                }

                                FriendStatus.FRIENDS -> {
                                    OutlinedButton(
                                        onClick = {},
                                        enabled = false
                                    ) {
                                        Text("✓ Friends", fontFamily = fredoka)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "About Me",
                fontFamily = fredoka,
                fontSize = 24.sp
            )

            Spacer(Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                border = BorderStroke(2.dp, ProfilePurple),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = aboutMe,
                        onValueChange = { aboutMe = it },
                        placeholder = { Text("Write about yourself...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = aboutMe,
                        fontFamily = fredoka,
                        fontSize = 13.sp,
                        color = Color(0xFF444444),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileTabCard(
                    label = "Songs",
                    imageRes = R.drawable.song_image,
                    selected = selectedTab == ProfileTab.SONGS,
                    font = fredoka,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = ProfileTab.SONGS }
                )

                ProfileTabCard(
                    label = "Friends",
                    imageRes = R.drawable.friends,
                    selected = selectedTab == ProfileTab.FRIENDS,
                    font = fredoka,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = ProfileTab.FRIENDS }
                )

                ProfileTabCard(
                    label = "Messages",
                    imageRes = R.drawable.chat,
                    selected = selectedTab == ProfileTab.CHATS,
                    font = fredoka,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = ProfileTab.CHATS }
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = when (selectedTab) {
                    ProfileTab.SONGS -> "Songs"
                    ProfileTab.FRIENDS -> "Friends"
                    ProfileTab.CHATS -> "Messages"
                },
                fontFamily = fredoka,
                fontSize = 20.sp
            )
        }

        when (selectedTab) {
            ProfileTab.SONGS -> {
                item {
                    when {
                        currentUid == null -> {
                            Text("Please log in to see songs.", fontFamily = fredoka)
                        }

                        profileUid.isNullOrBlank() -> {
                            Text(
                                "Waiting for the profile UID…",
                                fontFamily = fredoka
                            )
                        }

                        songsLoading -> {
                            Text("Loading songs…", fontFamily = fredoka)
                        }

                        songsError != null -> {
                            Text(
                                text = songsError.orEmpty(),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        songsList.isEmpty() -> {
                            Text(
                                "No songs yet.",
                                fontFamily = fredoka,
                                color = Color.DarkGray
                            )
                        }
                    }
                }

                items(
                    items = songsList,
                    key = { it.id }
                ) { song ->
                    ProfileSongCard(
                        song = song,
                        description = descriptions[song.id]
                            ?: "No description provided.",
                        font = fredoka,
                        onDetails = {
                            navController.navigate(
                                "SongScreen/${Uri.encode(song.id)}"
                            )
                        }
                    )
                }
            }

            ProfileTab.FRIENDS -> {
                item {
                    if (
                        currentUid != null &&
                        currentUid == profileUid &&
                        viewModel.incomingRequests.isNotEmpty()
                    ) {
                        Text(
                            "Friend Requests",
                            fontFamily = fredoka,
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        viewModel.incomingRequests.forEach { requester ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    requester.username,
                                    fontFamily = fredoka,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = {
                                        currentUid?.let { uid ->
                                            viewModel.acceptFriendRequest(
                                                uid,
                                                requester.uid
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ProfilePurple
                                    )
                                ) {
                                    Text("Accept", fontFamily = fredoka)
                                }

                                TextButton(
                                    onClick = {
                                        currentUid?.let { uid ->
                                            viewModel.declineFriendRequest(
                                                uid,
                                                requester.uid
                                            )
                                        }
                                    }
                                ) {
                                    Text("Decline", fontFamily = fredoka)
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White)
                    }

                    if (friendsList.isEmpty()) {
                        Text("No friends yet.", fontFamily = fredoka)
                    }
                }

                items(
                    items = friendsList,
                    key = { friend ->
                        friend.uid.ifBlank { friend.username }
                    }
                ) { friend ->
                    Surface(
                        color = ProfileCardPurple,
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 30.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.default_pfp),
                                    contentDescription = "Friend profile picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        friend.username,
                                        fontFamily = fredoka,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "About Me",
                                            fontFamily = fredoka,
                                            fontSize = 13.sp,
                                            color = Color(0xFF444444),
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    navController.navigate("ProfileScreen/${friend.uid}/${friend.username}")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProfilePurple
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Visit Profile", fontFamily = fredoka)
                            }
                        }
                    }
                }
            }

            ProfileTab.CHATS -> {
                item {
                    ChatBox(
                        friendUsernames = friendsList.map { it.username }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTabCard(
    label: String,
    imageRes: Int,
    selected: Boolean,
    font: FontFamily,
    modifier: Modifier = Modifier,
    ProfilePurple: Color = Color(0xFF5D36EB),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        color = ProfilePurple,
        border = BorderStroke(2.dp, if (selected) Color.White else ProfilePurple),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(
                vertical = 12.dp,
                horizontal = 8.dp
            )
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))

            Spacer(Modifier.height(6.dp))

            Text(
                text = label,
                fontFamily = font,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileSongCard(
    song: Song,
    description: String,
    font: FontFamily,
    ProfilePurple: Color = Color(0xFF5D36EB),
    ProfileCardPurple: Color = Color(0xFFBFB1FA),
    onDetails: () -> Unit
) {
    Surface(
        color = ProfileCardPurple,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 30.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(2.dp, Color.Black),
                    shadowElevation = 8.dp
                ) {
                    Image(
                        painter = painterResource(R.drawable.default_pfp),
                        contentDescription = "Song artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = song.name,
                        fontFamily = font,
                        color = Color.White,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (song.rating <= 0.0) {
                            "No ratings yet"
                        } else {
                            String.format(
                                Locale.US,
                                "★ %.1f / 5",
                                song.rating.coerceIn(0.0, 5.0)
                            )
                        },
                        fontFamily = font,
                        color = ProfilePurple,
                        fontSize = 14.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = description,
                            fontFamily = font,
                            fontSize = 13.sp,
                            color = Color(0xFF444444),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {},
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfilePurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("▶ Play", fontFamily = font)
                }

                Button(
                    onClick = onDetails,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfilePurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1.6f)
                ) {
                    Text(
                        "More details ☰",
                        fontFamily = font,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
