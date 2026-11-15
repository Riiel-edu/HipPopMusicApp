package com.example.csc490seniorproject.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.csc490seniorproject.objects.CollaborationSession
import com.example.csc490seniorproject.objects.Song

/**
 * Start / join / leave a collaboration session, show the session code, and hand
 * your tracks to your partner. All state lives in the ViewModels; this is display only.
 */
@Composable
fun CollaborationPanel(
    session: CollaborationSession?,
    isCollaborating: Boolean,
    currentUserId: String?,
    isLoading: Boolean,
    message: String?,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {

        if (!isCollaborating || session == null) {
            Button(onClick = onStart, enabled = !isLoading) {
                Text("🤝 Start collaboration")
            }
        } else {
            val iAmHost = session.hostUserId == currentUserId
            val partnerId = session.participantIds.firstOrNull { it != currentUserId }

            Text("Collaborating on “${session.songName}”")

            if (iAmHost) {
                Text("Session code (share it with your partner):")
                SelectionContainer { Text(session.id) }
            }

            Text(if (partnerId == null) "Waiting for a partner…" else "Partner connected")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("● Your tracks", color = Color(0xFF5d36eb))
                Text("● Partner's tracks", color = Color(0xFF0F7A6E))
            }

            TextButton(onClick = onLeave) {
                Text(if (iAmHost) "End session" else "Leave session")
            }
        }

        if (message != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = Color(0xFFB00020), modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissMessage) { Text("Dismiss") }
            }
        }
    }
}