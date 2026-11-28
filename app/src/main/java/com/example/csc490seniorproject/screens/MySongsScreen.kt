package com.example.csc490seniorproject.screens

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.viewmodels.CollaborationViewModel
import com.example.csc490seniorproject.viewmodels.MySongsVM
import com.example.csc490seniorproject.viewmodels.SearchScreenVM
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MySongsScreen(navController: NavController, viewModel: MySongsVM = viewModel()) {
    val songs by viewModel.songs.collectAsState()
    val error by viewModel.error.collectAsState()
    val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))
    var templates by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Song?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }   // reloads when you come back from the editor

    JoinSessionDialog(
        navController = navController,
        showDialog = showJoinDialog,
        onDismiss = {
            showJoinDialog = false
        }
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(5.dp)
        ) {

            Text(
                "My Songs", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                fontFamily = fredoka, fontSize = 28.sp, color = Color(0xFF5d36eb)
            )

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            if (songs.isEmpty()) {
                Text(
                    "No songs yet. Tap + to create one.",
                    fontFamily = fredoka,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)   // keeps the last song clear of the FAB
            ) {
                items(songs, key = { it.id }) { currentItem ->
                    var expanded by remember { mutableStateOf(false) }

                    Surface(
                        color = Color(0xFFbfb1fa),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 30.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.Top,
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
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = currentItem.name.ifBlank { "Untitled Song" },
                                            fontFamily = fredoka,
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        val (statusText, statusColor) = when {
                                            currentItem.published ->
                                                "Public" to Color(0xFF2E9B50)

                                            currentItem.friendsOnly ->
                                                "Friends Only" to Color(0xFF5D36EB)

                                            else ->
                                                "Private" to Color(0xFFE53935)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = statusColor
                                        ) {
                                            Text(
                                                text = statusText,
                                                fontFamily = fredoka,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.White,
                                                modifier = Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 5.dp
                                                )
                                            )
                                        }

                                        Box {
                                            IconButton(onClick = { expanded = true }) {
                                                Icon(
                                                    Icons.Default.MoreVert,
                                                    contentDescription = "Show menu"
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit Song Details") },
                                                    onClick = {
                                                        expanded = false
                                                        editing = currentItem
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            if (currentItem.published)
                                                                "Unpublish Song"
                                                            else
                                                                "Publish Song"
                                                        )
                                                    },
                                                    onClick = {
                                                        expanded = false
                                                        viewModel.togglePublish(currentItem)
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            if (currentItem.friendsOnly)
                                                                "Remove Friends-Only"
                                                            else
                                                                "Make Friends-Only"
                                                        )
                                                    },
                                                    enabled = !currentItem.published,
                                                    onClick = {
                                                        expanded = false
                                                        viewModel.toggleFriendsOnly(currentItem)
                                                    }
                                                )

                                                DropdownMenuItem(
                                                    text = { Text("Delete Song") },
                                                    onClick = {
                                                        expanded = false
                                                        viewModel.delete(currentItem.id)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        shadowElevation = 4.dp,
                                        color = Color.White,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = currentItem.description.ifBlank {
                                                "No description provided."
                                            },
                                            fontFamily = fredoka,
                                            fontSize = 13.sp,
                                            color = Color(0xFF444444),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val buttonColors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5d36eb),
                                contentColor = Color.White
                            )

                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            navController.navigate("GameScreen/${currentItem.id}")
                                        },
                                        colors = buttonColors,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("▶ Play", fontFamily = fredoka)
                                    }

                                    Button(
                                        onClick = {
                                            navController.navigate("EditorScreen/${currentItem.id}")
                                        },
                                        colors = buttonColors,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Edit Song", fontFamily = fredoka)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            navController.navigate(
                                                "BeatmapEditorScreen/${currentItem.id}"
                                            )
                                        },
                                        colors = buttonColors,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Edit Beatmap", fontFamily = fredoka)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Join Collaboration Session FAB
            FloatingActionButton(
                onClick = {
                    // The JoinSessionButton handles the dialog and session logic.
                    showJoinDialog = true
                },
                containerColor = Color(0xFF5d36eb),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Join collaboration session"
                )
            }

            FloatingActionButton(
                onClick = {
                    viewModel.getTemplates { list ->
                        templates = list
                    }
                    showCreate = true
                },
                containerColor = Color(0xFF5d36eb),
                contentColor = Color.White,
                modifier = Modifier.padding(16.dp)
            ) { Icon(Icons.Default.Add, contentDescription = "New song") }
        }
    }

    if (showCreate) {
        SongDetailsDialog(
            title = "New Song",
            initialName = "",
            initialGenres = emptyList(),
            initialDescription = "",
            confirmLabel = "Create",
            templates = templates,
            onTemplateClick = { templateId ->
                showCreate = false
                viewModel.createSongFromTemplate(templateId) { id ->
                    navController.navigate("EditorScreen/$id")
                }
            },
            onDismiss = { showCreate = false },
            onConfirm = { name, tags, description ->
                showCreate = false

                viewModel.createSong(
                    name,
                    tags,
                    description
                ) { id ->
                    navController.navigate("EditorScreen/$id")
                }
            }
        )
    }

    editing?.let { song ->
        SongDetailsDialog(
            title = "Song Details",
            initialName = song.name,
            initialGenres = song.genre,
            initialDescription = song.description,
            confirmLabel = "Save",
            onDismiss = { editing = null },
            onConfirm = { name, tags, description ->
                editing = null

                viewModel.updateDetails(
                    song.id,
                    name,
                    tags,
                    description
                )
            }
        )
    }
}

private val MUSIC_TAGS = listOf(
    "Rock",
    "Pop",
    "Jazz",
    "Hip-Hop",
    "Country"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongDetailsDialog(
    title: String,
    initialName: String,
    initialGenres: List<String>,
    initialDescription: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>, String) -> Unit,
    templates: List<Pair<String, String>> = emptyList(),
    onTemplateClick: (String) -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember {
        mutableStateOf(initialDescription)
    }
    var selectedTags by remember {
        mutableStateOf(initialGenres.toSet())
    }

    val fredoka = FontFamily(
        Font(R.font.fredoka_medium, FontWeight.Normal)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Song name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Description",
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        if (it.length <= 1000) {
                            description = it
                        }
                    },
                    label = { Text("Song description") },
                    placeholder = {
                        Text("Tell listeners about your song...")
                    },
                    supportingText = {
                        Text("${description.length}/1000")
                    },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Song Tags",
                    fontWeight = FontWeight.Bold
                )

                Text("Select all genres that describe this song.")

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MUSIC_TAGS.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = {
                                selectedTags =
                                    if (tag in selectedTags) {
                                        selectedTags - tag
                                    } else {
                                        selectedTags + tag
                                    }
                            },
                            label = {
                                Text(
                                    tag,
                                    fontFamily = fredoka,
                                    fontWeight = if (tag in selectedTags)
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5D36EB),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Color(0xFF444444)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = tag in selectedTags,
                                borderColor = Color(0xFF5D36EB),
                                selectedBorderColor = Color(0xFF5D36EB)
                            )
                        )
                    }
                }

                if (templates.isNotEmpty()) {
                    Text(
                        "Start from a template",
                        fontWeight = FontWeight.Bold
                    )

                    templates.forEach { template ->
                        Button(
                            onClick = {
                                onTemplateClick(template.first)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5D36EB),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                template.second,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.trim(),
                        MUSIC_TAGS.filter { it in selectedTags },
                        description.trim()
                    )
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@SuppressLint("ContextCastToActivity")
    @Composable
    fun JoinSessionDialog(
        navController: NavController,
        showDialog: Boolean,
        onDismiss: () -> Unit
    ) {
    val collabVm: CollaborationViewModel =
        viewModel(LocalContext.current as ComponentActivity)

    val isLoading by collabVm.isLoading.collectAsState()
    val error by collabVm.error.collectAsState()

    var code by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoading) onDismiss()
            },
            title = { Text("Join a collab session!") },
            text = {
                Column {
                    Text("Enter the session code your partner shared.")
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Session code") },
                        singleLine = true,
                        enabled = !isLoading
                    )

                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFB3261E))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = code.isNotBlank() && !isLoading,
                    onClick = {
                        collabVm.joinSession(code) { songId ->
                            onDismiss()
                            code = ""
                            navController.navigate("EditorScreen/$songId") {
                                launchSingleTop = true
                            }
                        }
                    }
                ) {
                    Text(if (isLoading) "Joining…" else "Join")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isLoading,
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}