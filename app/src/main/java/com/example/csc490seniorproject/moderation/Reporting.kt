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
            evidence: String,
            onDone: (String?) -> Unit
        ) {
            val reporterUid = FirebaseAuth.getInstance().currentUser?.uid

            if (reporterUid == null || reporterUid == targetUid) {
                onDone("Sign in to report another account.")
                return
            }

            if (reason.trim().length !in 10..100) {
                onDone("Enter a reason between 10 and 100 characters.")
                return
            }

            // Matches the evidence requirements in your supplied rules.
            if (evidence.trim().length !in 10..2000) {
                onDone("Enter evidence between 10 and 2,000 characters.")
                return
            }

            val queueRef = db.collection("reportQueues").document(targetUid)
            val newCaseId = UUID.randomUUID().toString()

            db.runTransaction { transaction ->
                val queue = transaction.get(queueRef)
                val caseId = queue.getString("caseId") ?: newCaseId

                val reportRef = queueRef
                    .collection("rounds").document(caseId)
                    .collection("reports").document(reporterUid)

                val previousReport = transaction.get(reportRef)

                check(!previousReport.exists()) {
                    "You already reported this account in the current case."
                }

                transaction.set(
                    queueRef,
                    mapOf(
                        "caseId" to caseId,
                        "reportCount" to (queue.getLong("reportCount") ?: 0L) + 1L,
                        "targetUsername" to (queue.getString("targetUsername") ?: targetName.take(100))
                    )
                )

                transaction.set(
                    reportRef,
                    mapOf(
                        "reporterId" to reporterUid,
                        "targetUid" to targetUid,
                        "caseId" to caseId,
                        "reason" to reason.trim(),
                        "evidence" to evidence.trim(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                null
            }.addOnSuccessListener {
                onDone(null)
            }.addOnFailureListener {
                onDone(it.localizedMessage ?: "Couldn't submit report.")
            }
        }

        @Composable
        public fun ReportUserButton(
            targetUid: String?,
            targetUsername: String,
            trigger: (@Composable (() -> Unit) -> Unit)? = null
        ) {
            val auth = remember { FirebaseAuth.getInstance() }
            var currentUid by remember {
                mutableStateOf(auth.currentUser?.uid)
            }

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener {
                    currentUid = it.currentUser?.uid
                }
                auth.addAuthStateListener(listener)
                onDispose { auth.removeAuthStateListener(listener) }
            }

            val target = targetUid
            if (
                currentUid == null ||
                target.isNullOrBlank() ||
                currentUid == target
            ) {
                return
            }

            key(currentUid, target) {
                var showDialog by remember { mutableStateOf(false) }
                var reason by remember { mutableStateOf("") }
                var evidence by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf<String?>(null) }

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
                        title = { Text("Report $targetUsername") },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Describe what happened. Reports do not " + "automatically suspend an account."
                                )

                                OutlinedTextField(
                                    value = reason,
                                    onValueChange = { reason = it },
                                    label = { Text("Reason") },
                                    supportingText = {
                                        Text("10–100 characters")
                                    },
                                    enabled = !busy,
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = evidence,
                                    onValueChange = { evidence = it },
                                    label = { Text("Evidence") },
                                    supportingText = {
                                        Text("10–2,000 characters")
                                    },
                                    enabled = !busy,
                                    minLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                message?.let { Text(it) }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !busy && reason.trim().length in 10..100 && evidence.trim().length in 10..2000,
                                onClick = {
                                    busy = true
                                    message = null

                                    submitReport(
                                        targetUid = target,
                                        targetName = targetUsername,
                                        reason = reason,
                                        evidence = evidence,
                                        onDone = { failure ->
                                            busy = false
                                            message = failure ?: "Report submitted for review."

                                            if (failure == null) {
                                                reason = ""
                                                evidence = ""
                                            }
                                        }
                                    )
                                }
                            ) {
                                Text(if (busy) "Submitting…" else "Submit report")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !busy,
                                onClick = { showDialog = false }
                            ) {
                                Text("Close")
                            }
                        }
                    )
                }
            }
        }

        public fun dismissReportCase(
            targetUid: String,
            caseId: String,
            reason: String,
            onDone: (String?) -> Unit
        ) {
            reviewCase(
                targetUid = targetUid,
                caseId = caseId,
                expectedCount = 0L,
                confirmViolation = false,
                reason = reason,
                onDone = onDone
            )
        }

        private fun reviewCase(
            targetUid: String,
            caseId: String,
            expectedCount: Long,
            confirmViolation: Boolean,
            reason: String,
            onDone: (String?) -> Unit,
            permanentBan: Boolean = false
        ) {
            val moderatorUid = FirebaseAuth.getInstance().currentUser?.uid

            if (moderatorUid == null) {
                onDone("Please sign in.")
                return
            }

            if (targetUid == moderatorUid) {
                onDone("You cannot moderate your own account.")
                return
            }

            if (reason.trim().length !in 10..1000) {
                onDone("Enter a reason between 10 and 1,000 characters.")
                return
            }

            val roleRef = db.collection("moderators").document(moderatorUid)
            val queueRef = db.collection("reportQueues").document(targetUid)
            val reviewRef = queueRef.collection("reviews").document(caseId)

            val moderationRef = db.collection("accountModeration").document(targetUid)

            val auditRef = moderationRef.collection("violations").document(caseId)

            val nextCaseId = UUID.randomUUID().toString()

            db.runTransaction { transaction ->
                // Complete all reads before writing.
                val role = transaction.get(roleRef)
                val previousReview = transaction.get(reviewRef)
                val queue = transaction.get(queueRef)
                val moderation = transaction.get(moderationRef)

                check(role.getBoolean("enabled") == true) {
                    "Moderator access required."
                }

                // A retried completed decision must not suspend twice.
                if (!previousReview.exists()) {
                    check(
                        queue.exists() && queue.getString("caseId") == caseId
                    ) {
                        "This case has already closed. Refresh the queue."
                    }

                    val reportCount = queue.getLong("reportCount") ?: 0L

                    check(reportCount >= 1L) {
                        "This case has no reports."
                    }

                    val count = moderation.getLong("violationCount") ?: 0L

                    check(!confirmViolation || count == expectedCount) {
                        "The account changed. Review it again."
                    }

                    check(!confirmViolation || count < 4L) {
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
                            "reportCount" to reportCount,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )

                    transaction.set(
                        queueRef,
                        mapOf(
                            "caseId" to nextCaseId,
                            "reportCount" to 0L,
                            "targetUsername" to queue.getString("targetUsername").orEmpty()
                        )
                    )

                    if (confirmViolation) {
                        // Existing account gate treats 4 as permanently banned.
                        val nextCount = if (permanentBan) 4L else count + 1L

                        val record = mapOf(
                            "violationCount" to nextCount,
                            "reason" to reason.trim(),
                            "moderatorId" to moderatorUid,
                            "incidentId" to caseId,
                            "startedAt" to FieldValue.serverTimestamp()
                        )

                        transaction.set(moderationRef, record)
                        transaction.set(auditRef, record)
                    }
                }

                null
            }.addOnSuccessListener {
                onDone(null)
            }.addOnFailureListener {
                onDone(it.localizedMessage ?: "Couldn't save decision.")
            }
        }

        @Composable
        public fun ModeratorReviewScreen(
            onBack: () -> Unit
        ) {
            val auth = remember { FirebaseAuth.getInstance() }
            var currentUid by remember {
                mutableStateOf(auth.currentUser?.uid)
            }

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener {
                    currentUid = it.currentUser?.uid
                }
                auth.addAuthStateListener(listener)
                onDispose { auth.removeAuthStateListener(listener) }
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
            var authorized by remember { mutableStateOf(false) }
            var roleLoaded by remember { mutableStateOf(false) }
            var loading by remember { mutableStateOf(true) }

            var queues by remember {
                mutableStateOf<List<DocumentSnapshot>>(emptyList())
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

            var detailsLoaded by remember { mutableStateOf(false) }
            var reportsLoaded by remember { mutableStateOf(false) }
            var reason by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }

            // null = closed; false = dismiss; true = confirm violation.
            var decisionDialog by remember {
                mutableStateOf<Boolean?>(null)
            }
            var permanentBanRequested by remember { mutableStateOf(false) }
            var confirmedCount by remember { mutableStateOf(0L) }

            DisposableEffect(moderatorUid) {
                var active = true

                val listener = moderatorUid?.let { uid ->
                    db.collection("moderators").document(uid)
                        .addSnapshotListener { document, failure ->
                            if (active) {
                                roleLoaded = true
                                authorized = failure == null && document?.getBoolean("enabled") == true

                                if (failure != null) {
                                    error = failure.localizedMessage
                                }
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
                queues = emptyList()
                loading = authorized

                val listener = if (authorized) {
                    db.collection("reportQueues")
                        .whereGreaterThanOrEqualTo("reportCount", 1L)
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
                reportsLoaded = false
                reports = emptyList()
                history = emptyList()
                moderation = null
                reason = ""
                decisionDialog = null
                permanentBanRequested = false

                val selected = selectedCase
                val caseId = selected?.getString("caseId")
                val listeners = mutableListOf<ListenerRegistration>()

                if (authorized && selected != null && caseId != null) {
                    val queueRef = db.collection("reportQueues")
                        .document(selected.id)

                    listeners += queueRef
                        .collection("rounds").document(caseId)
                        .collection("reports")
                        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                            if (active) {
                                reportsLoaded = failure == null &&
                                        snapshot != null &&
                                        !snapshot.metadata.isFromCache

                                if (failure != null) {
                                    error = failure.localizedMessage
                                } else {
                                    reports = snapshot?.documents.orEmpty()
                                }
                            }
                        }

                    listeners += db.collection("accountModeration")
                        .document(selected.id)
                        .addSnapshotListener(MetadataChanges.INCLUDE) { document, failure ->
                            if (active) {
                                detailsLoaded = failure == null &&
                                        document != null &&
                                        !document.metadata.isFromCache

                                moderation = document

                                if (failure != null) {
                                    error = failure.localizedMessage
                                }
                            }
                        }

                    listeners += queueRef.collection("reviews")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(10)
                        .addSnapshotListener { snapshot, failure ->
                            if (active) {
                                if (failure != null) {
                                    error = failure.localizedMessage
                                } else {
                                    history = snapshot?.documents.orEmpty()
                                }
                            }
                        }
                }

                onDispose {
                    active = false
                    listeners.forEach { it.remove() }
                }
            }

            val count = moderation?.getLong("violationCount") ?: 0L
            val ready = authorized &&
                    !busy &&
                    detailsLoaded &&
                    reportsLoaded &&
                    reports.isNotEmpty() &&
                    reason.trim().length in 10..1000

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    enabled = !busy,
                    onClick = onBack
                ) {
                    Text("Back")
                }

                Text(
                    "Report review",
                    style = MaterialTheme.typography.headlineSmall
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                when {
                    !roleLoaded -> CircularProgressIndicator()

                    !authorized -> Text("Moderator access required.")

                    selectedCase == null -> {
                        Text(
                            "Moderators can act after one report. " + "Reports never automatically suspend accounts."
                        )

                        if (loading) {
                            CircularProgressIndicator()
                        } else if (queues.isEmpty()) {
                            Text("No cases awaiting review.")
                        }

                        queues.forEach { queue ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    selectedCase = queue
                                    error = null
                                }
                            ) {
                                Text(
                                    queue.getString("targetUsername").orEmpty() + " — ${queue.getLong("reportCount")} reports" +
                                            if (
                                                (queue.getLong("reportCount") ?: 0L) >= 4L
                                            ) {
                                                "\nFlagged: four or more reports"
                                            } else {
                                                ""
                                            }
                                )
                            }
                        }
                    }

                    else -> {
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
                            "Account: " + selected.getString("targetUsername").orEmpty()
                        )

                        Text(
                            if (count >= 4L) {
                                "Account status: permanently banned"
                            } else {
                                "Previous confirmed rounds: $count"
                            }
                        )

                        Text(
                            "Review the evidence before deciding. " + "Do not count an already-handled incident again."
                        )

                        reports.forEachIndexed { index, report ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Report ${index + 1}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text("Reason: " + report.getString("reason").orEmpty())
                                    Text("Evidence: " + (report.getString("evidence")
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?: "No evidence provided.")
                                    )
                                }
                            }
                        }

                        if (!detailsLoaded || !reportsLoaded) {
                            CircularProgressIndicator()
                        }

                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Decision reason") },
                            supportingText = {
                                Text("Required: 10–1,000 characters")
                            },
                            enabled = !busy,
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            enabled = ready && count < 4L,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                permanentBanRequested = false
                                confirmedCount = count
                                decisionDialog = true
                            }
                        ) {
                            Text(
                                if (count == 3L) {
                                    "Confirm fourth violation — permanent ban"
                                } else {
                                    "Confirm violation — suspend 15 days"
                                }
                            )
                        }

                        Button(
                            enabled = ready && count < 4L,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            onClick = {
                                permanentBanRequested = true
                                confirmedCount = count
                                decisionDialog = true
                            }
                        ) {
                            Text("Permanently ban account now")
                        }

                        OutlinedButton(
                            enabled = ready,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                permanentBanRequested = false
                                confirmedCount = count
                                decisionDialog = false
                            }
                        ) {
                            Text("Dismiss case")
                        }

                        if (history.isNotEmpty()) {
                            Text("Previous reviews", style = MaterialTheme.typography.titleMedium)
                        }

                        history.forEach { review ->
                            HorizontalDivider()
                            Text("${review.getString("decision")}: " + review.getString("reason").orEmpty())
                            Text( "Moderator: " + review.getString("moderatorId").orEmpty(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            decisionDialog?.let { confirmViolation ->
                val willBan = confirmViolation &&
                        (permanentBanRequested || confirmedCount == 3L)

                AlertDialog(
                    onDismissRequest = {
                        if (!busy) decisionDialog = null
                    },
                    title = {
                        Text(
                            when {
                                !confirmViolation -> "Dismiss this case?"
                                willBan -> "Permanently ban this account?"
                                else -> "Suspend this account for 15 days?"
                            }
                        )
                    },
                    text = {
                        Text(
                            reason.trim() + "\n\nThis closes the current case. Its reports " +
                                    "will not count toward another decision."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !busy && authorized,
                            onClick = {
                                val selected = selectedCase
                                val caseId = selected?.getString("caseId")

                                if (selected != null && caseId != null) {
                                    busy = true
                                    error = null

                                    reviewCase(
                                        targetUid = selected.id,
                                        caseId = caseId,
                                        expectedCount = confirmedCount,
                                        confirmViolation = confirmViolation,
                                        reason = reason,
                                        permanentBan = permanentBanRequested,
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
                            Text(if (busy) "Saving…" else "Confirm decision")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !busy,
                            onClick = { decisionDialog = null }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}