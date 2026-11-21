package com.example.csc490seniorproject.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.data.SongRepository
import com.example.csc490seniorproject.objects.User
import com.example.csc490seniorproject.viewmodels.FriendStatus
import com.example.csc490seniorproject.viewmodels.SearchScreenVM
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor

private enum class SongSortOption {
    NEWLY_ADDED,
    HIGHEST_RATED,
    MOST_PLAYED
}

@Composable
fun SearchScreen(
    viewModel: SearchScreenVM,
    navController: NavController
) {

    val scope = rememberCoroutineScope()
    val purple = Color(0xFF5D36EB)
    val cardPurple = Color(0xFFBFB1FA)
    val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))

    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedFilters by remember { mutableStateOf(setOf<String>()) }

    var appliedFilters by remember {
        mutableStateOf(setOf<String>())
    }

    var text by rememberSaveable { mutableStateOf("") }

    val activeFilters = appliedFilters

    val selectedGenres = activeFilters.filter {
        it in listOf("Rock", "Pop", "Jazz", "Hip-Hop", "Country")
    }

    var selectedSort by rememberSaveable {
        mutableStateOf(SongSortOption.NEWLY_ADDED)
    }

    val showSongs = "Song" in activeFilters ||
            ("Song" !in activeFilters && "Profile" !in activeFilters)

    val showProfiles = "Profile" in activeFilters ||
            ("Song" !in activeFilters && "Profile" !in activeFilters)

    val liveRatings = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Double>()
    }

    val visibleSongs = viewModel.songsList
        .filter { song ->
            val matchesText = text.isBlank() ||
                    song.name.contains(text.trim(), ignoreCase = true)

            val matchesGenre = selectedGenres.isEmpty() ||
                    selectedGenres.any { selectedGenre ->
                        song.genre.any { songGenre ->
                            songGenre.equals(
                                selectedGenre,
                                ignoreCase = true
                            )
                        }
                    }

            matchesText && matchesGenre
        }
        .let { songs ->
            when (selectedSort) {
                SongSortOption.NEWLY_ADDED ->
                    songs.sortedByDescending { song ->
                        song.createdAt?.time ?: 0L
                    }

                SongSortOption.HIGHEST_RATED ->
                    songs.sortedByDescending { song ->
                        liveRatings[song.id] ?: 0.0
                    }

                SongSortOption.MOST_PLAYED ->
                    songs
            }
        }

    val visibleUsers = viewModel.userResults.filter { user ->
        val matchesText = text.isBlank() ||
                user.username.contains(text.trim(), ignoreCase = true)

        val matchesGenre = selectedGenres.isEmpty() ||
                selectedGenres.any { selectedGenre ->
                    user.genre.any { userGenre ->
                        userGenre.equals(selectedGenre, ignoreCase = true)
                    }
                }

        showProfiles &&
                matchesText &&
                matchesGenre &&
                viewModel.friendStatuses[user.uid] != FriendStatus.FRIENDS
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = { query ->
                    text = query
                    viewModel.searchUsers(query)
                },
                label = { Text("Search songs, or users") },
                singleLine = true,
                modifier = Modifier.weight(1.6f)
            )

            Spacer(Modifier.width(12.dp))

            Button(onClick = {
                showFilterDialog = true
            },
                modifier = Modifier.weight(.5f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = purple),
                shape = RectangleShape
            ) {
                Text("Filter", fontFamily = fredoka)
            }
        }

        if(showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = {
                    Text("Filters", fontFamily = fredoka, color = Color(0xFF5D36EB))
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {

                        HorizontalDivider(
                            color = Color(0xFF5D36EB),
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 2.dp
                        )

                        Spacer(Modifier.height(12.dp))

                        // Filters
                        Text(
                            "Filters",
                            fontFamily = fredoka,
                            style = MaterialTheme.typography.titleMedium
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(listOf("Song", "Profile")) { option ->
                                FilterChip(
                                    selected = option in selectedFilters,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF5D36EB),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFFbfb1fa),
                                        labelColor = Color.White),
                                    onClick = {
                                        selectedFilters =
                                            if (option in selectedFilters)
                                                selectedFilters - option
                                            else
                                                selectedFilters + option
                                    },
                                    label = { Text(option) }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Genres",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = fredoka
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                listOf("Rock", "Pop", "Jazz", "Hip-Hop", "Country")
                            ) { genre ->
                                FilterChip(
                                    selected = genre in selectedFilters,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF5D36EB),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFFbfb1fa),
                                        labelColor = Color.White),
                                    onClick = {
                                        selectedFilters =
                                            if (genre in selectedFilters)
                                                selectedFilters - genre
                                            else
                                                selectedFilters + genre
                                    },
                                    label = { Text(genre) }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Sort By",
                            fontFamily = fredoka,
                            style = MaterialTheme.typography.titleMedium
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                listOf(
                                    SongSortOption.NEWLY_ADDED,
                                    SongSortOption.HIGHEST_RATED,
                                    SongSortOption.MOST_PLAYED
                                )
                            ) { option ->

                                val label = when (option) {
                                    SongSortOption.NEWLY_ADDED -> "Most Recent"
                                    SongSortOption.HIGHEST_RATED -> "Highest Rated"
                                    SongSortOption.MOST_PLAYED -> "Most Viewed"
                                }

                                FilterChip(
                                    selected = selectedSort == option,
                                    enabled = option != SongSortOption.MOST_PLAYED,
                                    onClick = {
                                        selectedSort = option
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = purple,
                                        selectedLabelColor = Color.White,
                                        containerColor = cardPurple,
                                        labelColor = Color.White,
                                        disabledContainerColor = cardPurple.copy(alpha = 0.5f),
                                        disabledLabelColor = Color.Gray
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            appliedFilters = selectedFilters
                            showFilterDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = purple)
                    ) {
                        Text("Apply", fontFamily = fredoka)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showFilterDialog = false }
                    ) {
                        Text("Cancel", fontFamily = fredoka)
                    }
                }
            )
        }

        viewModel.songsError?.let { error ->
            Text(
                text = "Couldn't load songs: $error",
                color = Color.Red,
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Users — only appears once there's a query with matches, disappears otherwise.
            if (visibleUsers.isNotEmpty()) {
                item {
                    Text(
                        "Users",
                        fontFamily = fredoka,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray
                    )
                }

                items(
                    items = visibleUsers,
                    key = { "user_${it.uid}" }
                ) { user ->
                    UserResultCard(
                        user = user,
                        status = viewModel.friendStatuses[user.uid],
                        purple = purple,
                        cardPurple = cardPurple,
                        fredoka = fredoka,
                        onAddFriend = {
                            val myUid = FirebaseAuth.getInstance().currentUser?.uid
                            if (myUid != null) {
                                viewModel.sendFriendRequest(myUid, user.uid)
                            }
                        },
                        onDetails = {
                            navController.navigate("ProfileScreen/${user.uid}/${user.username}")
                        }
                    )
                }
            }

            if (visibleSongs.isNotEmpty()) {
                item {
                    Text(
                        "Songs",
                        fontFamily = fredoka,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray
                    )
                }

                items(
                    items = visibleSongs,
                    key = { "song_${it.id}" }
                ) { song ->

                    var liveRating by remember(song.id) {
                        mutableStateOf(song.rating.coerceIn(0.0, 5.0))
                    }

                    var ratingCount by remember(song.id) {
                        mutableIntStateOf(0)
                    }

                    DisposableEffect(song.id) {
                        val listener = FirebaseFirestore.getInstance()
                            .collection("songs")
                            .document(song.id)
                            .collection("ratings")
                            .addSnapshotListener { snapshot, error ->
                                if (error == null && snapshot != null) {
                                    val ratings = snapshot.documents.mapNotNull { doc ->
                                        (doc.get("rating") as? Number)
                                            ?.toInt()
                                            ?.takeIf { it in 1..5 }
                                    }

                                    ratingCount = ratings.size

                                    liveRating = if (ratings.isNotEmpty()) {
                                        ratings.average()
                                    } else {
                                        0.0
                                    }

                                    liveRatings[song.id] = liveRating
                                }
                            }

                        onDispose {
                            listener.remove()
                            liveRatings.remove(song.id)
                        }
                    }

                    Surface(
                        color = cardPurple,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shadowElevation = 8.dp,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(2.dp, Color.Black)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.default_pfp),
                                        contentScale = ContentScale.Crop,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {

                                    Text(
                                        text = song.name,
                                        fontFamily = fredoka,
                                        fontSize = 18.sp,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val stars = floor(liveRating + 0.5)
                                        .toInt()
                                        .coerceIn(0, 5)

                                    Text(
                                        text = if (ratingCount == 0) {
                                            "No ratings yet"
                                        } else {
                                            "★".repeat(stars) +
                                                    "☆".repeat(5 - stars) +
                                                    "  " + String.format(
                                                Locale.US,
                                                "%.1f/5.0",
                                                liveRating
                                            )
                                        },
                                        fontFamily = fredoka,
                                        color = purple
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        shadowElevation = 4.dp,
                                        color = Color.White,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Description",
                                            fontFamily = fredoka,
                                            fontSize = 13.sp,
                                            color = Color(0xFF444444),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                SongRepository.recordSongView(song.id)
                                            } catch (e: Exception) {
                                                android.util.Log.e(
                                                    "SongViews",
                                                    "Failed to record song view",
                                                    e
                                                )
                                            }
                                        }

                                        navController.navigate("GameScreen/${song.id}")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = purple
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "▶ Play",
                                        fontFamily = fredoka,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Button(
                                    onClick = {
                                        navController.navigate("SongScreen/${android.net.Uri.encode(song.id)}")                                },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = purple
                                    ),
                                    modifier = Modifier.weight(1.6f)
                                ) {
                                    Text(
                                        "More details ☰",
                                        fontFamily = fredoka,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (
                text.isNotBlank() &&
                visibleUsers.isEmpty() &&
                visibleSongs.isEmpty() &&
                viewModel.songsError == null
            ) {
                item {
                    Text(
                        text = "No results.",
                        color = Color.DarkGray,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserResultCard(
    user: User,
    status: FriendStatus?,
    purple: Color,
    cardPurple: Color,
    fredoka: FontFamily,
    onAddFriend: () -> Unit,
    onDetails: () -> Unit
) {
    Surface(
        color = cardPurple,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(2.dp, Color.Black)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.default_pfp),
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = user.username,
                    fontFamily = fredoka,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (status) {
                        FriendStatus.FRIENDS -> {
                            OutlinedButton(onClick = {}, enabled = false) {
                                Text("✓ Friends")
                            }
                        }

                        FriendStatus.REQUEST_SENT -> {
                            OutlinedButton(onClick = {}, enabled = false) {
                                Text("Request Sent")
                            }
                        }

                        FriendStatus.REQUEST_RECEIVED -> {
                            OutlinedButton(onClick = {}, enabled = false) {
                                Text("Respond on Friends tab")
                            }
                        }

                        FriendStatus.NONE -> {
                            IconButton(
                                onClick = onAddFriend,
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.add_friend_2),
                                    contentDescription = "Add friend",
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }

                        null -> {
                            // Status hasn't loaded yet — avoid flashing the wrong button.
                        }
                    }

                    Button(
                        onClick = onDetails,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = purple
                        ),
                        modifier = Modifier.weight(1.6f)
                    ) {
                        Text(
                            "More details ☰",
                            fontFamily = fredoka,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}