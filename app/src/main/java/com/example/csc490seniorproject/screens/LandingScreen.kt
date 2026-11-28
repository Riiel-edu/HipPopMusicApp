package com.example.csc490seniorproject.screens

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.data.SongRepository
import com.example.csc490seniorproject.viewmodels.LandingScreenVM
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor
import com.example.csc490seniorproject.moderation.Reporting
import com.example.csc490seniorproject.moderation.ReportCase
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.example.csc490seniorproject.moderation.Appeals
import com.example.csc490seniorproject.moderation.SuspendedUsersButton

@Composable
fun LandingScreen(viewModel: LandingScreenVM, navController: NavController, username: String) {

    val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))

    val scope = rememberCoroutineScope()
    val topSongs by viewModel.topSongs.collectAsState()
    val friendActivity by viewModel.friendActivity.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.loading.collectAsState()

    val isModerator by viewModel.isModerator.collectAsState()
    val pendingReports by viewModel.pendingReports.collectAsState()
    val finishedReports by viewModel.finishedReports.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    if (isModerator) {

        var showReviewScreen by remember {
            mutableStateOf(false)
        }

        if (showReviewScreen) {
            Reporting.ModeratorReviewScreen(
                onBack = {
                    showReviewScreen = false
                    viewModel.refresh()
                })
            return
        }

        var caseToDismiss by remember {
            mutableStateOf<ReportCase?>(null)
        }

        var dismissalReason by remember {
            mutableStateOf("")
        }

        var dismissalBusy by remember {
            mutableStateOf(false)
        }

        var dismissalError by remember {
            mutableStateOf<String?>(null)
        }

        caseToDismiss?.let { selectedCase ->
            AlertDialog(onDismissRequest = {
                if (!dismissalBusy) {
                    caseToDismiss = null
                }
            }, title = {
                Text("Dismiss report case?")
            }, text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Dismiss the case for " + selectedCase.targetUsername + " without suspending the account."
                    )

                    OutlinedTextField(
                        value = dismissalReason,
                        onValueChange = {
                            dismissalReason = it
                            dismissalError = null
                        },
                        label = {
                            Text("Reason for dismissal")
                        },
                        supportingText = {
                            Text("10–1,000 characters")
                        },
                        enabled = !dismissalBusy,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    dismissalError?.let { message ->
                        Text(
                            text = message, color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }, confirmButton = {
                TextButton(
                    enabled = !dismissalBusy && dismissalReason.trim().length in 10..1000,
                    onClick = {
                        dismissalBusy = true
                        dismissalError = null

                        Reporting.dismissReportCase(
                            targetUid = selectedCase.targetUid,
                            caseId = selectedCase.caseId,
                            reason = dismissalReason,
                            onDone = { failure ->
                                dismissalBusy = false

                                if (failure == null) {
                                    caseToDismiss = null
                                    dismissalReason = ""
                                    viewModel.refresh()
                                } else {
                                    dismissalError = failure
                                }
                            })
                    }) {
                    Text(
                        if (dismissalBusy) "Dismissing…" else "Dismiss case"
                    )
                }
            }, dismissButton = {
                TextButton(
                    enabled = !dismissalBusy, onClick = {
                        caseToDismiss = null
                    }) {
                    Text("Cancel")
                }
            })
        }

        var pendingExpanded by remember { mutableStateOf(false) }
        var appealsExpanded by remember { mutableStateOf(false) }
        var finishedExpanded by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp)
            ) {

                // Dashboard Header
                Text(
                    "♫ Moderator Dashboard ♫",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    fontFamily = fredoka,
                    fontSize = 28.sp,
                    color = Color(0xFF5D36EB)
                )

                Text(
                    "Welcome back, $username!",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    fontFamily = fredoka,
                    fontSize = 17.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    SuspendedUsersButton()

                    Surface(
                        color = Color(0xFFBFB1FA),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Pending Reports",
                                    fontFamily = fredoka,
                                    fontSize = 22.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    "${pendingReports.size}",
                                    fontFamily = fredoka,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )

                                Icon(
                                    imageVector = if (pendingExpanded) Icons.Default.MusicNote
                                    else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    pendingExpanded = !pendingExpanded
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5D36EB)
                                )
                            ) {
                                Text(
                                    if (pendingExpanded) "Hide Reports ▲"
                                    else "View Reports ▼", fontFamily = fredoka
                                )
                            }

                            if (pendingExpanded) {
                                HorizontalDivider(
                                    color = Color.White,
                                    thickness = 2.dp,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )

                                if (!loading && error == null && pendingReports.isEmpty()) {
                                    Text(text = "No pending account reports.")
                                }

                                pendingReports.forEach { reportCase ->

                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {

                                            Text(
                                                "Target: ${reportCase.targetUsername}",
                                                fontFamily = fredoka,
                                                color = Color(0xFF444444)
                                            )

                                            Text(
                                                "Reports: ${reportCase.reportCount}",
                                                fontFamily = fredoka,
                                                color = Color(0xFF444444)
                                            )

                                            reportCase.reports.forEachIndexed { index, report ->

                                                HorizontalDivider(
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                )

                                                Text(
                                                    "Report ${index + 1}",
                                                    fontFamily = fredoka,
                                                    color = Color(0xFF5D36EB)
                                                )

                                                Text(
                                                    "Reason: ${report.reason}",
                                                    fontFamily = fredoka,
                                                    color = Color.DarkGray
                                                )

                                                Text(
                                                    "Evidence: ${report.evidence}",
                                                    fontFamily = fredoka,
                                                    color = Color.DarkGray
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { showReviewScreen = true },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Review")
                                                }

                                                Button(
                                                    enabled = !dismissalBusy,
                                                    onClick = {
                                                        dismissalReason = ""
                                                        dismissalError = null
                                                        caseToDismiss = reportCase
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFD32F2F)
                                                    )
                                                ) {
                                                    Text("Dismiss")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        color = Color(0xFFBFB1FA),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Pending Appeals",
                                    fontFamily = fredoka,
                                    fontSize = 22.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    "", fontFamily = fredoka, color = Color.White, fontSize = 16.sp
                                )

                                Icon(
                                    imageVector = if (pendingExpanded) Icons.Default.MusicNote
                                    else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    appealsExpanded = !appealsExpanded
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5D36EB)
                                )
                            ) {
                                Text(
                                    if (appealsExpanded) "Hide Appeals ▲"
                                    else "View Appeals ▼", fontFamily = fredoka
                                )
                            }

                            if (appealsExpanded) {
                                HorizontalDivider(
                                    color = Color.White,
                                    thickness = 2.dp,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )

                                Appeals.PendingList()
                            }
                        }
                    }


                    Surface(
                        color = Color(0xFFBFB1FA),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            Text(
                                "Finished Reports",
                                fontFamily = fredoka,
                                fontSize = 22.sp,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    finishedExpanded = !finishedExpanded

                                    if (finishedExpanded) {
                                        viewModel.refresh()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5D36EB)
                                )
                            ) {
                                Text(
                                    if (finishedExpanded) "Hide Finished Reports ▲"
                                    else "View Finished Reports ▼", fontFamily = fredoka
                                )
                            }

                            if (finishedExpanded) {
                                HorizontalDivider(
                                    color = Color.White,
                                    thickness = 2.dp,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                                when {
                                    loading -> {
                                        Text(
                                            text = "Loading finished reports…", fontFamily = fredoka
                                        )
                                    }

                                    error != null -> {
                                        Text(
                                            text = error ?: "Couldn't load finished reports.",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    finishedReports.isEmpty() -> {
                                        Text(text = "No finished reports yet.")
                                    }
                                }

                                finishedReports.forEach { report ->
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = when (report.decision) {
                                                    "CONFIRMED" -> "Violation confirmed"
                                                    "DISMISSED" -> "Report dismissed"
                                                    else -> report.decision
                                                },
                                                style = MaterialTheme.typography.titleMedium
                                            )

                                            Text(text = "Reason: ${report.reason}")
                                            Text(text = "Reports reviewed: ${report.reportCount}")

                                            report.createdAt?.let { timestamp ->
                                                Text(
                                                    text = "Reviewed: " +
                                                            java.text.SimpleDateFormat(
                                                                "MMM d, yyyy",
                                                                Locale.US
                                                            ).format(timestamp.toDate())
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {

                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "♫ Welcome Back, $username! ♫",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontFamily = fredoka,
                        fontSize = 28.sp,
                        color = Color(0xFF5d36eb)
                    )
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 30.dp,
                    color = Color(0xFFBFB1FA)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "♪ Dive right back into the music! ♪",
                            fontFamily = fredoka,
                            fontSize = 20.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(2.5.dp))

                        HorizontalDivider(
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 2.dp
                        )

                        Spacer(modifier = Modifier.height(7.5.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    navController.navigate("MySongsScreen")
                                }, colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5d36eb), contentColor = Color.White
                                ), modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Make New Song \uD83D\uDCBF",
                                    fontFamily = fredoka,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = {
                                    navController.navigate("SearchScreen")
                                }, colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5d36eb), contentColor = Color.White
                                ), modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Find New Songs \uD83D\uDCC0",
                                    fontFamily = fredoka,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }


                Surface(
                    color = Color(0xFFbfb1fa),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        Text(
                            "Today's Top 5 🔥",
                            fontFamily = fredoka,
                            fontSize = 26.sp,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        HorizontalDivider(
                            color = Color.White, thickness = 2.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (loading) {
                            Text(
                                "Loading songs...", fontFamily = fredoka, color = Color.White
                            )
                        } else if (topSongs.isEmpty()) {
                            Text(
                                "No published songs yet!", fontFamily = fredoka, color = Color.White
                            )
                        } else {
                            val pagerState = rememberPagerState(
                                pageCount = { topSongs.size })

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxWidth(),
                                    pageSpacing = 12.dp
                                ) { page ->

                                    val song = topSongs[page]

                                    Surface(
                                        color = Color(0xFFBFB1FA),
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
                                                        painter = painterResource(
                                                            id = R.drawable.default_pfp
                                                        ),
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

                                                    val stars = floor(song.rating + 0.5).toInt()
                                                        .coerceIn(0, 5)

                                                    Text(
                                                        text = "★".repeat(stars) + "☆".repeat(5 - stars) + "  " + String.format(
                                                            Locale.US, "%.1f/5.0", song.rating
                                                        ),
                                                        fontFamily = fredoka,
                                                        color = Color(0xFF5D36EB)
                                                    )

                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        shadowElevation = 4.dp,
                                                        color = Color.White,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            song.description.ifBlank { "No description provided" },
                                                            fontFamily = fredoka,
                                                            fontSize = 13.sp,
                                                            color = Color(0xFF444444),
                                                            maxLines = 3,
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

                                                        navController.navigate(
                                                            "GameScreen/${Uri.encode(song.id)}"
                                                        )
                                                    }, colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF5D36EB)
                                                    ), modifier = Modifier.weight(1f)
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
                                                        navController.navigate(
                                                            "SongScreen/${Uri.encode(song.id)}"
                                                        )
                                                    }, colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF5D36EB)
                                                    ), modifier = Modifier.weight(1.6f)
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

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(topSongs.size) { index ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .size(
                                                    if (pagerState.currentPage == index) 10.dp
                                                    else 7.dp
                                                )
                                                .clip(RoundedCornerShape(50))
                                                .background(
                                                    if (pagerState.currentPage == index) Color.White
                                                    else Color.White.copy(alpha = 0.4f)
                                                )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                Text(
                                    "${pagerState.currentPage + 1} of ${topSongs.size}",
                                    fontFamily = fredoka,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFFbfb1fa),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        Text(
                            "Friend's Recent Activity",
                            fontFamily = fredoka,
                            fontSize = 26.sp,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        HorizontalDivider(
                            color = Color.White, thickness = 2.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (loading) {
                            Text(
                                "Loading activity...", fontFamily = fredoka, color = Color.White
                            )
                        } else if (friendActivity.isEmpty()) {
                            Text(
                                "No recent friend activity!",
                                fontFamily = fredoka,
                                color = Color.White
                            )
                        } else {

                            friendActivity.forEach { activity ->

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    Text(
                                        "${activity.friendName} created a song!",
                                        fontFamily = fredoka,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Surface(
                                        color = Color(0xFFBFB1FA),
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
                                                        painter = painterResource(
                                                            id = R.drawable.default_pfp
                                                        ),
                                                        contentScale = ContentScale.Crop,
                                                        contentDescription = null,
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
                                                        activity.song.name,
                                                        fontFamily = fredoka,
                                                        fontSize = 18.sp,
                                                        color = Color.White,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        shadowElevation = 4.dp,
                                                        color = Color.White,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            activity.song.description.ifBlank {
                                                                "No description provided"
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

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Button(
                                                    onClick = {
                                                        navController.navigate(
                                                            "GameScreen/${activity.song.id}"
                                                        )
                                                    }, colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF5D36EB)
                                                    ), modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        "▶ Play", fontFamily = fredoka
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        navController.navigate(
                                                            "SongScreen/${Uri.encode(activity.song.id)}"
                                                        )
                                                    }, colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF5D36EB)
                                                    ), modifier = Modifier.weight(1.6f)
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
                        }
                    }
                }

            }
        }
    }
}