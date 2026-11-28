package com.example.csc490seniorproject.moderation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class Appeals {
    companion object {

        private val db: FirebaseFirestore
            get() = FirebaseFirestore.getInstance()

        private fun submit(
            uid: String,
            incidentId: String,
            reason: String,
            onDone: (String?) -> Unit
        ) {
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
                onDone("Please sign in to your account.")
                return
            }

            if (reason.trim().length !in 10..1000) {
                onDone("Enter between 10 and 1,000 characters.")
                return
            }

            val statusRef = db.collection("accountModeration").document(uid)
            val appealRef = db.collection("accountAppeals")
                .document(uid)
                .collection("incidents")
                .document(incidentId)

            db.runTransaction { transaction ->
                val status = transaction.get(statusRef)
                val existing = transaction.get(appealRef)

                check(status.getString("incidentId") == incidentId) {
                    "Your account restriction changed. Tap Check again."
                }

                check(!existing.exists()) {
                    "You already submitted an appeal for this action."
                }

                transaction.set(
                    appealRef,
                    mapOf(
                        "uid" to uid,
                        "incidentId" to incidentId,
                        "reason" to reason.trim(),
                        "status" to "PENDING",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "decisionReason" to "",
                        "moderatorId" to "",
                        "reviewedAt" to null
                    )
                )

                null
            }.addOnSuccessListener {
                onDone(null)
            }.addOnFailureListener {
                onDone(it.localizedMessage ?: "Couldn't submit appeal.")
            }
        }

        private fun decide(
            appealRef: DocumentReference,
            accept: Boolean,
            reason: String,
            onDone: (String?) -> Unit
        ) {
            val moderatorUid = FirebaseAuth.getInstance().currentUser?.uid

            if (moderatorUid == null) {
                onDone("Please sign in.")
                return
            }

            if (reason.trim().length !in 10..1000) {
                onDone("Enter a decision reason of 10–1,000 characters.")
                return
            }

            db.runTransaction { transaction ->
                val appeal = transaction.get(appealRef)
                val uid = appeal.getString("uid")
                    ?: error("This appeal has no account.")

                val status = transaction.get(
                    db.collection("accountModeration").document(uid)
                )

                check(appeal.getString("status") == "PENDING") {
                    "Another moderator already handled this appeal."
                }

                check(uid != moderatorUid) {
                    "You cannot decide your own appeal."
                }

                if (accept) {
                    check(
                        status.getString("incidentId") ==
                                appeal.getString("incidentId")
                    ) {
                        "A newer account action exists. This appeal cannot " +
                                "remove that newer restriction."
                    }
                }

                transaction.update(
                    appealRef,
                    mapOf(
                        "status" to if (accept) "ACCEPTED" else "REJECTED",
                        "decisionReason" to reason.trim(),
                        "moderatorId" to moderatorUid,
                        "reviewedAt" to FieldValue.serverTimestamp()
                    )
                )

                null
            }.addOnSuccessListener {
                onDone(null)
            }.addOnFailureListener {
                onDone(it.localizedMessage ?: "Couldn't save decision.")
            }
        }

        @Composable
        public fun SubmitButton(
            uid: String,
            incidentId: String
        ) {
            var appeal by remember(uid, incidentId) {
                mutableStateOf<DocumentSnapshot?>(null)
            }
            var loaded by remember(uid, incidentId) {
                mutableStateOf(false)
            }
            var showDialog by remember(uid, incidentId) {
                mutableStateOf(false)
            }
            var reason by remember(uid, incidentId) {
                mutableStateOf("")
            }
            var busy by remember(uid, incidentId) {
                mutableStateOf(false)
            }
            var error by remember(uid, incidentId) {
                mutableStateOf<String?>(null)
            }

            DisposableEffect(uid, incidentId) {
                val listener = db.collection("accountAppeals")
                    .document(uid)
                    .collection("incidents")
                    .document(incidentId)
                    .addSnapshotListener { document, failure ->
                        loaded = failure == null
                        appeal = document
                        error = failure?.localizedMessage
                    }

                onDispose { listener.remove() }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            when (appeal?.getString("status")) {
                "PENDING" -> Text("Your appeal is awaiting moderator review.")

                "ACCEPTED" -> {
                    Text("Appeal accepted. Tap Check again to restore access.")
                    Text(appeal?.getString("decisionReason").orEmpty())
                }

                "REJECTED" -> {
                    Text("Appeal rejected.")
                    Text(appeal?.getString("decisionReason").orEmpty())
                }

                else -> {
                    OutlinedButton(
                        enabled = loaded && !busy,
                        onClick = {
                            error = null
                            showDialog = true
                        }
                    ) {
                        Text("Appeal this decision")
                    }
                }
            }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {
                        if (!busy) showDialog = false
                    },
                    title = { Text("Appeal account restriction") },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Explain why this decision should be reconsidered.")

                            OutlinedTextField(
                                value = reason,
                                onValueChange = { reason = it },
                                label = { Text("Appeal reason") },
                                supportingText = {
                                    Text("10–1,000 characters")
                                },
                                enabled = !busy,
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            error?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !busy &&
                                    reason.trim().length in 10..1000,
                            onClick = {
                                busy = true
                                error = null

                                submit(uid, incidentId, reason) { failure ->
                                    busy = false
                                    error = failure

                                    if (failure == null) {
                                        showDialog = false
                                        reason = ""
                                    }
                                }
                            }
                        ) {
                            Text(if (busy) "Submitting…" else "Submit appeal")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !busy,
                            onClick = { showDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        @Composable
        public fun PendingList() {
            val auth = remember { FirebaseAuth.getInstance() }
            var moderatorUid by remember {
                mutableStateOf(auth.currentUser?.uid)
            }
            var appeals by remember {
                mutableStateOf<List<DocumentSnapshot>>(emptyList())
            }
            var loading by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            var selected by remember {
                mutableStateOf<DocumentSnapshot?>(null)
            }
            var accepting by remember { mutableStateOf(false) }
            var reason by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener {
                    moderatorUid = it.currentUser?.uid
                }
                auth.addAuthStateListener(listener)
                onDispose { auth.removeAuthStateListener(listener) }
            }

            DisposableEffect(moderatorUid) {
                appeals = emptyList()
                selected = null
                loading = moderatorUid != null
                error = null

                val listener = if (moderatorUid != null) {
                    db.collectionGroup("incidents")
                        .whereEqualTo("status", "PENDING")
                        .addSnapshotListener { snapshot, failure ->
                            loading = false
                            error = failure?.localizedMessage
                            appeals = snapshot?.documents.orEmpty()
                        }
                } else {
                    null
                }

                onDispose { listener?.remove() }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                if (loading) {
                    CircularProgressIndicator()
                } else if (appeals.isEmpty()) {
                    Text("No pending account appeals.")
                }

                appeals.forEach { appeal ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Account restriction appeal",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(appeal.getString("reason").orEmpty())

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selected = appeal
                                        accepting = true
                                        reason = ""
                                        error = null
                                    }
                                ) {
                                    Text("Accept")
                                }

                                OutlinedButton(
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selected = appeal
                                        accepting = false
                                        reason = ""
                                        error = null
                                    }
                                ) {
                                    Text("Reject")
                                }
                            }
                        }
                    }
                }
            }

            selected?.let { appeal ->
                AlertDialog(
                    onDismissRequest = {
                        if (!busy) selected = null
                    },
                    title = {
                        Text(if (accepting) "Accept appeal?" else "Reject appeal?")
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                if (accepting) {
                                    "This lifts the account restriction being " +
                                            "appealed. The moderation history remains."
                                } else {
                                    "The account restriction will remain in place."
                                }
                            )

                            OutlinedTextField(
                                value = reason,
                                onValueChange = { reason = it },
                                label = { Text("Decision reason") },
                                supportingText = {
                                    Text("10–1,000 characters")
                                },
                                enabled = !busy,
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            error?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !busy &&
                                    reason.trim().length in 10..1000,
                            onClick = {
                                busy = true
                                error = null

                                decide(
                                    appealRef = appeal.reference,
                                    accept = accepting,
                                    reason = reason,
                                    onDone = { failure ->
                                        busy = false
                                        error = failure
                                        if (failure == null) selected = null
                                    }
                                )
                            }
                        ) {
                            Text(if (busy) "Saving…" else "Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !busy,
                            onClick = { selected = null }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}