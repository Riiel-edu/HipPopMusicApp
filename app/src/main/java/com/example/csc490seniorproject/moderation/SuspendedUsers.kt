package com.example.csc490seniorproject.moderation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.csc490seniorproject.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

@Composable
fun SuspendedUsersButton() {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))

    var currentUid by remember {
        mutableStateOf(auth.currentUser?.uid)
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

    key(currentUid) {
        var expanded by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var suspendedCount by remember { mutableStateOf<Int?>(null) }
        var bannedCount by remember { mutableStateOf<Int?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        val uid = currentUid

        // Closing the card cancels its loading coroutine.
        // Opening it again fetches fresh counts.
        LaunchedEffect(expanded, uid) {
            if (!expanded || uid == null) {
                return@LaunchedEffect
            }

            busy = true
            error = null
            suspendedCount = null
            bannedCount = null

            try {
                val role = db.collection("moderators")
                    .document(uid)
                    .get(Source.SERVER)
                    .await()

                check(role.getBoolean("enabled") == true) {
                    "Moderator access required."
                }

                val records = db.collection("accountModeration")
                    .get(Source.SERVER)
                    .await()

                var suspended = 0
                var banned = 0

                val fifteenDays = 15L * 24 * 60 * 60 * 1000

                for (record in records.documents) {
                    val count =
                        record.getLong("violationCount") ?: 0L

                    if (count <= 0L) continue

                    val incidentId = record.getString("incidentId")

                    check(!incidentId.isNullOrBlank()) {
                        "A moderation record is missing its incident ID."
                    }

                    val appeal = db.collection("accountAppeals")
                        .document(record.id)
                        .collection("incidents")
                        .document(incidentId)
                        .get(Source.SERVER)
                        .await()

                    if (appeal.getString("status") == "ACCEPTED") {
                        continue
                    }

                    if (count >= 4L) {
                        banned++
                    } else {
                        val startedAt = record
                            .getTimestamp("startedAt")
                            ?.toDate()
                            ?.time

                        check(startedAt != null) {
                            "A suspension is missing its start date."
                        }

                        if (
                            System.currentTimeMillis() <
                            startedAt + fifteenDays
                        ) {
                            suspended++
                        }
                    }
                }

                if (auth.currentUser?.uid == uid) {
                    suspendedCount = suspended
                    bannedCount = banned
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (auth.currentUser?.uid == uid) {
                    error = e.localizedMessage
                        ?: "Couldn't load suspended users."
                }
            } finally {
                busy = false
            }
        }

        Surface(
            color = Color(0xFFBFB1FA),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Suspended Users",
                        fontFamily = fredoka,
                        fontSize = 22.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    enabled = uid != null,
                    onClick = {
                        expanded = !expanded
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5D36EB)
                    )
                ) {
                    Text(
                        text = if (expanded) {
                            "Hide Suspended Users ▲"
                        } else {
                            "View Suspended Users ▼"
                        },
                        fontFamily = fredoka
                    )
                }

                if (expanded) {
                    HorizontalDivider(
                        color = Color.White,
                        thickness = 2.dp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    when {
                        busy -> {
                            Text("Loading suspended users…")
                        }

                        error != null -> {
                            Text(
                                text = error.orEmpty(),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        suspendedCount != null && bannedCount != null -> {
                            Text(
                                text = "Currently suspended: $suspendedCount"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Permanently banned: $bannedCount"
                            )
                        }
                    }
                }
            }
        }
    }
}