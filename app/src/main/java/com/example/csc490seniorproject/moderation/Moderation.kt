package com.example.csc490seniorproject.moderation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class Moderation {
    companion object {

        private const val FIFTEEN_DAYS_MS =
            15L * 24 * 60 * 60 * 1000

        private fun suspensionEndDate(start: Timestamp?): String {
            return start?.let {
                DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT
                ).format(
                    Date(it.toDate().time + FIFTEEN_DAYS_MS)
                )
            } ?: "Waiting for server date"
        }

        @Composable
        private fun rememberAccountUid(): String? {
            val auth = remember {
                FirebaseAuth.getInstance()
            }

            var uid by remember {
                mutableStateOf(auth.currentUser?.uid)
            }

            DisposableEffect(auth) {
                val listener = FirebaseAuth.AuthStateListener {
                    uid = it.currentUser?.uid
                }

                auth.addAuthStateListener(listener)

                onDispose {
                    auth.removeAuthStateListener(listener)
                }
            }

            return uid
        }

        @Composable
        private fun rememberModerator(uid: String?): Boolean {
            var moderator by remember(uid) {
                mutableStateOf(false)
            }

            DisposableEffect(uid) {
                var active = true

                val listener = uid?.let {
                    FirebaseFirestore.getInstance()
                        .collection("moderators")
                        .document(it)
                        .addSnapshotListener { document, error ->
                            if (active) {
                                moderator =
                                    error == null &&
                                            document?.getBoolean("enabled") == true
                            }
                        }
                }

                onDispose {
                    active = false
                    listener?.remove()
                }
            }

            return moderator
        }

        @Composable
        public fun ModeratorSettingsEntry(
            onClick: () -> Unit
        ) {
            val uid = rememberAccountUid()

            if (rememberModerator(uid)) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Moderator tools")
                }
            }
        }

        @Composable
        public fun ModerationGate(
            lifecycle: Lifecycle,
            bypass: Boolean = false,
            content: @Composable () -> Unit
        ) {
            val uid = rememberAccountUid()

            if (uid == null || bypass) {
                content()
                return
            }

            SignedInAccountGate(
                uid = uid,
                lifecycle = lifecycle,
                content = content
            )
        }

        @Composable
        private fun SignedInAccountGate(
            uid: String,
            lifecycle: Lifecycle,
            content: @Composable () -> Unit
        ) {
            val db = remember {
                FirebaseFirestore.getInstance()
            }

            var moderation by remember(uid) {
                mutableStateOf<DocumentSnapshot?>(null)
            }

            var allowed by remember(uid) {
                mutableStateOf(false)
            }

            var checking by remember(uid) {
                mutableStateOf(true)
            }

            var error by remember(uid) {
                mutableStateOf<String?>(null)
            }

            var refresh by remember(uid) {
                mutableIntStateOf(0)
            }

            var probe by remember(uid) {
                mutableIntStateOf(0)
            }

            DisposableEffect(lifecycle, uid) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            refresh++
                        }

                        Lifecycle.Event.ON_STOP -> {
                            allowed = false
                            checking = true
                        }

                        else -> Unit
                    }
                }

                lifecycle.addObserver(observer)

                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(uid, lifecycle) {
                while (true) {
                    delay(30_000)

                    if (
                        lifecycle.currentState.isAtLeast(
                            Lifecycle.State.STARTED
                        )
                    ) {
                        probe++
                    }
                }
            }

            DisposableEffect(uid, refresh) {
                var active = true

                allowed = false
                checking = true
                error = null

                val listener = db.collection("accountModeration")
                    .document(uid)
                    .addSnapshotListener(
                        MetadataChanges.INCLUDE
                    ) { document, failure ->
                        if (
                            active &&
                            FirebaseAuth.getInstance()
                                .currentUser?.uid == uid
                        ) {
                            if (failure != null) {
                                allowed = false
                                checking = false

                                error =
                                    "Couldn't verify your account. " +
                                            "Connect to the internet and retry."
                            } else if (
                                document != null &&
                                !document.metadata.isFromCache
                            ) {
                                moderation = document
                                allowed = false
                                checking = true
                                probe++
                            }
                        }
                    }

                onDispose {
                    active = false
                    listener.remove()
                }
            }

            DisposableEffect(uid, probe, refresh) {
                var active = true

                // Firebase rules decide whether this read is allowed.
                // No accessChecks documents need to be created.
                db.collection("accessChecks")
                    .document(uid)
                    .get(Source.SERVER)
                    .addOnSuccessListener {
                        if (
                            active &&
                            FirebaseAuth.getInstance()
                                .currentUser?.uid == uid
                        ) {
                            allowed = true
                            checking = false
                            error = null
                        }
                    }
                    .addOnFailureListener { failure ->
                        if (
                            active &&
                            FirebaseAuth.getInstance()
                                .currentUser?.uid == uid
                        ) {
                            allowed = false
                            checking = false

                            error = if (
                                failure is FirebaseFirestoreException &&
                                failure.code ==
                                FirebaseFirestoreException.Code.PERMISSION_DENIED
                            ) {
                                null
                            } else {
                                "Couldn't verify your account. " +
                                        "Connect to the internet and retry."
                            }
                        }
                    }

                onDispose {
                    active = false
                }
            }

            if (allowed && !checking) {
                content()
                return
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (checking) {
                        CircularProgressIndicator()

                        Spacer(Modifier.height(16.dp))

                        Text("Checking account access…")
                    } else {
                        val count =
                            moderation?.getLong("violationCount") ?: 0L

                        Text(
                            text = when {
                                error != null ->
                                    "Unable to verify account"

                                count >= 4 ->
                                    "Account permanently banned"

                                count > 0 ->
                                    "Account suspended"

                                else ->
                                    "Account access unavailable"
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            error
                                ?: moderation?.getString("reason")
                                ?: "Please retry to verify your account."
                        )

                        if (error == null && count in 1L..3L) {
                            Spacer(Modifier.height(12.dp))

                            Text(
                                "Access returns: ${
                                    suspensionEndDate(
                                        moderation?.getTimestamp("startedAt")
                                    )
                                }"
                            )
                        }

                        if (count > 0) {
                            Text("Confirmed violations: $count / 4")
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { refresh++ }
                        ) {
                            Text("Check again")
                        }
                    }

                    TextButton(
                        onClick = {
                            FirebaseAuth.getInstance().signOut()
                        }
                    ) {
                        Text("Log out")
                    }
                }
            }
        }

        private fun recordViolation(
            moderatorUid: String,
            targetUid: String,
            incidentId: String,
            expectedCount: Long,
            reason: String,
            onDone: (String?) -> Unit
        ) {
            val db = FirebaseFirestore.getInstance()

            val moderationRef = db.collection("accountModeration")
                .document(targetUid)

            val historyRef = moderationRef
                .collection("violations")
                .document(incidentId)

            db.runTransaction { transaction ->
                val previousIncident = transaction.get(historyRef)
                val previousModeration = transaction.get(moderationRef)

                // Do not count a retried submission twice.
                if (!previousIncident.exists()) {
                    val count =
                        previousModeration.getLong("violationCount") ?: 0L

                    check(count == expectedCount) {
                        "Another moderator changed this account. " +
                                "Review its latest history and confirm again."
                    }

                    check(count < 3) {
                        "This account is already permanently banned."
                    }

                    val record = mapOf(
                        "violationCount" to count + 1,
                        "reason" to reason.trim(),
                        "moderatorId" to moderatorUid,
                        "incidentId" to incidentId,
                        "startedAt" to FieldValue.serverTimestamp()
                    )

                    transaction.set(moderationRef, record)
                    transaction.set(historyRef, record)
                }

                null
            }
                .addOnSuccessListener {
                    onDone(null)
                }
                .addOnFailureListener {
                    onDone(
                        it.localizedMessage
                            ?: "Couldn't record the violation."
                    )
                }
        }

        @Composable
        public fun ModeratorScreen(
            onBack: () -> Unit
        ) {
            Reporting.ModeratorReviewScreen(onBack = onBack)
        }

        @Composable
        private fun ModeratorContent(
            moderatorUid: String,
            onBack: () -> Unit
        ) {
            val db = remember {
                FirebaseFirestore.getInstance()
            }

            var username by remember {
                mutableStateOf("")
            }

            var selectedUsername by remember {
                mutableStateOf("")
            }

            var targetUid by remember {
                mutableStateOf<String?>(null)
            }

            var reason by remember {
                mutableStateOf("")
            }

            var moderation by remember {
                mutableStateOf<DocumentSnapshot?>(null)
            }

            var history by remember {
                mutableStateOf<List<DocumentSnapshot>>(emptyList())
            }

            var loaded by remember {
                mutableStateOf(false)
            }

            var busy by remember {
                mutableStateOf(false)
            }

            var error by remember {
                mutableStateOf<String?>(null)
            }

            var success by remember {
                mutableStateOf<String?>(null)
            }

            var showConfirmation by remember {
                mutableStateOf(false)
            }

            var confirmedCount by remember {
                mutableLongStateOf(0L)
            }

            var incidentId by remember {
                mutableStateOf(UUID.randomUUID().toString())
            }

            DisposableEffect(targetUid) {
                var active = true

                loaded = false
                moderation = null
                history = emptyList()

                val reference = targetUid?.let {
                    db.collection("accountModeration")
                        .document(it)
                }

                val statusListener = reference?.addSnapshotListener(
                    MetadataChanges.INCLUDE
                ) { document, failure ->
                    if (active) {
                        loaded =
                            failure == null &&
                                    document != null &&
                                    !document.metadata.isFromCache

                        moderation = document

                        if (failure != null) {
                            error = failure.localizedMessage
                        }
                    }
                }

                val historyListener = reference
                    ?.collection("violations")
                    ?.orderBy(
                        "startedAt",
                        Query.Direction.DESCENDING
                    )
                    ?.limit(10)
                    ?.addSnapshotListener { snapshot, failure ->
                        if (active) {
                            if (failure != null) {
                                error = failure.localizedMessage
                            } else {
                                history = snapshot?.documents.orEmpty()
                            }
                        }
                    }

                onDispose {
                    active = false
                    statusListener?.remove()
                    historyListener?.remove()
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
                    text = "Moderator tools",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    "First and second violations: 15-day suspension. " +
                            "Third violation: permanent ban."
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        targetUid = null
                        loaded = false
                        error = null
                        success = null
                    },
                    label = {
                        Text("Exact login username")
                    },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = !busy && username.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        success = null
                        targetUid = null

                        val enteredUsername = username.trim()

                        if (enteredUsername.contains('/')) {
                            busy = false
                            error = "Enter a valid login username."
                        } else {
                            db.collection("usernames")
                                .document(
                                    enteredUsername.lowercase(Locale.ROOT)
                                )
                                .get(Source.SERVER)
                                .addOnSuccessListener { document ->
                                    busy = false

                                    if (
                                        FirebaseAuth.getInstance()
                                            .currentUser?.uid == moderatorUid
                                    ) {
                                        val foundUid =
                                            document.getString("uid")

                                        when {
                                            foundUid == null -> {
                                                error = "User not found."
                                            }

                                            foundUid == moderatorUid -> {
                                                error =
                                                    "You cannot moderate " +
                                                            "your own account."
                                            }

                                            else -> {
                                                selectedUsername =
                                                    enteredUsername

                                                targetUid = foundUid
                                                reason = ""

                                                incidentId =
                                                    UUID.randomUUID()
                                                        .toString()
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    busy = false
                                    error = it.localizedMessage
                                }
                        }
                    }
                ) {
                    Text(
                        if (busy) "Working…" else "Find account"
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                success?.let {
                    Text(it)
                }

                if (targetUid != null) {
                    Text("Account: $selectedUsername")

                    if (!loaded) {
                        CircularProgressIndicator()
                    } else {
                        Text("Confirmed violations: $count / 3")

                        if (count in 1L..2L) {
                            Text(
                                "Last suspension ends: ${
                                    suspensionEndDate(
                                        moderation?.getTimestamp("startedAt")
                                    )
                                }"
                            )
                        }

                        if (count >= 3) {
                            Text("Permanently banned")
                        }

                        if (count < 3) {
                            OutlinedTextField(
                                value = reason,
                                onValueChange = {
                                    reason = it
                                },
                                label = {
                                    Text("Rule broken and reason")
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
                                            reason.trim().length in 10..1000,
                                onClick = {
                                    confirmedCount = count
                                    showConfirmation = true
                                }
                            ) {
                                Text(
                                    if (count == 2L) {
                                        "Record third violation — permanent ban"
                                    } else {
                                        "Record violation — suspend 15 days"
                                    }
                                )
                            }
                        }

                        if (history.isNotEmpty()) {
                            Text(
                                text = "Recent moderation history",
                                style =
                                    MaterialTheme.typography.titleMedium
                            )
                        }

                        history.forEach { record ->
                            HorizontalDivider()

                            Text(
                                "Violation ${
                                    record.getLong("violationCount")
                                }: ${
                                    record.getString("reason").orEmpty()
                                }"
                            )

                            Text(
                                text = "Moderator: ${
                                    record.getString("moderatorId")
                                        .orEmpty()
                                }",
                                style =
                                    MaterialTheme.typography.bodySmall
                            )

                            record.getTimestamp("startedAt")?.let {
                                Text(
                                    text = DateFormat
                                        .getDateTimeInstance()
                                        .format(it.toDate()),
                                    style =
                                        MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (showConfirmation) {
                AlertDialog(
                    onDismissRequest = {
                        if (!busy) {
                            showConfirmation = false
                        }
                    },
                    title = {
                        Text(
                            if (confirmedCount >= 2) {
                                "Permanently ban $selectedUsername?"
                            } else {
                                "Suspend $selectedUsername for 15 days?"
                            }
                        )
                    },
                    text = {
                        Text(
                            "Reason: ${reason.trim()}\n\n" +
                                    "Only confirm a new, distinct incident. " +
                                    "This records the next violation."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                val target = targetUid

                                if (
                                    target != null &&
                                    FirebaseAuth.getInstance()
                                        .currentUser?.uid == moderatorUid
                                ) {
                                    busy = true
                                    error = null

                                    recordViolation(
                                        moderatorUid = moderatorUid,
                                        targetUid = target,
                                        incidentId = incidentId,
                                        expectedCount = confirmedCount,
                                        reason = reason,
                                        onDone = { failure ->
                                            busy = false
                                            showConfirmation = false

                                            if (failure != null) {
                                                error = failure
                                            } else {
                                                success =
                                                    "Violation recorded. " +
                                                            "Account access restricted."

                                                targetUid = null
                                                username = ""
                                                reason = ""

                                                incidentId =
                                                    UUID.randomUUID()
                                                        .toString()
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
                                    "Confirm violation"
                                }
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                showConfirmation = false
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