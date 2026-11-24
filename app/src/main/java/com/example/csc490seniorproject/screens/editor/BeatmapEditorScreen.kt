package com.example.csc490seniorproject.screens

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.objects.BeatNoteType
import com.example.csc490seniorproject.objects.stepDurationMs
import com.example.csc490seniorproject.objects.totalSteps
import com.example.csc490seniorproject.ui.theme.SaveFab
import com.example.csc490seniorproject.viewmodels.BeatmapEditorVM
import com.example.csc490seniorproject.viewmodels.WorkspaceViewModel

@SuppressLint("ContextCastToActivity")
@Composable
fun BeatmapEditorScreen(viewModel: BeatmapEditorVM, navController: NavController, modifier: Modifier = Modifier) {

    val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))
    val beatmap by viewModel.beatmap.collectAsState()
    val activeNoteType by viewModel.activeNoteType.collectAsState()
    val workspaceViewModel: WorkspaceViewModel = viewModel(LocalContext.current as ComponentActivity)
    val isSongPlaying by workspaceViewModel.isSongPlaying.collectAsState()
    val currentSong by workspaceViewModel.currentSong.collectAsState()
    val songStep by workspaceViewModel.songCurrentStep.collectAsState()

    val context = LocalContext.current
    val songId = navController.currentBackStackEntry?.arguments?.getString("songId")

    LaunchedEffect(songId) {
        if (songId != null && currentSong?.id != songId) {
            val loaded = workspaceViewModel.loadSongById(songId)

            if (loaded) {
                workspaceViewModel.seekSong(0)
            }
        } else {
            workspaceViewModel.seekSong(0)
        }
    }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route != "BeatmapEditorScreen") workspaceViewModel.stopSong()
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    val songStepMs = currentSong?.stepDurationMs()
    val beatmapStepMs = beatmap.stepDurationMs()
    val playheadStep: Int? = songStepMs?.let { ms ->
        ((songStep * ms) / beatmapStepMs).toInt().coerceIn(0, (beatmap.totalSteps() - 1).coerceAtLeast(0))
    }

    val saveStatus by viewModel.saveStatus.collectAsState()


    Box(Modifier.fillMaxSize()){
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { navController.popBackStack("MySongsScreen", inclusive = false) }) {
                    Text("← My Songs")
                }

                Button(
                    onClick = {
                        currentSong?.id?.let { songId ->
                            workspaceViewModel.stopSong()
                            navController.navigate("EditorScreen/$songId")
                        }
                    },
                    enabled = currentSong != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFbfb1fa),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("⬅ Song Editor", fontFamily = fredoka)
                }
                Button(
                    onClick = {
                        workspaceViewModel.setCurrentBeatmap(beatmap)
                        navController.navigate("GameScreen/${currentSong?.id}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5d36eb), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "\u25B6 Test Play", fontFamily = fredoka)
                }
                val status by viewModel.saveStatus.collectAsState()
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("\uD83C\uDFAE Beatmap Editor", fontFamily = fredoka, fontSize = 22.sp, color = Color(0xFF5d36eb))

            Spacer(modifier = Modifier.height(10.dp))

            TransportControls(
                isPlaying = isSongPlaying,
                onRestart = { workspaceViewModel.seekSong(0) },
                onPlay = { workspaceViewModel.playSong() },
                onStop = { workspaceViewModel.stopSong() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            LaneCountSelector(
                selected = beatmap.columnCount,
                onSelect = { count -> viewModel.setColumnCount(count) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            NoteTypeSelector(
                selected = activeNoteType,
                onSelect = { type -> viewModel.setActiveNoteType(type) }
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (activeNoteType == BeatNoteType.HOLD) "Drag across the grid to draw how long a Hold lasts" else "Tap the grid to place a note",
                fontFamily = fredoka,
                fontSize = 11.sp,
                color = Color(0xFF5d36eb).copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            BeatmapEditor(
                beatmap = beatmap,
                activeNoteType = activeNoteType,
                onPlaceNote = { column, step, length -> viewModel.placeNote(column, step, length) },
                onResizeNote = { noteId, length -> viewModel.resizeNote(noteId, length) },
                onDeleteNote = { noteId -> viewModel.deleteNote(noteId) },
                playheadStep = playheadStep,
                isPlaying = isSongPlaying,
                onSeek = { beatmapStep ->
                    if (songStepMs != null) {
                        workspaceViewModel.seekSong(((beatmapStep * beatmapStepMs) / songStepMs).toInt())
                    }
                }
            )
        }
        SaveFab(
            status = saveStatus,
            onSave = { viewModel.saveBeatmap() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)   // OK: direct child of Box
        )
    }
}

@Composable
private fun LaneCountSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(4, 6).forEach { count ->
            SelectorChip(
                label = "$count Lanes",
                selectedColor = Color(0xFF5d36eb),
                unselectedColor = Color(0xFFbfb1fa),
                selected = count == selected,
                onClick = { onSelect(count) }
            )
        }
    }
}

@Composable
private fun NoteTypeSelector(selected: BeatNoteType, onSelect: (BeatNoteType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorChip(label = "Tap", selectedColor = Color(0xFF3f47cb), unselectedColor = Color(0xFF8388F2), selected == BeatNoteType.TAP, onClick = { onSelect(BeatNoteType.TAP) })
        SelectorChip(label = "Hold", selectedColor = Color(0xFF7f01cd), unselectedColor = Color(0xFFCE78FF), selected = selected == BeatNoteType.HOLD, onClick = { onSelect(BeatNoteType.HOLD) })
    }
}

@Composable
private fun SelectorChip(label: String, selectedColor: Color, unselectedColor: Color, selected: Boolean, onClick: () -> Unit) {
    val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))

    Box(
        modifier = Modifier
            .background(if (selected) selectedColor else unselectedColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontFamily = fredoka, fontSize = 13.sp, color = Color.White)
    }
}