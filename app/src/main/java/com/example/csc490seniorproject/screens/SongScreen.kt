package com.example.csc490seniorproject.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.viewmodels.SongScreenVM
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Locale
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SongScreen(viewModel: SongScreenVM, songId: String = "", navController: NavController) {

    var songName by remember(songId) {
        mutableStateOf("Loading…")
    }
    var songRating by remember(songId) {
        mutableStateOf(0.0)
    }
    var songDescription by remember(songId) {
        mutableStateOf("No description provided.")
    }
    var songLoadError by remember(songId) {
        mutableStateOf<String?>(null)
    }
    var openPanel by remember(songId) {
        mutableStateOf<String?>(null)
    }

    var ratingCount by remember(songId) {
        mutableIntStateOf(0)
    }
    var myRating by remember(songId) {
        mutableIntStateOf(0)
    }
    var selectedRating by remember(songId) {
        mutableIntStateOf(0)
    }
    var showRatingDialog by remember(songId) {
        mutableStateOf(false)
    }
    var savingRating by remember(songId) {
        mutableStateOf(false)
    }
    var ratingError by remember(songId) {
        mutableStateOf<String?>(null)
    }

    var ownerId by remember(songId) {
        mutableStateOf("")
    }
    var showDescriptionEditor by remember(songId) {
        mutableStateOf(false)
    }
    var descriptionDraft by remember(songId) {
        mutableStateOf("")
    }
    var savingDescription by remember(songId) {
        mutableStateOf(false)
    }
    var descriptionError by remember(songId) {
        mutableStateOf<String?>(null)
    }

    var songTags by remember(songId) {
        mutableStateOf<List<String>>(emptyList())
    }

    var showSongTagEditor by remember(songId) {
        mutableStateOf(false)
    }

    var selectedSongTags by remember(songId) {
        mutableStateOf(setOf<String>())
    }

    var savingSongTags by remember(songId) {
        mutableStateOf(false)
    }

    var songTagError by remember(songId) {
        mutableStateOf<String?>(null)
    }

    var creatorUsername by remember(songId) {
        mutableStateOf("Loading creator…")
    }

    var creatorAboutMe by remember(songId) {
        mutableStateOf("No description provided.")
    }

    var creatorProfilePictureUrl by remember(songId) {
        mutableStateOf("")
    }

    var creatorProfileLoading by remember(songId) {
        mutableStateOf(true)
    }

    val auth = remember { FirebaseAuth.getInstance() }
    var currentUid by remember {
        mutableStateOf(auth.currentUser?.uid)
    }

    val fredoka = FontFamily(
        Font(R.font.fredoka_medium, FontWeight.Normal)
    )
    val purple = Color(0xFF5D36EB)

    val musicGenres = listOf(
        "Rock",
        "Pop",
        "Jazz",
        "Hip-Hop",
        "Country"
    )

    openPanel?.let { panel ->
        SongFeaturePopup(
            songId = songId,
            leaderboard = panel == "Leaderboard",
            onClose = { openPanel = null }
        )
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUid = it.currentUser?.uid
            showDescriptionEditor = false
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val canEditDescription =
        songId.isNotBlank() &&
                ownerId.isNotBlank() &&
                currentUid == ownerId &&
                songLoadError == null

    val canEditSongTags =
        songId.isNotBlank() &&
                ownerId.isNotBlank() &&
                currentUid == ownerId &&
                songLoadError == null

    DisposableEffect(songId) {
        var active = true

        val listener = if (songId.isNotBlank()) {
            FirebaseFirestore.getInstance()
                .collection("songs")
                .document(songId)
                .addSnapshotListener { document, error ->
                    if (active) {
                        when {
                            error != null -> {
                                songLoadError =
                                    "Unable to load song details."
                                songName = "Unable to load song"
                            }

                            document == null || !document.exists() -> {
                                songLoadError = "Song not found."
                                songName = "Song not found"
                            }

                            else -> {
                                ownerId = document
                                    .getString("ownerId")
                                    .orEmpty()

                                songLoadError = null
                                songName = document.getString("name")
                                    ?: "Untitled Song"

                                songRating = (
                                        document.get("rating") as? Number
                                        )?.toDouble()?.coerceIn(0.0, 5.0) ?: 0.0

                                ratingCount = (
                                        document.get("ratingCount") as? Number
                                        )?.toInt() ?: 0

                                songDescription = document
                                    .getString("description")
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "No description provided."

                                songTags = document
                                    .get("tags")
                                    ?.let { it as? List<*> }
                                    .orEmpty()
                                    .filterIsInstance<String>()
                                    .filter { it in musicGenres }
                            }
                        }
                    }
                }
        } else {
            songName = "No song selected"
            null
        }

        onDispose {
            active = false
            listener?.remove()
        }
    }

    DisposableEffect(ownerId) {
        var active = true

        creatorUsername = "Loading creator…"
        creatorAboutMe = "No description provided."
        creatorProfilePictureUrl = ""
        creatorProfileLoading = ownerId.isNotBlank()

        val listener = if (ownerId.isNotBlank()) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(ownerId)
                .addSnapshotListener { document, error ->
                    if (active) {
                        creatorProfileLoading = false

                        if (error == null && document != null &&
                            document.exists()
                        ) {
                            creatorUsername = document
                                .getString("username")
                                ?.takeIf { it.isNotBlank() }
                                ?: "Unknown user"

                            creatorAboutMe = document
                                .getString("aboutMe")
                                ?.takeIf { it.isNotBlank() }
                                ?: "No description provided."

                            creatorProfilePictureUrl = document
                                .getString("profilePictureUrl")
                                .orEmpty()
                        } else {
                            creatorUsername = "Unknown user"
                            creatorAboutMe = "Unable to load creator details."
                            creatorProfilePictureUrl = ""
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

    DisposableEffect(songId) {
        var active = true

        val listener = if (songId.isNotBlank()) {
            FirebaseFirestore.getInstance()
                .collection("songs")
                .document(songId)
                .collection("ratings")
                .addSnapshotListener { snapshot, error ->
                    if (active && error == null && snapshot != null) {
                        val ratings = snapshot.documents.mapNotNull { doc ->
                            doc.getLong("rating")
                                ?.toInt()
                                ?.takeIf { it in 1..5 }
                        }

                        ratingCount = ratings.size
                        songRating = if (ratings.isNotEmpty()) {
                            ratings.average()
                        } else {
                            0.0
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

    // Load the current user's existing rating.
    DisposableEffect(songId, currentUid) {
        var active = true
        myRating = 0

        val uid = currentUid
        val listener = if (songId.isNotBlank() && uid != null) {
            FirebaseFirestore.getInstance()
                .collection("songs")
                .document(songId)
                .collection("ratings")
                .document(uid)
                .addSnapshotListener { document, error ->
                    if (active && error == null) {
                        myRating = document
                            ?.getLong("rating")
                            ?.toInt()
                            ?.takeIf { it in 1..5 }
                            ?: 0
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

    if (showRatingDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!savingRating) showRatingDialog = false
            },
            title = {
                Text(
                    text = "Rate this song",
                    color = purple,
                    fontFamily = fredoka
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        color = purple,
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 2.dp
                    )

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            TextButton(
                                enabled = !savingRating,
                                onClick = {
                                    selectedRating = star
                                    ratingError = null
                                },
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text(
                                    text = if (star <= selectedRating) {
                                        "★"
                                    } else {
                                        "☆"
                                    },
                                    color = if (star <= selectedRating) {
                                        purple
                                    } else {
                                        Color.Gray
                                    },
                                    fontSize = 32.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = if (selectedRating == 0) {
                            "Select 1 to 5 stars"
                        } else {
                            "$selectedRating out of 5 stars"
                        }
                    )

                    ratingError?.let {
                        Text(
                            text = it,
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedRating in 1..5 && !savingRating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purple
                    ),
                    onClick = {
                        if (auth.currentUser == null) {
                            ratingError = "Please log in first."
                        } else {
                            savingRating = true
                            ratingError = null

                            viewModel.saveSongRating(
                                songId = songId,
                                ratingValue = selectedRating,
                                onSuccess = {
                                    savingRating = false
                                    showRatingDialog = false
                                },
                                onError = {
                                    savingRating = false
                                    ratingError = it
                                }
                            )
                        }
                    }
                ) {
                    Text(
                        if (savingRating) "Saving..." else "Submit rating"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !savingRating,
                    onClick = { showRatingDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }


    if (showSongTagEditor && canEditSongTags) {
        AlertDialog(
            onDismissRequest = {
                if (!savingSongTags) {
                    showSongTagEditor = false
                }
            },
            title = {
                Text(
                    text = "Edit Song Tags",
                    color = purple,
                    fontFamily = fredoka
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Select all genres that describe this song.")

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(musicGenres) { genre ->
                            FilterChip(
                                selected = genre in selectedSongTags,
                                onClick = {
                                    selectedSongTags =
                                        if (genre in selectedSongTags) {
                                            selectedSongTags - genre
                                        } else {
                                            selectedSongTags + genre
                                        }

                                    songTagError = null
                                },
                                label = {
                                    Text(
                                        text = genre,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        fontFamily = fredoka
                                    )
                                },
                                enabled = !savingSongTags,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF5D36EB),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFbfb1fa),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    songTagError?.let { error ->
                        Text(
                            text = error,
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !savingSongTags,
                    onClick = {
                        if (auth.currentUser?.uid != ownerId) {
                            songTagError =
                                "Only the song owner can edit these tags."
                        } else {
                            savingSongTags = true
                            songTagError = null

                            FirebaseFirestore.getInstance()
                                .collection("songs")
                                .document(songId)
                                .update(
                                    "tags",
                                    musicGenres.filter {
                                        it in selectedSongTags
                                    }
                                )
                                .addOnSuccessListener {
                                    savingSongTags = false
                                    showSongTagEditor = false
                                }
                                .addOnFailureListener { exception ->
                                    savingSongTags = false
                                    songTagError =
                                        exception.localizedMessage
                                            ?: "Unable to save song tags."
                                }
                        }
                    }
                ) {
                    Text(
                        if (savingSongTags) "Saving..." else "Save"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !savingSongTags,
                    onClick = {
                        showSongTagEditor = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }


    if (showDescriptionEditor && canEditDescription) {
        AlertDialog(
            onDismissRequest = {
                if (!savingDescription) showDescriptionEditor = false
            },
            title = {
                Text("Edit description")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = descriptionDraft,
                        onValueChange = {
                            if (it.length <= 1000) {
                                descriptionDraft = it
                                descriptionError = null
                            }
                        },
                        label = { Text("Description") },
                        supportingText = {
                            Text("${descriptionDraft.length}/1000")
                        },
                        minLines = 3,
                        maxLines = 6,
                        enabled = !savingDescription,
                        modifier = Modifier.fillMaxWidth()
                    )

                    descriptionError?.let {
                        Text(it, color = Color.Red)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !savingDescription,
                    onClick = {
                        if (auth.currentUser?.uid != ownerId) {
                            descriptionError =
                                "Only the song owner can edit this."
                        } else {
                            savingDescription = true
                            descriptionError = null

                            FirebaseFirestore.getInstance()
                                .collection("songs")
                                .document(songId)
                                .update(
                                    "description",
                                    descriptionDraft.trim()
                                )
                                .addOnSuccessListener {
                                    savingDescription = false
                                    showDescriptionEditor = false
                                }
                                .addOnFailureListener {
                                    savingDescription = false
                                    descriptionError = it.localizedMessage
                                        ?: "Unable to save description."
                                }
                        }
                    }
                ) {
                    Text(
                        if (savingDescription) "Saving…" else "Save"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !savingDescription,
                    onClick = { showDescriptionEditor = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(2.5.dp))

        HorizontalDivider(
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
            thickness = 2.dp
        )

        Spacer(Modifier.height(7.5.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                shadowElevation = 30.dp,
                border = BorderStroke(2.dp, purple)
            ) {
                Image(
                    painter = painterResource(R.drawable.default_pfp),
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 120.dp)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ){
                Text(
                    text = songName,
                    fontFamily = fredoka,
                    fontSize = 24.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val filledStars = kotlin.math.floor(songRating + 0.5)
                        .toInt()
                        .coerceIn(0, 5)

                    repeat(5) { index ->
                        Text(
                            text = if (index < filledStars) "★" else "☆",
                            color = if (index < filledStars) {
                                Color(0xFFFFB300)
                            } else {
                                Color.LightGray
                            },
                            fontSize = 18.sp
                        )
                    }

                    Text(
                        text = if (ratingCount > 0) {
                            String.format(
                                Locale.US,
                                "%.1f/5.0",
                                songRating
                            )
                        } else {
                            "0.0/5.0"
                        },
                        fontFamily = fredoka,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Text(
                        text = if (ratingCount > 0) {
                            "($ratingCount ratings)"
                        } else {
                            "(No ratings)"
                        },
                        fontFamily = fredoka,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (songTags.isEmpty()) {
                        Text(
                            text = "No song tags yet",
                            fontFamily = fredoka,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            songTags.chunked(3).forEach { rowTags ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowTags.forEach { tag ->
                                        Surface(
                                            color = Color(0xFFBFB1FA),
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

                    if (canEditSongTags) {
                        TextButton(
                            onClick = {
                                selectedSongTags = songTags.toSet()
                                songTagError = null
                                showSongTagEditor = true
                            },
                            contentPadding = PaddingValues(
                                horizontal = 4.dp,
                                vertical = 0.dp
                            )
                        ) {
                            Text(
                                text = "Edit",
                                fontSize = 12.sp,
                                color = purple
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (songId.isNotBlank() && songLoadError == null) {
                        navController.navigate("GameScreen/${Uri.encode(songId)}")
                    }
                },
                enabled = songId.isNotBlank() && songLoadError == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = purple
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "▶ Play",
                    fontFamily = fredoka,
                    color = Color.White,
                    fontSize = 16.sp
                )
            }

            Button(
                onClick = {
                    if (currentUid == null) {
                        ratingError = "Please log in to rate this song."
                    } else {
                        selectedRating = myRating
                        ratingError = null
                        showRatingDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = purple
                ),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 4.dp
                ),modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (myRating > 0) "★ Change rating" else "★ Rate",
                    fontFamily = fredoka,
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Description",
                fontFamily = fredoka,
                fontSize = 24.sp
            )

            if (canEditDescription) {
                TextButton(
                    onClick = {
                        descriptionDraft =
                            if (songDescription == "No description provided.") {
                                ""
                            } else {
                                songDescription
                            }

                        descriptionError = null
                        showDescriptionEditor = true
                    }
                ) {
                    Text("Edit", color = purple)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp,
            border = BorderStroke(2.dp, purple),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = songLoadError ?: songDescription,
                fontFamily = fredoka,
                fontSize = 13.sp,
                color = Color(0xFF444444),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp,
                color = purple,
                modifier = Modifier.weight(1f),
                border = BorderStroke(2.dp, purple),
                onClick = { openPanel = "Comments" }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(
                        vertical = 12.dp,
                        horizontal = 8.dp
                    )
                ) {
                    Image(
                        painter = painterResource(R.drawable.comment),
                        contentScale = ContentScale.Crop,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Comments",
                        fontFamily = fredoka,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp,
                color = purple,
                modifier = Modifier.weight(1f),
                border = BorderStroke(2.dp, purple),
                onClick = { openPanel = "Leaderboard" }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(
                        vertical = 12.dp,
                        horizontal = 8.dp
                    )
                ) {
                    Image(
                        painter = painterResource(R.drawable.leaderboard),
                        contentScale = ContentScale.Crop,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Leaderboard",
                        fontFamily = fredoka,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Creator",
            fontFamily = fredoka,
            fontSize = 24.sp
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 4.dp,
            border = BorderStroke(2.dp, purple),
            color = Color(0xFFF7F3FF),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        modifier = Modifier.size(76.dp)
                    ) {
                        if (creatorProfilePictureUrl.isNotBlank()) {
                            AsyncImage(
                                model = creatorProfilePictureUrl,
                                contentDescription = "Creator profile picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.default_pfp),
                                contentDescription = "Creator profile picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (creatorProfileLoading) {
                                "Loading creator…"
                            } else {
                                creatorUsername
                            },
                            fontFamily = fredoka,
                            fontSize = 18.sp,
                            color = purple,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "Song owner",
                            fontFamily = fredoka,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Text(
                    text = "About Me",
                    fontFamily = fredoka,
                    fontSize = 16.sp,
                    color = purple
                )

                Text(
                    text = creatorAboutMe,
                    fontFamily = fredoka,
                    fontSize = 13.sp,
                    color = Color(0xFF444444)
                )

                Button(
                    onClick = {
                        if (ownerId.isNotBlank() && creatorUsername != "Unknown user") {
                            navController.navigate(
                                "ProfileScreen/${Uri.encode(ownerId)}/${Uri.encode(creatorUsername)}"
                            )
                        }
                    },
                    enabled = ownerId.isNotBlank() &&
                            !creatorProfileLoading &&
                            creatorUsername != "Unknown user",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = purple
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Visit Profile",
                        fontFamily = fredoka,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

private data class SongComment(
    val id: String,
    val userId: String,
    val text: String,
    val hidden: Boolean = false,
    val likedBy: List<String> = emptyList()
)

private data class LeaderboardMap(
    val id: String,
    val title: String
)

private data class RankedScore(
    val userId: String,
    val username: String,
    val score: Long,
    val stars: Int
)

@Composable
private fun SongFeaturePopup(
    songId: String,
    leaderboard: Boolean,
    onClose: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val purple = Color(0xFF5D36EB)

    var currentUid by remember {
        mutableStateOf(auth.currentUser?.uid)
    }
    var songOwnerId by remember(songId, currentUid) {
        mutableStateOf("")
    }

    var maps by remember(songId, currentUid) {
        mutableStateOf<List<LeaderboardMap>>(emptyList())
    }
    var selectedMap by remember(songId, currentUid) {
        mutableStateOf("")
    }
    var comments by remember(songId, currentUid) {
        mutableStateOf<List<SongComment>>(emptyList())
    }
    var scores by remember(songId, currentUid) {
        mutableStateOf<List<RankedScore>>(emptyList())
    }
    val names = remember(songId, currentUid) {
        mutableStateMapOf<String, String>()
    }

    var error by remember(songId, currentUid) {
        mutableStateOf<String?>(null)
    }
    var mapError by remember(songId, currentUid) {
        mutableStateOf<String?>(null)
    }
    var loading by remember(songId, currentUid) {
        mutableStateOf(false)
    }
    var mapsLoading by remember(songId, currentUid) {
        mutableStateOf(leaderboard)
    }
    var draft by remember(songId, currentUid) {
        mutableStateOf("")
    }
    var sending by remember(songId, currentUid) {
        mutableStateOf(false)
    }
    var postError by remember(songId, currentUid) {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUid = it.currentUser?.uid
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    DisposableEffect(songId, currentUid) {
        var active = true
        songOwnerId = ""

        val listener = if (songId.isNotBlank() && currentUid != null) {
            db.collection("songs")
                .document(songId)
                .addSnapshotListener { document, exception ->
                    if (active) {
                        songOwnerId = if (exception == null) {
                            document?.getString("ownerId").orEmpty()
                        } else {
                            ""
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

    val isSongOwner =
        currentUid != null && currentUid == songOwnerId

    DisposableEffect(songId, leaderboard, currentUid) {
        var active = true
        maps = emptyList()
        mapError = null

        mapsLoading =
            leaderboard && songId.isNotBlank() && currentUid != null

        val listener = if (
            leaderboard && songId.isNotBlank() && currentUid != null
        ) {
            db.collection("beatmaps")
                .whereEqualTo("songId", songId)
                .addSnapshotListener { snapshot, exception ->
                    if (active) {
                        mapsLoading = false
                        mapError = exception?.localizedMessage

                        if (exception == null) {
                            maps = snapshot?.documents.orEmpty().map { doc ->
                                LeaderboardMap(
                                    id = doc.id,
                                    title = doc.getString("title") ?: "Beatmap"
                                )
                            }

                            if (maps.none { it.id == selectedMap }) {
                                selectedMap =
                                    maps.firstOrNull()?.id.orEmpty()
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

    DisposableEffect(songId, leaderboard, selectedMap, currentUid) {
        var active = true
        comments = emptyList()
        scores = emptyList()
        error = null

        fun loadUsername(uid: String) {
            if (uid.isBlank() || uid in names) return

            names[uid] = "User"

            db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { user ->
                    if (active) {
                        names[uid] = user.getString("username")
                            ?.takeIf { it.isNotBlank() }
                            ?: "User"
                    }
                }
        }

        val query: Query? = when {
            songId.isBlank() || currentUid == null -> null

            !leaderboard -> db.collection("songs")
                .document(songId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)

            selectedMap.isNotBlank() -> db.collection("beatmaps")
                .document(selectedMap)
                .collection("scores")
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(50)

            else -> null
        }

        loading = query != null

        val listener = query?.addSnapshotListener { snapshot, exception ->
            if (active) {
                loading = false
                error = exception?.localizedMessage

                if (exception == null) {
                    try {
                        if (leaderboard) {
                            scores = snapshot?.documents.orEmpty().map { doc ->
                                RankedScore(
                                    userId = doc.id,
                                    username = doc.getString("username")
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "User",
                                    score = doc.getLong("score") ?: 0L,
                                    stars = (doc.getLong("stars") ?: 0L).toInt().coerceIn(0, 5)
                                )
                            }
                        } else {
                            comments =
                                snapshot?.documents.orEmpty().map { doc ->
                                    val uid =
                                        doc.getString("userId").orEmpty()

                                    loadUsername(uid)

                                    SongComment(
                                        id = doc.id,
                                        userId = uid,
                                        text = doc.getString("text").orEmpty(),
                                        hidden =
                                            doc.getBoolean("hidden") ?: false,
                                        likedBy =
                                            (doc.get("likedBy") as? List<*>)
                                                .orEmpty()
                                                .filterIsInstance<String>()
                                    )
                                }
                        }
                    } catch (e: Exception) {
                        error = e.localizedMessage
                            ?: "Unable to load entries."
                    }
                }
            }
        }

        onDispose {
            active = false
            listener?.remove()
        }
    }

    val visibleComments = comments.filter {
        !it.hidden || isSongOwner
    }

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF7F3FF),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (leaderboard) "Leaderboard" else "Comments",
                        color = purple,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = onClose) {
                        Text("Close")
                    }
                }

                if (songId.isBlank()) {
                    Text("Open a song through Search → More details.")
                } else if (currentUid == null) {
                    Text("Please log in to view this section.")
                }

                (mapError ?: error)?.let {
                    Text(it, color = Color.Red)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (leaderboard) {
                        items(maps, key = { it.id }) { map ->
                            OutlinedButton(
                                onClick = { selectedMap = map.id },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (selectedMap == map.id) {
                                        "✓ ${map.title}"
                                    } else {
                                        map.title
                                    }
                                )
                            }
                        }
                    }

                    if (loading || mapsLoading) {
                        item {
                            Text("Loading…")
                        }
                    } else if (
                        error == null &&
                        mapError == null &&
                        songId.isNotBlank() &&
                        currentUid != null
                    ) {
                        if (!leaderboard && visibleComments.isEmpty()) {
                            item {
                                Text("No comments yet.")
                            }
                        } else if (leaderboard && maps.isEmpty()) {
                            item {
                                Text("No beatmaps for this song yet.")
                            }
                        } else if (leaderboard && scores.isEmpty()) {
                            item {
                                Text("No scores yet.")
                            }
                        }
                    }

                    if (leaderboard) {
                        items(
                            count = scores.size,
                            key = { index -> scores[index].userId }
                        ) { index ->
                            val entry = scores[index]
                            val isMe = entry.userId == currentUid

                            val rankColor = when (index) {
                                0 -> Color(0xFFFFB300)   // gold
                                1 -> Color(0xFF9E9E9E)   // silver
                                2 -> Color(0xFFB87333)   // bronze
                                else -> Color.White
                            }

                            Surface(
                                color = if (isMe) Color(0xFFD6C8FF) else Color(0xFFE9E2FF),
                                shape = RoundedCornerShape(12.dp),
                                border = if (isMe) BorderStroke(2.dp, purple) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        color = rankColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        modifier = Modifier.widthIn(min = 36.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.username,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "★".repeat(entry.stars) + "☆".repeat(5 - entry.stars),
                                            color = purple,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Text(
                                        text = "${entry.score}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            visibleComments,
                            key = { it.id }
                        ) { comment ->
                            ManagedCommentCard(
                                songId = songId,
                                comment = comment,
                                username = names[comment.userId] ?: "User",
                                currentUid = currentUid,
                                isSongOwner = isSongOwner
                            )
                        }
                    }
                }

                if (!leaderboard && songId.isNotBlank()) {
                    postError?.let {
                        Text(it, color = Color.Red)
                    }

                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            if (it.length <= 1000) draft = it
                        },
                        label = { Text("Write a comment") },
                        enabled = !sending && currentUid != null,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = draft.isNotBlank() &&
                                !sending &&
                                currentUid != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = purple
                        ),
                        onClick = {
                            val uid = auth.currentUser?.uid
                            val message = draft.trim()

                            if (uid == null) {
                                postError = "Please log in first."
                            } else if (message.isNotEmpty()) {
                                sending = true
                                postError = null

                                db.collection("songs")
                                    .document(songId)
                                    .collection("comments")
                                    .add(
                                        mapOf(
                                            "userId" to uid,
                                            "text" to message,
                                            "createdAt" to
                                                    FieldValue.serverTimestamp()
                                        )
                                    )
                                    .addOnSuccessListener {
                                        draft = ""
                                        sending = false
                                    }
                                    .addOnFailureListener {
                                        postError = it.localizedMessage
                                            ?: "Unable to post comment."
                                        sending = false
                                    }
                            }
                        }
                    ) {
                        Text(
                            if (sending) "Posting…" else "Post comment"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagedCommentCard(
    songId: String,
    comment: SongComment,
    username: String,
    currentUid: String?,
    isSongOwner: Boolean
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val purple = Color(0xFF5D36EB)

    var menuOpen by remember(comment.id, currentUid) {
        mutableStateOf(false)
    }
    var confirmDelete by remember(comment.id, currentUid) {
        mutableStateOf(false)
    }
    var busy by remember(comment.id, currentUid) {
        mutableStateOf(false)
    }
    var actionError by remember(comment.id, currentUid) {
        mutableStateOf<String?>(null)
    }

    val isCommentAuthor =
        currentUid != null && comment.userId == currentUid

    val liked =
        currentUid != null && currentUid in comment.likedBy

    val commentRef = remember(songId, comment.id) {
        db.collection("songs")
            .document(songId)
            .collection("comments")
            .document(comment.id)
    }

    Surface(
        color = if (comment.hidden) {
            Color(0xFFF0ECF5)
        } else {
            Color(0xFFE9E2FF)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 4.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = username,
                    color = purple,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Like button: immediately LEFT of the three dots.
                TextButton(
                    enabled = currentUid != null &&
                            !busy &&
                            !comment.hidden,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .defaultMinSize(minWidth = 1.dp)
                        .semantics {
                            contentDescription = if (liked) {
                                "Unlike comment"
                            } else {
                                "Like comment"
                            }
                        },
                    onClick = {
                        val uid = currentUid

                        if (uid != null) {
                            busy = true
                            actionError = null

                            val change = if (liked) {
                                FieldValue.arrayRemove(uid)
                            } else {
                                FieldValue.arrayUnion(uid)
                            }

                            commentRef.update("likedBy", change)
                                .addOnSuccessListener {
                                    busy = false
                                }
                                .addOnFailureListener {
                                    busy = false
                                    actionError = it.localizedMessage
                                        ?: "Unable to update like."
                                }
                        }
                    }
                ) {
                    Text(
                        text = if (liked) "♥" else "♡",
                        color = if (liked) {
                            Color(0xFFE54878)
                        } else {
                            purple
                        },
                        fontSize = 20.sp
                    )

                    Spacer(Modifier.width(4.dp))

                    Text(
                        text = "${comment.likedBy.size}",
                        color = purple,
                        fontSize = 12.sp
                    )
                }

                // Three-dot menu.
                if (isCommentAuthor || isSongOwner) {
                    Box {
                        IconButton(
                            enabled = !busy,
                            onClick = { menuOpen = true },
                            modifier = Modifier
                                .size(32.dp)
                                .semantics {
                                    contentDescription = "Comment options"
                                }
                        ) {
                            Text(
                                text = "⋮",
                                color = purple,
                                fontSize = 22.sp
                            )
                        }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            if (isCommentAuthor) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Delete",
                                            color = Color(0xFFB3261E)
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        actionError = null
                                        confirmDelete = true
                                    }
                                )
                            }

                            if (isSongOwner) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (comment.hidden) {
                                                "Unhide comment"
                                            } else {
                                                "Hide comment"
                                            }
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        busy = true
                                        actionError = null

                                        commentRef.update(
                                            "hidden",
                                            !comment.hidden
                                        )
                                            .addOnSuccessListener {
                                                busy = false
                                            }
                                            .addOnFailureListener {
                                                busy = false
                                                actionError =
                                                    it.localizedMessage
                                                        ?: "Unable to update comment."
                                            }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (comment.hidden) {
                Text(
                    text = "Hidden from the comments list",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Text(
                text = comment.text,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            if (busy) {
                Text(
                    text = "Saving…",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            actionError?.let {
                Text(
                    text = it,
                    color = Color(0xFFB3261E),
                    fontSize = 12.sp
                )
            }
        }
    }

    if (confirmDelete && isCommentAuthor) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) confirmDelete = false
            },
            title = {
                Text("Delete comment?")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("This permanently removes your comment.")

                    actionError?.let {
                        Text(
                            text = it,
                            color = Color(0xFFB3261E)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        actionError = null

                        commentRef.delete()
                            .addOnSuccessListener {
                                busy = false
                                confirmDelete = false
                            }
                            .addOnFailureListener {
                                busy = false
                                actionError = it.localizedMessage
                                    ?: "Unable to delete comment."
                            }
                    }
                ) {
                    Text(
                        text = if (busy) "Deleting…" else "Delete",
                        color = Color(0xFFB3261E)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmDelete = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}