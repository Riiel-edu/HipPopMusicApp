package com.example.csc490seniorproject.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

private data class BlockedPerson(
    val uid: String,
    val username: String
)

// Self-contained, same style as ChatBox -- talks to Firestore directly
// rather than going through a ViewModel, since this screen's only job is
// to list /users/{uid}/blockedUsers and let you delete entries from it.
@Composable
fun BlockedUsersScreen(navController: NavController) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val purple = Color(0xFF683CF5)

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

    val uid = currentUid

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = purple
                )
            }

            Text(
                text = "Blocked users",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        if (uid == null) {
            Text(
                text = "Please log in to manage blocked users.",
                modifier = Modifier.padding(20.dp)
            )
            return@Column
        }

        val blockedUsers = remember(uid) {
            mutableStateListOf<BlockedPerson>()
        }
        var loading by remember(uid) {
            mutableStateOf(true)
        }
        var error by remember(uid) {
            mutableStateOf<String?>(null)
        }
        var unblockingUid by remember(uid) {
            mutableStateOf<String?>(null)
        }

        DisposableEffect(uid) {
            var active = true

            val registration = db.collection("users")
                .document(uid)
                .collection("blockedUsers")
                .addSnapshotListener { snapshot, exception ->
                    if (active) {
                        loading = false

                        if (exception != null) {
                            error = exception.localizedMessage
                                ?: "Couldn't load blocked users."
                        } else {
                            error = null

                            blockedUsers.clear()
                            blockedUsers.addAll(
                                snapshot?.documents.orEmpty()
                                    .map { document ->
                                        BlockedPerson(
                                            uid = document.id,
                                            username = document.getString("blockedUsername")
                                                ?: "Unknown user"
                                        )
                                    }
                                    .sortedBy { it.username.lowercase() }
                            )
                        }
                    }
                }

            onDispose {
                active = false
                registration.remove()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            when {
                loading -> {
                    CircularProgressIndicator()
                }

                blockedUsers.isEmpty() -> {
                    Text(
                        text = "You haven't blocked anyone.",
                        color = Color.Gray
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = blockedUsers,
                            key = { it.uid }
                        ) { person ->
                            Surface(
                                color = Color(0xFFF7F4FF),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = person.username,
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedButton(
                                        enabled = unblockingUid != person.uid,
                                        onClick = {
                                            unblockingUid = person.uid
                                            error = null

                                            db.collection("users").document(uid)
                                                .collection("blockedUsers")
                                                .document(person.uid)
                                                .delete()
                                                .addOnSuccessListener {
                                                    unblockingUid = null
                                                }
                                                .addOnFailureListener {
                                                    unblockingUid = null
                                                    error = it.localizedMessage
                                                        ?: "Couldn't unblock this user."
                                                }
                                        }
                                    ) {
                                        Text(
                                            if (unblockingUid == person.uid) {
                                                "Unblocking…"
                                            } else {
                                                "Unblock"
                                            }
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
