package com.example.csc490seniorproject.screens.editor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.objects.Instrument
import com.example.csc490seniorproject.objects.Note
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.objects.Track
import com.example.csc490seniorproject.objects.endStep
import com.example.csc490seniorproject.objects.stepDurationMs
import com.example.csc490seniorproject.objects.stepsPerBar
import com.example.csc490seniorproject.objects.totalSteps


private val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))

// Partner-owned tracks get the same teal as the track bar, so ownership reads
// consistently: purple = yours, teal = your partner's (read-only for you).
private val PartnerHeader = Color(0xFF6CC3B8)
private val PartnerRowPlain = Color(0xFFEAF7F5)
private val PartnerRowAccidental = Color(0xFFD5EEEA)

private data class RowSpec(val key: Int, val label: String, val color: Color, val isAccidental: Boolean)

private val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private val noteColors = listOf(
    Color(0xFFFF6B6B), // C  - red
    Color(0xFFFF9F6B), // C# - red-orange
    Color(0xFFFFC75B), // D  - orange
    Color(0xFFFFE66B), // D# - yellow-orange
    Color(0xFFF4E04D), // E  - yellow
    Color(0xFFB5E655), // F  - yellow-green
    Color(0xFF6BCB77), // F# - green
    Color(0xFF4DCFC0), // G  - teal
    Color(0xFF4D96FF), // G# - blue
    Color(0xFF7A6BFF), // A  - indigo
    Color(0xFFB26BFF), // A# - purple
    Color(0xFFFF6BCB)  // B  - pink
)

private const val MIN_OCTAVE = 2
private const val MAX_OCTAVE = 7

private fun pitchFor(octave: Int, pitchClass: Int): Int = (octave + 1) * 12 + pitchClass
private fun pitchClassOf(pitch: Int): Int = ((pitch % 12) + 12) % 12

private fun melodicRows(): List<RowSpec> =
    noteNames.mapIndexed { index, name ->
        RowSpec(key = index, label = name, color = noteColors[index], isAccidental = name.endsWith("#"))
    }

private fun drumRows(): List<RowSpec> = listOf(
    RowSpec(49, "Crash", Color(0xFF4DCFC0), false),
    RowSpec(45, "Tom", Color(0xFF6BCB77), true),
    RowSpec(46, "Open Hat", Color(0xFFF4E04D), false),
    RowSpec(42, "Closed Hat", Color(0xFFFFC75B), true),
    RowSpec(38, "Snare", Color(0xFFFF6B6B), false),
    RowSpec(36, "Kick", Color(0xFF7A6BFF), true)
)

@Composable
fun MultiTrackEditor(
    song: Song,
    currentStep: Int,
    isPlaying: Boolean,
    activeOctaveByTrack: Map<String, Int>,
    onSeek: (Int) -> Unit,
    onOctaveChange: (trackId: String, octave: Int) -> Unit,
    onPlaceNote: (trackId: String, pitch: Int, step: Int) -> Unit,
    onDeleteNote: (trackId: String, noteId: String) -> Unit,
    // --- Collaboration (optional; defaults leave solo editing unchanged) ---
    collaborating: Boolean = false,
    currentUserId: String? = null
) {
    val totalSteps = song.totalSteps()
    val stepsPerBar = song.stepsPerBar()

    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val cellWidthPx = with(density) { 40.dp.toPx() }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }

        LaunchedEffect(currentStep, isPlaying) {
            if (isPlaying && viewportWidthPx > 0) {
                val playheadPx = currentStep * cellWidthPx
                val target = (playheadPx - viewportWidthPx / 2f).toInt().coerceIn(0, hScroll.maxValue)
                val stepMs = song.stepDurationMs().toInt().coerceAtLeast(1)
                hScroll.animateScrollTo(target, animationSpec = tween(durationMillis = stepMs, easing = LinearEasing))
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(60.dp).height(28.dp))
                    SeekRuler(
                        totalSteps = totalSteps,
                        stepsPerBar = stepsPerBar,
                        currentStep = currentStep,
                        hScroll = hScroll,
                        onSeek = onSeek
                    )
                }

                Column {
                    song.tracks.forEachIndexed { index, track ->
                        val octave = activeOctaveByTrack[track.id] ?: 4
                        val locked = collaborating && track.ownerId != currentUserId
                        TrackPianoRollSection(
                            track = track,
                            locked = locked,
                            totalSteps = totalSteps,
                            stepsPerBar = stepsPerBar,
                            playheadStep = if (isPlaying) currentStep else null,
                            hScroll = hScroll,
                            octave = octave,
                            onOctaveChange = { newOctave -> onOctaveChange(track.id, newOctave) },
                            onPlaceNote = { pitch, step -> onPlaceNote(track.id, pitch, step) },
                            onDeleteNote = { noteId -> onDeleteNote(track.id, noteId) }
                        )
                        if (index != song.tracks.lastIndex) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekRuler(
    totalSteps: Int,
    stepsPerBar: Int,
    currentStep: Int,
    hScroll: ScrollState,
    onSeek: (Int) -> Unit
) {
    val density = LocalDensity.current
    val cellWidthPx = with(density) { 40.dp.toPx() }
    val barCount = (totalSteps / stepsPerBar).coerceAtLeast(1)
    val totalWidth = 40.dp * totalSteps

    fun stepAt(x: Float): Int = (x / cellWidthPx).toInt().coerceIn(0, totalSteps - 1)

    Box(modifier = Modifier.height(28.dp).horizontalScroll(hScroll, enabled = false)) {
        Box(
            modifier = Modifier
                .width(totalWidth)
                .height(28.dp)
                .pointerInput(totalSteps) {
                    detectTapGestures { offset -> onSeek(stepAt(offset.x)) }
                }
                .pointerInput(totalSteps) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onSeek(stepAt(change.position.x))
                    }
                }
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(barCount) { barIndex ->
                    Box(
                        modifier = Modifier.width(40.dp * stepsPerBar).height(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "${barIndex + 1}", fontFamily = fredoka, fontSize = 12.sp, color = Color(0xFF5d36eb))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .padding(start = 40.dp * currentStep)
                    .width(3.dp)
                    .height(28.dp)
                    .background(Color(0xFFFF6B6B))
            )
        }
    }
}

@Composable
private fun TrackPianoRollSection(
    track: Track,
    locked: Boolean,
    totalSteps: Int,
    stepsPerBar: Int,
    playheadStep: Int?,
    hScroll: ScrollState,
    octave: Int,
    onOctaveChange: (Int) -> Unit,
    onPlaceNote: (pitch: Int, step: Int) -> Unit,
    onDeleteNote: (noteId: String) -> Unit
) {
    val isDrums = track.instrument == Instrument.DRUMS
    val rows = remember(track.instrument) { if (isDrums) drumRows() else melodicRows() }
    val vScroll = rememberScrollState()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = if (locked) PartnerHeader else Color(0xFFbfb1fa),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (locked) {
                        "${track.instrument.emoji()} ${track.name} · 🔒 Partner's track"
                    } else {
                        "${track.instrument.emoji()} ${track.name}"
                    },
                    fontFamily = fredoka,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            // The octave only decides where YOUR taps land, so it's pointless on a track you can't edit.
            if (!isDrums && !locked) {
                Spacer(modifier = Modifier.width(8.dp))
                OctaveStepper(octave = octave, onOctaveChange = onOctaveChange)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth().height(36.dp * rows.size.coerceAtMost(6))) {
            KeysColumn(rows = rows, locked = locked, vScroll = vScroll)
            NoteGrid(
                rows = rows,
                instrument = track.instrument,
                locked = locked,
                octave = octave,
                notes = track.notes,
                totalSteps = totalSteps,
                stepsPerBar = stepsPerBar,
                playheadStep = playheadStep,
                hScroll = hScroll,
                vScroll = vScroll,
                onPlaceNote = onPlaceNote,
                onDeleteNote = onDeleteNote
            )
        }
    }
}

@Composable
private fun OctaveStepper(octave: Int, onOctaveChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(
            onClick = { if (octave > MIN_OCTAVE) onOctaveChange(octave - 1) },
            modifier = Modifier.size(28.dp).background(Color(0xFF5d36eb), CircleShape).padding(2.dp)
        ) {
            Text(text = "–", fontFamily = fredoka, color = Color.White, fontSize = 16.sp)
        }
        Text(
            text = "Oct $octave",
            fontFamily = fredoka,
            color = Color(0xFF5d36eb),
            fontSize = 12.sp,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { if (octave < MAX_OCTAVE) onOctaveChange(octave + 1) },
            modifier = Modifier.size(28.dp).background(Color(0xFF5d36eb), CircleShape).padding(2.dp)
        ) {
            Text(text = "+", fontFamily = fredoka, color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun KeysColumn(rows: List<RowSpec>, locked: Boolean, vScroll: ScrollState) {
    Column(modifier = Modifier.width(60.dp).verticalScroll(vScroll, enabled = false)) {
        rows.forEach { row ->
            val background = when {
                locked && row.isAccidental -> PartnerRowAccidental
                locked -> PartnerRowPlain
                row.isAccidental -> Color(0xFFF1EDFC)
                else -> Color.White
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(background),
                contentAlignment = Alignment.Center
            ) {
                Text(text = row.label, fontFamily = fredoka, fontSize = 11.sp, color = Color(0xFF444444), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun NoteGrid(
    rows: List<RowSpec>,
    instrument: Instrument,
    locked: Boolean,
    octave: Int,
    notes: List<Note>,
    totalSteps: Int,
    stepsPerBar: Int,
    playheadStep: Int?,
    hScroll: ScrollState,
    vScroll: ScrollState,
    onPlaceNote: (pitch: Int, step: Int) -> Unit,
    onDeleteNote: (noteId: String) -> Unit
) {
    val density = LocalDensity.current
    val cellWidthPx = with(density) { 40.dp.toPx() }
    val cellHeightPx = with(density) { 36.dp.toPx() }
    val gridWidth = 40.dp * totalSteps
    val gridHeight = 36.dp * rows.size

    fun rowIndexFor(notePitch: Int): Int =
        if (instrument == Instrument.DRUMS) rows.indexOfFirst { it.key == notePitch }
        else rows.indexOfFirst { it.key == pitchClassOf(notePitch) }

    Box(modifier = Modifier.horizontalScroll(hScroll, enabled = true).verticalScroll(vScroll, enabled = true)) {
        Canvas(
            modifier = Modifier
                .size(gridWidth, gridHeight)
                // Taps on a partner's track still reach the editor, which shows the
                // "belongs to your partner" notice instead of changing anything.
                .pointerInput(notes, rows, octave) {
                    detectTapGestures { offset ->
                        val step = (offset.x / cellWidthPx).toInt().coerceIn(0, totalSteps - 1)
                        val rowIndex = (offset.y / cellHeightPx).toInt().coerceIn(0, rows.size - 1)
                        val row = rows[rowIndex]
                        val pitch = if (instrument == Instrument.DRUMS) row.key else pitchFor(octave, row.key)
                        val existing = notes.firstOrNull { rowIndexFor(it.pitch) == rowIndex && step in it.startStep until it.endStep() }
                        if (existing != null) onDeleteNote(existing.id) else onPlaceNote(pitch, step)
                    }
                }
        ) {
            rows.forEachIndexed { rowIndex, row ->
                val rowColor = when {
                    locked && row.isAccidental -> PartnerRowAccidental
                    locked -> PartnerRowPlain
                    row.isAccidental -> Color(0xFFF1EDFC)
                    else -> Color.White
                }
                drawRect(
                    color = rowColor,
                    topLeft = Offset(0f, rowIndex * cellHeightPx),
                    size = Size(gridWidth.toPx(), cellHeightPx)
                )
            }
            for (rowIndex in 0..rows.size) {
                val y = rowIndex * cellHeightPx
                drawLine(Color(0xFFEDEDED), Offset(0f, y), Offset(gridWidth.toPx(), y))
            }
            for (step in 0..totalSteps) {
                val x = step * cellWidthPx
                val isBarLine = step % stepsPerBar == 0
                drawLine(
                    color = if (isBarLine) Color(0xFFbfb1fa) else Color(0xFFEDEDED),
                    start = Offset(x, 0f),
                    end = Offset(x, gridHeight.toPx()),
                    strokeWidth = if (isBarLine) 2f else 1f
                )
            }

            notes.forEach { note ->
                val rowIndex = rowIndexFor(note.pitch)
                if (rowIndex < 0) return@forEach
                val row = rows[rowIndex]
                val isActive = playheadStep != null && playheadStep >= note.startStep && playheadStep < note.endStep()
                val topLeft = Offset(note.startStep * cellWidthPx + 2f, rowIndex * cellHeightPx + 2f)
                val size = Size(note.lengthSteps * cellWidthPx - 4f, cellHeightPx - 4f)

                if (isActive) {
                    drawRoundRect(color = Color.White, topLeft = topLeft, size = size, cornerRadius = CornerRadius(8f, 8f))
                    drawRoundRect(color = row.color, topLeft = topLeft, size = size, cornerRadius = CornerRadius(8f, 8f), style = Stroke(width = 5f))
                } else {
                    drawRoundRect(color = row.color, topLeft = topLeft, size = size, cornerRadius = CornerRadius(8f, 8f))
                }
            }

            if (playheadStep != null) {
                val x = playheadStep * cellWidthPx
                drawLine(color = Color(0xFF33313F), start = Offset(x, 0f), end = Offset(x, gridHeight.toPx()), strokeWidth = 3f)
            }
        }
    }
}