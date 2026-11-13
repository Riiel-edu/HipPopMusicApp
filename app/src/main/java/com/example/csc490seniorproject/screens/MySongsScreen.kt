package com.example.csc490seniorproject.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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

    LaunchedEffect(Unit) { viewModel.refresh() }   // reloads when you come back from the editor

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(5.dp)) {

            Text("My Songs", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                fontFamily = fredoka, fontSize = 28.sp, color = Color(0xFF5d36eb))

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }

            Spacer(modifier = Modifier.height(5.dp))

            if (songs.isEmpty()) {
                Text("No songs yet. Tap + to create one.", fontFamily = fredoka,
                    modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center)
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
                                        modifier = Modifier.size(90.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            currentItem.name,
                                            fontFamily = fredoka,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Box(modifier = Modifier.wrapContentSize()) {
                                            IconButton(onClick = { expanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Show menu")
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit Song Details") },
                                                    onClick = { expanded = false; editing = currentItem }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(if (currentItem.published) "Unpublish Song" else "Publish Song") },
                                                    onClick = { expanded = false; viewModel.togglePublish(currentItem) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete Song") },
                                                    onClick = { expanded = false; viewModel.delete(currentItem.id) }
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
                                        // Song has no description field, so this shows genre + status instead
                                        Text(
                                            buildString {
                                                append(if (currentItem.genre.isEmpty()) "No genre" else currentItem.genre.joinToString(", "))
                                                append(if (currentItem.published) " • Published" else " • Private")
                                            },
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

                            Spacer(modifier = Modifier.height(12.dp))

                            val buttonColors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5d36eb), contentColor = Color.White
                            )
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(onClick = { navController.navigate("GameScreen/${currentItem.id}") },
                                        colors = buttonColors, modifier = Modifier.weight(1f)) {
                                        Text("▶ Play", fontFamily = fredoka, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(onClick = { navController.navigate("EditorScreen/${currentItem.id}") },
                                        colors = buttonColors, modifier = Modifier.weight(1f)) {
                                        Text("Edit Song", fontFamily = fredoka, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(onClick = { navController.navigate("BeatmapEditorScreen/${currentItem.id}") },
                                        colors = buttonColors, modifier = Modifier.weight(1f)) {
                                        Text("Edit Beatmap", fontFamily = fredoka, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "New song") }
    }

    if (showCreate) {
        SongDetailsDialog(
            title = "New Song",
            initialName = "",
            initialGenres = emptyList(),
            confirmLabel = "Create",
            templates = templates,
            onTemplateClick = { templateId ->
                showCreate = false
                viewModel.createSongFromTemplate(templateId) { id ->
                    navController.navigate("EditorScreen/$id")
                }
            },
            onDismiss = { showCreate = false },
            onConfirm = { name, genres ->
                showCreate = false
                viewModel.createSong(name, genres) { id ->
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
            confirmLabel = "Save",
            onDismiss = { editing = null },
            onConfirm = { name, genres ->
                editing = null
                viewModel.updateDetails(song.id, name.ifBlank { "Untitled Song" }, genres)
            }
        )
    }
}

private val GENRES = listOf("Pop", "Rock", "Electronic", "Hip-Hop", "Classical", "Jazz", "Lo-fi", "Game")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongDetailsDialog(
    title: String,
    initialName: String,
    initialGenres: List<String>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>) -> Unit,
    templates: List<Pair<String, String>> = emptyList(),
    onTemplateClick: (String) -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var genres by remember { mutableStateOf(initialGenres) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Song name") },
                    singleLine = true
                )
                Text("Genre")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GENRES.forEach { g ->
                        FilterChip(
                            selected = g in genres,
                            onClick = { genres = if (g in genres) genres - g else genres + g },
                            label = { Text(g) }
                        )
                    }
                }
                Text("Start from a template")

                templates.forEach { template ->
                    Button(
                        onClick = { onTemplateClick(template.first) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5D36EB),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = template.second,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim(), genres) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}