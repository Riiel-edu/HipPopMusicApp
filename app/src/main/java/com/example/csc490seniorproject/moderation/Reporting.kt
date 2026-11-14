package com.example.csc490seniorproject.moderation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.util.UUID

class Reporting {
    companion object {

        private val db: FirebaseFirestore
            get() = FirebaseFirestore.getInstance()

        private fun submitReport(
            targetUid: String,
            targetName: String,
            reason: String,
            onDone: (String?) -> Unit
        ) {
            val reporterUid =
                FirebaseAuth.getInstance().currentUser?.uid

            if (reporterUid == null || reporterUid == targetUid) {
                onDone("Sign in to report another account.")
                return
            }

            val queueRef = db.collection("reportQueues")
                .document(targetUid)

            val newCaseId = UUID.randomUUID().toString()

            db.runTransaction { transaction ->
                val queue = transaction.get(queueRef)

                val caseId =
                    queue.getString("caseId") ?: newCaseId

                val reportRef = queueRef
                    .collection("rounds")
                    .document(caseId)
                    .collection("reports")
                    .document(reporterUid)

                val previousReport = transaction.get(reportRef)

                check(!previousReport.exists()) {
                    "You already reported this account in the current case."
                }

                transaction.set(
                    queueRef,
                    mapOf(
                        "caseId" to caseId,
                        "reportCount" to
                                (queue.getLong("reportCount") ?: 0L) + 1,
                        "targetUsername" to (
                                queue.getString("targetUsername")
                                    ?: targetName.take(100)
                                )
                    )
                )

                transaction.set(
                    reportRef,
                    mapOf(
                        "reporterId" to reporterUid,
                        "targetUid" to targetUid,
                        "caseId" to caseId,
                        "reason" to reason.trim(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                null
            }
                .addOnSuccessListener {
                    onDone(null)
                }
                .addOnFailureListener {
                    onDone(
                        it.localizedMessage
                            ?: "Couldn't submit report."
                    )
                }
        }

        @Composable
        public fun ReportUserButton(
            targetUid: String?,
            targetUsername: String,
            trigger: (@Composable (() -> Unit) -> Unit)? = null
        ) {
            val auth = remember {
                FirebaseAuth.getInstance()
            }

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

            if (
                currentUid == null ||
                targetUid.isNullOrBlank() ||
                currentUid == targetUid
            ) {
                return
            }

            key(currentUid, targetUid) {
                var showDialog by remember {
                    mutableStateOf(false)
                }

                var reason by remember {
                    mutableStateOf("")
                }

                var busy by remember {
                    mutableStateOf(false)
                }

                var message by remember {
                    mutableStateOf<String?>(null)
                }

                val openReport: () -> Unit = {
                    showDialog = true
                    message = null
                }

                if (trigger != null) {
                    trigger(openReport)
                } else {
                    TextButton(onClick = openReport) {
                        Text("Report user")
                    }
                }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            if (!busy) showDialog = false
                        },
                        title = {
                            Text("Report $targetUsername")
                        },
                        text = {
                            Column(
                                verticalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Describe what happened and where. " +
                                            "Submitting a report does not " +
                                            "automatically suspend anyone."
                                )

                                OutlinedTextField(
                                    value = reason,
                                    onValueChange = {
                                        reason = it
                                    },
                                    label = {
                                        Text("Reason and evidence")
                                    },
                                    minLines = 3,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    "10–1,000 characters. " +
                                            "One report per account per case."
                                )

                                message?.let {
                                    Text(it)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !busy &&
                                        reason.trim().length in 10..1000,
                                onClick = {
                                    busy = true

                                    submitReport(
                                        targetUid = targetUid,
                                        targetName = targetUsername,
                                        reason = reason,
                                        onDone = { failure ->
                                            busy = false

                                            message = failure
                                                ?: "Report submitted for review."

                                            if (failure == null) {
                                                reason = ""
                                            }
                                        }
                                    )
                                }
                            ) {
                                Text(
                                    if (busy) {
                                        "Submitting…"
                                    } else {
                                        "Submit report"
                                    }
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    showDialog = false
                                }
                            ) {
                                Text("Close")
                            }
                        }
                    )
                }
            }
        }

        private fun reviewCase(
            targetUid: String,
            caseId: String,
            expectedCount: Long,
            confirmViolation: Boolean,
            reason: String,
            onDone: (String?) -> Unit
        ) {
            val moderatorUid =
                FirebaseAuth.getInstance().currentUser?.uid

            if (moderatorUid == null) {
                onDone("Please sign in.")
                return
            }

            val queueRef = db.collection("reportQueues")
                .document(targetUid)

            val reviewRef = queueRef
                .collection("reviews")
                .document(caseId)

            val moderationRef = db.collection("accountModeration")
                .document(targetUid)

            val auditRef = moderationRef
                .collection("violations")
                .document(caseId)

            val nextCaseId = UUID.randomUUID().toString()

            db.runTransaction { transaction ->
                val previousReview = transaction.get(reviewRef)
                val queue = transaction.get(queueRef)
                val moderation = transaction.get(moderationRef)

                // Retrying an already-completed decision does not
                // create another suspension.
                if (!previousReview.exists()) {
                    check(queue.getString("caseId") == caseId) {
                        "This case has already closed. Refresh the queue."
                    }

                    check(
                        (queue.getLong("reportCount") ?: 0L) >= 4L
                    ) {
                        "Four distinct reports are required."
                    }

                    val count =
                        moderation.getLong("violationCount") ?: 0L

                    check(
                        !confirmViolation || count == expectedCount
                    ) {
                        "The account changed. Review it again."
                    }

                    check(!confirmViolation || count < 4) {
                        "This account is already permanently banned."
                    }

                    transaction.set(
                        reviewRef,
                        mapOf(
                            "caseId" to caseId,
                            "decision" to if (confirmViolation) {
                                "CONFIRMED"
                            } else {
                                "DISMISSED"
                            },
                            "reason" to reason.trim(),
                            "moderatorId" to moderatorUid,
                            "reportCount" to
                                    queue.getLong("reportCount"),
                            "createdAt" to
                                    FieldValue.serverTimestamp()
                        )
                    )

                    // Start a fresh case. Old reports stay in their
                    // original case and cannot trigger another action.
                    transaction.set(
                        queueRef,
                        mapOf(
                            "caseId" to nextCaseId,
                            "reportCount" to 0L,
                            "targetUsername" to
                                    queue.getString("targetUsername").orEmpty()
                        )
                    )

                    if (confirmViolation) {
                        val record = mapOf(
                            "violationCount" to count + 1,
                            "reason" to reason.trim(),
                            "moderatorId" to moderatorUid,
                            "incidentId" to caseId,
                            "startedAt" to
                                    FieldValue.serverTimestamp()
                        )

                        transaction.set(moderationRef, record)
                        transaction.set(auditRef, record)
                    }
                }

                null
            }
                .addOnSuccessListener {
                    onDone(null)
                }
                .addOnFailureListener {
                    onDone(
                        it.localizedMessage
                            ?: "Couldn't review case."
                    )
                }
        }

        @Composable
        public fun ModeratorReviewScreen(
            onBack: () -> Unit
        ) {
            val auth = remember {
                FirebaseAuth.getInstance()
            }

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
                ReviewContent(
                    moderatorUid = currentUid,
                    onBack = onBack
                )
            }
        }

        @Composable
        private fun ReviewContent(
            moderatorUid: String?,
            onBack: () -> Unit
        ) {
            var authorized by remember {
                mutableStateOf(false)
            }

            var roleLoaded by remember {
                mutableStateOf(false)
            }

            var queues by remember {
                mutableStateOf<List<DocumentSnapshot>>(emptyList())
            }

            var loading by remember {
                mutableStateOf(true)
            }

            var selectedCase by remember {
                mutableStateOf<DocumentSnapshot?>(null)
            }

            var reports by remember {
                mutableStateOf<List<DocumentSnapshot>>(emptyList())
            }

            var history by remember {
                mutableStateOf<List<DocumentSnapshot>>(emptyList())
            }

            var moderation by remember {
                mutableStateOf<DocumentSnapshot?>(null)
            }

            var detailsLoaded by remember {
                mutableStateOf(false)
            }

            var reason by remember {
                mutableStateOf("")
            }

            var busy by remember {
                mutableStateOf(false)
            }

            var error by remember {
                mutableStateOf<String?>(null)
            }

            // true = confirm violation; false = dismiss case.
            var decisionDialog by remember {
                mutableStateOf<Boolean?>(null)
            }

            var confirmedCount by remember {
                mutableLongStateOf(0L)
            }

            DisposableEffect(moderatorUid) {
                var active = true

                val listener = moderatorUid?.let {
                    db.collection("moderators")
                        .document(it)
                        .addSnapshotListener { document, failure ->
                            if (active) {
                                roleLoaded = true
                                authorized =
                                    failure == null &&
                                            document?.getBoolean("enabled") == true
                            }
                        }
                }

                if (moderatorUid == null) {
                    roleLoaded = true
                }

                onDispose {
                    active = false
                    listener?.remove()
                }
            }

            DisposableEffect(authorized) {
                var active = true

                val listener = if (authorized) {
                    db.collection("reportQueues")
                        .whereGreaterThanOrEqualTo(
                            "reportCount",
                            4L
                        )
                        .addSnapshotListener { snapshot, failure ->
                            if (active) {
                                loading = false

                                if (failure != null) {
                                    error = failure.localizedMessage
                                } else {
                                    queues = snapshot?.documents.orEmpty()
                                }
                            }
                        }
                } else {
                    null
                }

                onDispose {
                    active = false
                    listener?.remove()
                }
            }

            DisposableEffect(
                selectedCase?.id,
                selectedCase?.getString("caseId"),
                authorized
            ) {
                var active = true

                detailsLoaded = false
                reports = emptyList()
                history = emptyList()
                moderation = null
                reason = ""

                val selected = selectedCase
                val caseId = selected?.getString("caseId")

                val listeners =
                    mutableListOf<ListenerRegistration>()

                if (
                    authorized &&
                    selected != null &&
                    caseId != null
                ) {
                    val queueRef = db.collection("reportQueues")
                        .document(selected.id)

                    listeners += queueRef
                        .collection("rounds")
                        .document(caseId)
                        .collection("reports")
                        .addSnapshotListener { snapshot, failure ->
                            if (active) {
                                if (failure != null) {
                                    error = failure.localizedMessage
                                } else {
                                    reports =
                                        snapshot?.documents.orEmpty()
                                }
                            }
                        }

                    listeners += db
                        .collection("accountModeration")
                        .document(selected.id)
                        .addSnapshotListener(
                            MetadataChanges.INCLUDE
                        ) { document, failure ->
                            if (active) {
                                detailsLoaded =
                                    failure == null &&
                                            document != null &&
                                            !document.metadata.isFromCache

                                moderation = document

                                if (failure != null) {
                                    error = failure.localizedMessage
                                }
                            }
                        }

                    listeners += queueRef
                        .collection("reviews")
                        .orderBy(
                            "createdAt",
                            Query.Direction.DESCENDING
                        )
                        .limit(10)
                        .addSnapshotListener { snapshot, failure ->
                            if (active) {
                                if (failure != null) {
                                    error = failure.localizedMessage
                                } else {
                                    history =
                                        snapshot?.documents.orEmpty()
                                }
                            }
                        }
                }

                onDispose {
                    active = false
                    listeners.forEach { it.remove() }
                }
            }

            val count =
                moderation?.getLong("violationCount") ?: 0L

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = !busy
                ) {
                    Text("Back")
                }

                Text(
                    text = "Report review",
                    style = MaterialTheme.typography.headlineSmall
                )

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!roleLoaded) {
                    CircularProgressIndicator()
                } else if (!authorized) {
                    Text("Moderator access required.")
                } else if (selectedCase == null) {
                    Text(
                        "Cases with at least four different reporters. " +
                                "Reports do not automatically suspend accounts."
                    )

                    if (loading) {
                        CircularProgressIndicator()
                    } else if (queues.isEmpty()) {
                        Text("No cases awaiting review.")
                    }

                    queues.forEach { queue ->
                        OutlinedButton(
                            onClick = {
                                selectedCase = queue
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${queue.getString("targetUsername").orEmpty()}" +
                                        " — ${queue.getLong("reportCount")} reports\n" +
                                        "UID: ${queue.id}"
                            )
                        }
                    }
                } else {
                    val selected = selectedCase!!

                    TextButton(
                        enabled = !busy,
                        onClick = {
                            selectedCase = null
                            error = null
                        }
                    ) {
                        Text("Back to queue")
                    }

                    Text(
                        "Account: ${
                            selected.getString("targetUsername").orEmpty()
                        }\nUID: ${selected.id}"
                    )

                    Text("Previous confirmed rounds: $count / 4")

                    Text(
                        "Check the evidence and previous reviews. " +
                                "Do not count an already-handled incident again."
                    )

                    reports.forEach { report ->
                        HorizontalDivider()

                        Text(
                            text = "Reporter: ${report.id}",
                            style = MaterialTheme.typography.labelMedium
                        )

                        Text(
                            report.getString("reason").orEmpty()
                        )
                    }

                    if (!detailsLoaded) {
                        CircularProgressIndicator()
                    }

                    OutlinedTextField(
                        value = reason,
                        onValueChange = {
                            reason = it
                        },
                        label = {
                            Text("Review decision and reason")
                        },
                        supportingText = {
                            Text("Required: 10–1,000 characters")
                        },
                        enabled = !busy,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled =
                            !busy &&
                                    detailsLoaded &&
                                    reports.size >= 4 &&
                                    count < 4 &&
                                    reason.trim().length in 10..1000,
                        onClick = {
                            confirmedCount = count
                            decisionDialog = true
                        }
                    ) {
                        Text(
                            if (count == 3L) {
                                "Confirm fourth round — permanent ban"
                            } else {
                                "Confirm violation — suspend 15 days"
                            }
                        )
                    }

                    OutlinedButton(
                        enabled =
                            !busy &&
                                    detailsLoaded &&
                                    reports.size >= 4 &&
                                    reason.trim().length in 10..1000,
                        onClick = {
                            confirmedCount = count
                            decisionDialog = false
                        }
                    ) {
                        Text("Dismiss case")
                    }

                    if (history.isNotEmpty()) {
                        Text(
                            text = "Previous reviews",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    history.forEach { review ->
                        HorizontalDivider()

                        Text(
                            "${review.getString("decision")}: " +
                                    review.getString("reason").orEmpty()
                        )

                        Text(
                            text = "Moderator: ${
                                review.getString("moderatorId").orEmpty()
                            }",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            decisionDialog?.let { confirmViolation ->
                AlertDialog(
                    onDismissRequest = {
                        if (!busy) decisionDialog = null
                    },
                    title = {
                        Text(
                            when {
                                !confirmViolation ->
                                    "Dismiss this case?"

                                confirmedCount == 3L ->
                                    "Permanently ban this account?"

                                else ->
                                    "Suspend for 15 days?"
                            }
                        )
                    },
                    text = {
                        Text(
                            "${reason.trim()}\n\n" +
                                    "This closes the current case. Its reports " +
                                    "will not count toward the next case."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                val selected = selectedCase
                                val caseId =
                                    selected?.getString("caseId")

                                if (
                                    selected != null &&
                                    caseId != null
                                ) {
                                    busy = true

                                    reviewCase(
                                        targetUid = selected.id,
                                        caseId = caseId,
                                        expectedCount = confirmedCount,
                                        confirmViolation = confirmViolation,
                                        reason = reason,
                                        onDone = { failure ->
                                            busy = false
                                            decisionDialog = null

                                            if (failure == null) {
                                                selectedCase = null
                                                error = null
                                            } else {
                                                error = failure
                                            }
                                        }
                                    )
                                }
                            }
                        ) {
                            Text(
                                if (busy) {
                                    "Saving…"
                                } else {
                                    "Confirm decision"
                                }
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                decisionDialog = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}