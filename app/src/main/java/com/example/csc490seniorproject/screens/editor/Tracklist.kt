package com.example.csc490seniorproject.screens.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.objects.Instrument
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.objects.Track

private val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))

// Your tracks keep the app's purple; your partner's are teal, so ownership reads at a glance.
private val MineSelected = Color(0xFF5d36eb)
private val MineIdle = Color(0xFFbfb1fa)
private val MineBorder = Color(0xFF3A1FA6)
private val PartnerSelected = Color(0xFF0F7A6E)
private val PartnerIdle = Color(0xFF6CC3B8)
private val PartnerBorder = Color(0xFF085048)

@Composable
fun TrackBar(
    song: Song,
    selectedTrackId: String,
    onSelectTrack: (String) -> Unit,
    onAddTrack: (name: String, instrument: Instrument) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleSolo: (String) -> Unit,
    // --- Collaboration (all optional; defaults leave solo editing unchanged) ---
    collaborating: Boolean = false,
    currentUserId: String? = null,
    partnerConnected: Boolean = false,
    onHandOff: (trackId: String) -> Unit = {}
) {
    var showPicker by remember { mutableStateOf(false) }
    var handOffTrack by remember { mutableStateOf<Track?>(null) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = song.tracks, key = { it.id }) { track ->
            val mine = !collaborating || track.ownerId == currentUserId
            TrackChip(
                track = track,
                isSelected = track.id == selectedTrackId,
                canDelete = song.tracks.size > 1 && mine,
                collaborating = collaborating,
                mine = mine,
                canHandOff = collaborating && mine && partnerConnected,
                onSelect = { onSelectTrack(track.id) },
                onToggleMute = { onToggleMute(track.id) },
                onToggleSolo = { onToggleSolo(track.id) },
                onDelete = { onDeleteTrack(track.id) },
                onHandOff = { handOffTrack = track }
            )
        }
        item(key = "add_track") {
            AddTrackChip(onClick = { showPicker = true })
        }
    }

    if (showPicker) {
        InstrumentPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { instrument ->
                val takenNames = song.tracks.map { it.name }
                val baseName = instrument.displayName()
                var candidate = baseName
                var suffix = 2
                while (candidate in takenNames) {
                    candidate = "$baseName $suffix"
                    suffix++
                }
                onAddTrack(candidate, instrument)
                showPicker = false
            }
        )
    }

    handOffTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { handOffTrack = null },
            title = { Text("Hand off “${track.name}”?") },
            text = {
                Text("Your partner will be able to edit it, and you won't until they hand it back.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onHandOff(track.id)
                    handOffTrack = null
                }) {
                    Text("Hand off")
                }
            },
            dismissButton = {
                TextButton(onClick = { handOffTrack = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TrackChip(
    track: Track,
    isSelected: Boolean,
    canDelete: Boolean,
    collaborating: Boolean,
    mine: Boolean,
    canHandOff: Boolean,
    onSelect: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onDelete: () -> Unit,
    onHandOff: () -> Unit
) {
    val locked = collaborating && !mine
    val selectedColor = if (locked) PartnerSelected else MineSelected
    val idleColor = if (locked) PartnerIdle else MineIdle
    val borderColor = if (locked) PartnerBorder else MineBorder

    Surface(
        color = if (isSelected) selectedColor else idleColor,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = if (isSelected) 10.dp else 4.dp,
        border = if (isSelected) BorderStroke(2.dp, borderColor) else null,
        modifier = Modifier
            .width(128.dp)
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = track.instrument.emoji(), fontSize = 18.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = track.name,
                    fontFamily = fredoka,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (collaborating) {
                Text(
                    text = if (mine) "YOURS" else "🔒 PARTNER",
                    fontFamily = fredoka,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A partner's track can't be changed by you, so its toggles are shown
                // (so you can see its state) but dimmed and inert.
                ToggleLetter(
                    letter = "M",
                    active = track.muted,
                    activeColor = Color(0xFFFF6B6B),
                    enabled = !locked,
                    onClick = onToggleMute
                )
                ToggleLetter(
                    letter = "S",
                    active = track.solo,
                    activeColor = Color(0xFFF4E04D),
                    enabled = !locked,
                    onClick = onToggleSolo
                )
                if (canHandOff) {
                    ToggleLetter(
                        letter = "⇄",
                        active = false,
                        activeColor = Color.White,
                        onClick = onHandOff
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete ${track.name}",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleLetter(
    letter: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(if (active) activeColor else Color.White.copy(alpha = 0.25f), CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text = letter, fontFamily = fredoka, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddTrackChip(onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Color(0xFFbfb1fa)),
        modifier = Modifier
            .width(64.dp)
            .height(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add track", tint = Color(0xFF5d36eb))
        }
    }
}

/** Simple dialog: a grid of instrument choices, styled like the rest of the app. */
@Composable
private fun InstrumentPickerDialog(onDismiss: () -> Unit, onPick: (Instrument) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 20.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Add an Instrument \uD83C\uDFB5",
                        fontFamily = fredoka,
                        fontSize = 20.sp,
                        color = Color(0xFF5d36eb),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF5d36eb))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Instrument.values().forEach { instrument ->
                    Surface(
                        color = Color(0xFFbfb1fa),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable { onPick(instrument) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(text = instrument.emoji(), fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = instrument.displayName(),
                                fontFamily = fredoka,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}