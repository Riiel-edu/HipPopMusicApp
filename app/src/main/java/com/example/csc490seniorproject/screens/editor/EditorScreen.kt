package com.example.csc490seniorproject.screens

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.csc490seniorproject.objects.Track
import com.example.csc490seniorproject.objects.stepsPerBar
import com.example.csc490seniorproject.screens.editor.CollaborationPanel
import com.example.csc490seniorproject.screens.editor.MultiTrackEditor
import com.example.csc490seniorproject.screens.editor.TrackBar
import com.example.csc490seniorproject.ui.theme.SaveFab
import com.example.csc490seniorproject.viewmodels.CollabTrackSink
import com.example.csc490seniorproject.viewmodels.CollaborationViewModel
import com.example.csc490seniorproject.viewmodels.EditorScreenVM
import com.example.csc490seniorproject.viewmodels.WorkspaceViewModel

@SuppressLint("ContextCastToActivity")
@Composable
fun EditorScreen(viewModel: EditorScreenVM, navController: NavController, modifier: Modifier = Modifier) {

    val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))
    val song by viewModel.song.collectAsState()
    val selectedTrackId by viewModel.selectedTrackId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val noteLengthSteps by viewModel.noteLengthSteps.collectAsState()
    val activeOctaveByTrack by viewModel.activeOctaveByTrack.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()
    val collabNotice by viewModel.collabNotice.collectAsState()

    val workspaceViewModel: WorkspaceViewModel = viewModel(LocalContext.current as ComponentActivity)

    // Scoped to the activity so the session survives navigating between screens.
    val collabVm: CollaborationViewModel = viewModel(LocalContext.current as ComponentActivity)
    val collabSession by collabVm.session.collectAsState()
    val collabTracks by collabVm.tracks.collectAsState()
    val isCollaborating by collabVm.isCollaborating.collectAsState()
    val collabLoading by collabVm.isLoading.collectAsState()
    val collabError by collabVm.error.collectAsState()
    val collabUid = collabVm.currentUserId
    val partnerId = collabSession?.participantIds?.firstOrNull { it != collabUid }

    val trackSink = remember(collabVm) {
        object : CollabTrackSink {
            override fun add(track: Track) = collabVm.addTrack(track)
            override fun update(track: Track) = collabVm.updateTrack(track)
            override fun delete(trackId: String) = collabVm.deleteTrack(trackId)
        }
    }

    // Bind / unbind the editor to the session. Must stay declared BEFORE the
    // tracks effect below so the editor is bound before the first merge.
    LaunchedEffect(isCollaborating, collabSession?.id, collabSession?.status, song.id) {
        val s = collabSession
        val uid = collabVm.currentUserId
        if (isCollaborating && s != null && uid != null) {
            viewModel.startCollaboration(s, uid, trackSink)
        } else {
            viewModel.stopCollaboration()
            // The host ended the session: drop our listeners and state.
            if (s != null && s.status != "active") collabVm.clearSession()
        }
    }

    LaunchedEffect(collabTracks) {
        viewModel.applyRemoteTracks(collabTracks)
    }

    Box(
        Modifier.fillMaxSize()
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { navController.navigate("MySongsScreen") }) {
                    Text("← My Songs")
                }

                Button(
                    onClick = {
                        workspaceViewModel.setCurrentSong(song)
                        navController.navigate("BeatmapEditorScreen/${viewModel.song.value.id}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5d36eb), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "\uD83C\uDFAE Beatmap Editor", fontFamily = fredoka)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("\uD83D\uDCBF Song Editor", fontFamily = fredoka, fontSize = 22.sp, color = Color(0xFF5d36eb))

            Spacer(modifier = Modifier.height(6.dp))

            CollaborationPanel(
                session = collabSession,
                isCollaborating = isCollaborating,
                currentUserId = collabVm.currentUserId,
                isLoading = collabLoading,
                message = collabError ?: collabNotice,
                onStart = { collabVm.createSession(song) },
                onLeave = { collabVm.leaveSession() },
                onDismissMessage = {
                    collabVm.clearError()
                    viewModel.clearCollabNotice()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            EditorToolbar(
                isPlaying = isPlaying,
                bpm = song.bpm,
                totalBars = song.bars,
                onRestart = { viewModel.restartFromStart() },
                onPlay = { viewModel.playSong() },
                onStop = { viewModel.stopPlayback() },
                onBpmChange = { bpm -> viewModel.setBpm(bpm) },
                onJumpToBar = { bar -> viewModel.seekTo((bar - 1) * song.stepsPerBar()) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            NoteLengthSelector(
                selectedSteps = noteLengthSteps,
                onSelect = { steps -> viewModel.setNoteLength(steps) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            TrackBar(
                song = song,
                selectedTrackId = selectedTrackId,
                onSelectTrack = { viewModel.selectTrack(it) },
                onAddTrack = { name, instrument -> viewModel.addTrack(name, instrument) },
                onDeleteTrack = { viewModel.deleteTrack(it) },
                onToggleMute = { viewModel.toggleMute(it) },
                onToggleSolo = { viewModel.toggleSolo(it) },
                collaborating = isCollaborating && collabSession != null,
                currentUserId = collabUid,
                partnerConnected = partnerId != null,
                onHandOff = { trackId -> partnerId?.let { collabVm.assignTrack(trackId, it) } }
            )

            Spacer(modifier = Modifier.height(8.dp))

            MultiTrackEditor(
                song = song,
                currentStep = currentStep,
                isPlaying = isPlaying,
                activeOctaveByTrack = activeOctaveByTrack,
                onSeek = { step -> viewModel.seekTo(step) },
                onOctaveChange = { trackId, octave -> viewModel.setActiveOctave(trackId, octave) },
                onPlaceNote = { trackId, pitch, step -> viewModel.placeNote(trackId, pitch, step) },
                onDeleteNote = { trackId, noteId -> viewModel.deleteNote(trackId, noteId) },
                collaborating = isCollaborating && collabSession != null,
                currentUserId = collabUid
            )
        }
        SaveFab(
            status = saveStatus,
            onSave = { viewModel.saveCurrentSong() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}