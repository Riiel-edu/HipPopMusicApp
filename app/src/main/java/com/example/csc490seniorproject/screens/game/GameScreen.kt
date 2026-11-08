package com.example.csc490seniorproject.screens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.csc490seniorproject.viewmodels.TapHitEffect
import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment.Companion.Unbounded
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.objects.Beatmap
import com.example.csc490seniorproject.objects.BeatNoteType
import com.example.csc490seniorproject.objects.MultiplayerPlayerStats
import com.example.csc490seniorproject.viewmodels.GameVM
import com.example.csc490seniorproject.viewmodels.FallingNote
import com.example.csc490seniorproject.viewmodels.Judgment
import com.example.csc490seniorproject.viewmodels.MultiplayerMatchResult
import com.example.csc490seniorproject.viewmodels.MultiplayerVM
import com.example.csc490seniorproject.viewmodels.StarRating
import com.example.csc490seniorproject.viewmodels.WorkspaceViewModel
import com.example.csc490seniorproject.viewmodels.starsForPercent
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))
private val GameBackground = Color(0xFF221B3F)
private val BUTTON_ROW_HEIGHT = 90.dp

private val FALL_DURATION_MS = GameVM.FALL_DURATION_MS

@SuppressLint("ContextCastToActivity")
@Composable
fun GameScreen(viewModel: GameVM, beatmap: Beatmap, navController: NavController, modifier: Modifier = Modifier, multiplayerRoomId: String? = null) {
    val workspaceViewModel: WorkspaceViewModel = viewModel(LocalContext.current as ComponentActivity)

    val multiplayerViewModel: MultiplayerVM =
        viewModel(LocalContext.current as ComponentActivity)

    val opponentStats by multiplayerViewModel.opponentStats.collectAsState()

    val matchResult by multiplayerViewModel.matchResult.collectAsState()

    LaunchedEffect(beatmap.id, multiplayerRoomId) {

        viewModel.loadBeatmap(beatmap)

        if (multiplayerRoomId != null) {
            multiplayerViewModel.resetMatchState()
            multiplayerViewModel.startListening(multiplayerRoomId)

            multiplayerViewModel.currentRoom.first {
                it?.status == "playing"
            }
        }

        val songLoaded =
            workspaceViewModel.loadSongById(beatmap.songId)

        if (songLoaded) {
            workspaceViewModel.prepareSong()

            viewModel.start()

            workspaceViewModel.playSongFromStart(
                startDelayMs = GameVM.LEAD_IN_MS.toLong()
            )
        }
    }

    DisposableEffect(multiplayerRoomId) {
        onDispose {
            viewModel.stop()
            workspaceViewModel.stopSong()

            if (multiplayerRoomId != null) {
                multiplayerViewModel.stopListening()
            }
        }
    }

    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val judgedIds by viewModel.judgedNoteIds.collectAsState()
    val score by viewModel.score.collectAsState()
    val combo by viewModel.combo.collectAsState()
    val isOnFire by viewModel.isOnFire.collectAsState()
    val lastJudgment by viewModel.lastJudgment.collectAsState()
    val isGameOver by viewModel.isGameOver.collectAsState()
    val maxCombo by viewModel.maxCombo.collectAsState()
    val activeHoldColumns by viewModel.activeHoldColumns.collectAsState()
    val perfectCount by viewModel.perfectCount.collectAsState()
    val goodCount by viewModel.goodCount.collectAsState()
    val missCount by viewModel.missCount.collectAsState()
    val tapHitEffect by viewModel.tapHitEffect.collectAsState()
    val holdReleaseEffect by viewModel.holdReleaseEffect.collectAsState()

    LaunchedEffect(isGameOver) {
        if (isGameOver) {
            workspaceViewModel.stopSong()
            viewModel.stop()
        }
    }

    LaunchedEffect(multiplayerRoomId) {

        val roomId = multiplayerRoomId ?: return@LaunchedEffect

        multiplayerViewModel.currentRoom.first {
            it?.status == "playing"
        }

        val username = multiplayerViewModel.getCurrentUsername()

        snapshotFlow {
            val percentage = if (viewModel.maxPossibleScore > 0) {
                (score.toDouble() / viewModel.maxPossibleScore) * 100.0
            } else {
                0.0
            }

            MultiplayerPlayerStats(
                username = username,
                score = score,
                percentage = percentage,
                combo = combo,
                maxCombo = maxCombo,
                isOnFire = isOnFire,
                totalStars = starsForPercent(percentage).starsFilled,
                perfectCount = perfectCount,
                goodCount = goodCount,
                missCount = missCount,
                finished = isGameOver
            )
        }
            .distinctUntilChanged()
            .collectLatest { stats ->

                delay(150.milliseconds)

                try {
                    if (stats.finished) {
                        multiplayerViewModel.finishMatch(stats)
                    } else {
                        multiplayerViewModel.publishStats(stats)
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "MultiplayerGame",
                        "Failed to publish player statistics",
                        e
                    )
                }
            }
    }

    val colors = laneColors(beatmap.columnCount)

    val visibleNotes = remember(elapsedMs, judgedIds) {
        viewModel.fallingNotes.filter { note ->
            val spawnTime = note.hitTimeMs - FALL_DURATION_MS
            if (note.type == BeatNoteType.HOLD) {
                elapsedMs >= spawnTime && elapsedMs <= note.releaseTimeMs + 200
            } else {
                note.id !in judgedIds && elapsedMs >= spawnTime && elapsedMs <= note.hitTimeMs + 200
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(GameBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScoreHud(
                score = score,
                maxPossibleScore = viewModel.maxPossibleScore,
                combo = combo,
                isOnFire = isOnFire
            )

            if (multiplayerRoomId != null) {
                OpponentHud(stats = opponentStats)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ComboDisplay(
                        combo = combo,
                        isOnFire = isOnFire
                    )
                }

                HitButtonsRow(
                    columnCount = beatmap.columnCount,
                    colors = colors,
                    activeHoldColumns = activeHoldColumns,
                    onLanePress = { column -> viewModel.onLanePress(column) },
                    onLaneRelease = { column -> viewModel.onLaneRelease(column) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(BUTTON_ROW_HEIGHT)
                )

                FallingNotesField(
                    columnCount = beatmap.columnCount,
                    notes = visibleNotes,
                    elapsedMs = elapsedMs.toDouble(),
                    activeHoldColumns = activeHoldColumns,
                    tapHitEffect = tapHitEffect,
                    holdReleaseEffect = holdReleaseEffect
                )
                JudgmentPopup(judgment = lastJudgment)
            }
        }

        if (isGameOver) {

            if (multiplayerRoomId == null) {
                GameOverOverlay(
                    score = score,
                    maxPossibleScore = viewModel.maxPossibleScore,
                    maxCombo = maxCombo,
                    perfectCount = perfectCount,
                    goodCount = goodCount,
                    missCount = missCount,
                    onTapToExit = {
                        navController.popBackStack()
                    }
                )

            } else {
                if (matchResult == null) {
                    MultiplayerWaitingOverlay()
                } else {
                    MultiplayerResultsOverlay(
                        result = matchResult!!,
                        localUserId = com.google.firebase.auth.FirebaseAuth
                            .getInstance().currentUser?.uid,
                        onTapToExit = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreHud(
    score: Int,
    maxPossibleScore: Int,
    combo: Int,
    isOnFire: Boolean
) {
    val fireBackground by animateColorAsState(
        targetValue = if (isOnFire) {
            Color(0xFF633A21)
        } else {
            GameBackground
        },
        animationSpec = tween(durationMillis = 250)
    )

    // Calculate live percentage.
    val percentage = if (maxPossibleScore > 0) {
        (score.toDouble() / maxPossibleScore) * 100.0
    } else {
        0.0
    }

    // Calculate live star rating.
    val rating = starsForPercent(percentage)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(fireBackground)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SCORE (PERCENTAGE)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$score",
                fontFamily = fredoka,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = if (isOnFire) {
                    Color(0xFFFFC857)
                } else {
                    Color.White
                }
            )

            Spacer(modifier = Modifier.padding(horizontal = 3.dp))

            Text(
                text = "(${String.format("%.1f", percentage)}%)",
                fontFamily = fredoka,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        // TOTAL STARS (5 stars that fill as earned)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u2605".repeat(rating.starsFilled) +
                        "\u2606".repeat(5 - rating.starsFilled),
                fontFamily = fredoka,
                fontSize = 22.sp,
                color = Color(0xFFFFD700)
            )
        }
    }
}

@Composable
private fun OpponentHud(
    stats: MultiplayerPlayerStats?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF352850))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {

        Text(
            text = stats?.username ?: "Opponent",
            fontFamily = fredoka,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFFBFA7FF)
        )

        if (stats == null) {
            Text(
                text = "Waiting for opponent statistics...",
                fontFamily = fredoka,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column {
                    Text(
                        text = "SCORE",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Text(
                        text = stats.score.toString(),
                        fontFamily = fredoka,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )
                }

                Column {
                    Text(
                        text = "PERCENT",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Text(
                        text = "${String.format("%.1f", stats.percentage)}%",
                        fontFamily = fredoka,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Column {
                    Text(
                        text = "COMBO",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Text(
                        text = stats.combo.toString(),
                        fontFamily = fredoka,
                        fontSize = 20.sp,
                        color = if (stats.isOnFire) {
                            Color(0xFFFFC857)
                        } else {
                            Color.White
                        }
                    )
                }

                Column {
                    Text(
                        text = "STARS",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Text(
                        text = "\u2605".repeat(stats.totalStars) +
                                "\u2606".repeat(5 - stats.totalStars),
                        fontSize = 16.sp,
                        color = Color(0xFFFFD700)
                    )
                }
            }

            if (stats.isOnFire) {
                Text(
                    text = "🔥 OPPONENT ON FIRE!",
                    fontFamily = fredoka,
                    fontSize = 12.sp,
                    color = Color(0xFFFF9F6B)
                )
            }
        }
    }
}

@Composable
private fun JudgmentPopup(judgment: Judgment?) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(judgment) {
        if (judgment != null) {
            visible = true
            delay(500)
            visible = false
        }
    }

    AnimatedVisibility(visible = visible && judgment != null, enter = fadeIn(), exit = fadeOut()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = judgment?.label ?: "",
                fontFamily = fredoka,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = when (judgment) {
                    Judgment.PERFECT -> Color(0xFFF4E04D)
                    Judgment.GOOD -> Color(0xFF6BCB77)
                    Judgment.MISS -> Color(0xFFFF6B6B)
                    null -> Color.White
                },

                modifier = Modifier.blur(
                    radius = 16.dp,
                    edgeTreatment = Unbounded
                )
            )

            Text(
                text = judgment?.label ?: "",
                fontFamily = fredoka,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = Color.White
            )

        }
    }
}


@Composable
private fun FallingNotesField(
    columnCount: Int,
    notes: List<FallingNote>,
    elapsedMs: Double,
    activeHoldColumns: Set<Int>,
    tapHitEffect: TapHitEffect?,
    holdReleaseEffect: TapHitEffect?
) {
    val density = LocalDensity.current
    val buttonHeightPx = with(density) { BUTTON_ROW_HEIGHT.toPx() }

    // A single short animation is reused for TAP hit effects.
    val popProgress = remember { Animatable(1f) }

    LaunchedEffect(tapHitEffect?.id) {
        if (tapHitEffect != null) {
            popProgress.snapTo(0f)
            popProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220)
            )
        }
    }

    val holdPopProgress = remember { Animatable(1f) }

    LaunchedEffect(holdReleaseEffect?.id) {
        if (holdReleaseEffect != null) {
            holdPopProgress.snapTo(0f)
            holdPopProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (columnCount <= 0) return@Canvas

        val laneWidth = size.width / columnCount
        val hitLineY = size.height - buttonHeightPx / 2f

        // Lane dividers.
        for (column in 0..columnCount) {
            val x = column * laneWidth
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2f
            )
        }

        notes.forEach { note ->
            val isHold = note.type == BeatNoteType.HOLD
            val isHeld = isHold && note.column in activeHoldColumns

            val color = if (isHold) {
                Color(0xFF7f01cd)
            } else {
                Color(0xFF3f47cb)
            }

            val laneX = note.column * laneWidth + laneWidth / 2f
            val radius = laneWidth * 0.3f

            val progress = (
                    (elapsedMs - (note.hitTimeMs - FALL_DURATION_MS)) /
                            FALL_DURATION_MS
                    ).coerceIn(0.0, 1.3)

            val headY = (hitLineY * progress).toFloat()

            if (isHold) {
                val tailProgress = (
                        (elapsedMs - (note.releaseTimeMs - FALL_DURATION_MS)) /
                                FALL_DURATION_MS
                        ).coerceIn(0.0, 1.3)

                val tailY = (hitLineY * tailProgress).toFloat()
                val barWidth = radius * 1.5f
                val barHeight = (headY - tailY).coerceAtLeast(0f)

                // Subtle outer highlight while the player holds the lane.
                if (isHeld) {
                    drawRect(
                        color = Color(0xFFb773ff).copy(alpha = 0.25f),
                        topLeft = Offset(
                            laneX - barWidth * 0.75f,
                            tailY
                        ),
                        size = Size(
                            barWidth * 1.5f,
                            barHeight
                        )
                    )
                }

                drawRect(
                    color = color,
                    topLeft = Offset(
                        laneX - barWidth / 2f,
                        tailY
                    ),
                    size = Size(barWidth, barHeight)
                )

                drawRect(
                    color = Color.White,
                    topLeft = Offset(
                        laneX - barWidth * 0.12f,
                        tailY
                    ),
                    size = Size(
                        barWidth * 0.24f,
                        barHeight
                    )
                )

                drawNoteCircle(
                    center = Offset(laneX, tailY),
                    radius = radius,
                    color = color
                )

                drawNoteCircle(
                    center = Offset(laneX, headY),
                    radius = radius,
                    color = color
                )

                // Extra white outline confirms that the HOLD is active.
                if (isHeld) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = radius * 1.15f,
                        center = Offset(laneX, headY),
                        style = Stroke(width = 3f)
                    )
                }
            } else {
                drawNoteCircle(
                    center = Offset(laneX, headY),
                    radius = radius,
                    color = color
                )
            }

        }

        // Expanding ring when a normal TAP note is hit.
        val effect = tapHitEffect
        val pop = popProgress.value

        if (effect != null && pop < 1f &&
            effect.column in 0 until columnCount
        ) {
            val centerX =
                effect.column * laneWidth + laneWidth / 2f

            val popRadius = radiusForLane(laneWidth) *
                    (1f + pop * 1.5f)

            drawCircle(
                color = Color(0xFF8ECFFF).copy(
                    alpha = (1f - pop).coerceIn(0f, 1f)
                ),
                radius = popRadius,
                center = Offset(centerX, hitLineY),
                style = Stroke(width = 5f * (1f - pop))
            )
        }

        val releaseEffect = holdReleaseEffect
        val holdPop = holdPopProgress.value

        if (
            releaseEffect != null &&
            holdPop < 1f &&
            releaseEffect.column in 0 until columnCount
        ) {
            val centerX =
                releaseEffect.column * laneWidth + laneWidth / 2f

            val baseRadius = radiusForLane(laneWidth)

            drawCircle(
                color = Color(0xFF7f01cd).copy(
                    alpha = (1f - holdPop).coerceIn(0f, 1f)
                ),
                radius = baseRadius * (1f + holdPop * 2f),
                center = Offset(centerX, hitLineY),
                style = Stroke(
                    width = 6f * (1f - holdPop)
                )
            )
        }
    }
}

private fun radiusForLane(laneWidth: Float): Float =
    laneWidth * 0.3f

@Composable
private fun ComboDisplay(
    combo: Int,
    isOnFire: Boolean
) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(combo) {
        if (combo > 0) {
            scale.snapTo(1.25f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(180)
            )
        }
    }

    AnimatedVisibility(
        visible = combo > 0,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            Text(
                text = combo.toString(),
                fontFamily = fredoka,
                fontWeight = FontWeight.Bold,
                fontSize = 52.sp,
                color = if (isOnFire) {
                    Color(0xFFFFC857)
                } else {
                    Color.White
                },
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
            )

            Text(
                text = if (isOnFire) "🔥 COMBO" else "COMBO",
                fontFamily = fredoka,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 4.sp,
                color = if (isOnFire) {
                    Color(0xFFFF9F6B)
                } else {
                    Color.White.copy(alpha = 0.75f)
                }
            )
        }
    }
}


@Composable
private fun HitButtonsRow(
    columnCount: Int,
    colors: List<Color>,
    activeHoldColumns: Set<Int>,
    onLanePress: (Int) -> Unit,
    onLaneRelease: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (column in 0 until columnCount) {
            val isHeld = column in activeHoldColumns

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .pointerInput(column) {
                        detectTapGestures(
                            onPress = {
                                onLanePress(column)
                                tryAwaitRelease()
                                onLaneRelease(column)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .height(64.dp)
                        .fillMaxWidth()
                ) {
                    val diameter = size.minDimension * 0.85f
                    val radius = diameter / 2f

                    // Transparent center with a white outline.
                    drawCircle(
                        color = Color.White,
                        radius = radius,
                        center = Offset(
                            x = size.width / 2f,
                            y = size.height / 2f
                        ),
                        style = Stroke(
                            width = if (isHeld) 4.dp.toPx() else 2.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiplayerWaitingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "YOU FINISHED!",
                fontFamily = fredoka,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Color(0xFFFFD700)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Waiting for your opponent...",
                fontFamily = fredoka,
                fontSize = 17.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MultiplayerResultsOverlay(
    result: MultiplayerMatchResult,
    localUserId: String?,
    onTapToExit: () -> Unit
) {
    val didWin = result.winnerId == localUserId

    val title = when {
        result.isDraw -> "IT'S A DRAW!"
        didWin -> "YOU WON!"
        else -> "YOU LOST!"
    }

    val titleColor = when {
        result.isDraw -> Color(0xFFFFD700)
        didWin -> Color(0xFF6BCB77)
        else -> Color(0xFFFF6B6B)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onTapToExit),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "VERSUS COMPLETE",
                fontFamily = fredoka,
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontFamily = fredoka,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                color = titleColor
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "YOU",
                fontFamily = fredoka,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Text(
                text = result.localScore.toString(),
                fontFamily = fredoka,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "OPPONENT",
                fontFamily = fredoka,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Text(
                text = result.opponentScore.toString(),
                fontFamily = fredoka,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Tap anywhere to continue",
                fontFamily = fredoka,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun GameOverOverlay(
    score: Int,
    maxPossibleScore: Int,
    maxCombo: Int,
    perfectCount: Int,
    goodCount: Int,
    missCount: Int,
    onTapToExit: () -> Unit
) {
    // Guard against a zero-note beatmap (maxPossibleScore == 0) dividing by zero.
    val percent = if (maxPossibleScore > 0) (score.toDouble() / maxPossibleScore) * 100.0 else 0.0
    val rating = starsForPercent(percent)
    val isGold = rating == StarRating.GOLD
    val starsText = "\u2605".repeat(rating.starsFilled) + "\u2606".repeat(5 - rating.starsFilled)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onTapToExit),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text(text = "Song Complete!", fontFamily = fredoka, fontSize = 24.sp, color = Color.White)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = starsText,
                fontFamily = fredoka,
                fontSize = 20.sp,
                color = if (isGold) Color(0xFFFFD700) else Color.White.copy(alpha = 0.9f)
            )
            if (isGold) {
                Text(text = rating.label, fontFamily = fredoka, fontSize = 14.sp, color = Color(0xFFFFD700))
            } else if (rating != StarRating.ZERO) {
                Text(text = rating.label, fontFamily = fredoka, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "Score: $score", fontFamily = fredoka, fontSize = 20.sp, color = Color(0xFFF4E04D))

            Spacer(modifier = Modifier.height(4.dp))

            Text(text = "Best Combo: $maxCombo", fontFamily = fredoka, fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))

            Spacer(modifier = Modifier.height(4.dp))

            Text(text = "Perfect Notes: $perfectCount", fontFamily = fredoka, fontSize = 16.sp, color = Color(0xFFF4E04D))

            Spacer(modifier = Modifier.height(4.dp))

            Text(text = "Good Notes: $goodCount", fontFamily = fredoka, fontSize = 16.sp, color = Color(0xFF6BCB77))

            Spacer(modifier = Modifier.height(4.dp))

            Text(text = "Missed Notes: $missCount", fontFamily = fredoka, fontSize = 16.sp, color = Color(0xFFFF6B6B))

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Tap anywhere to continue", fontFamily = fredoka, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}