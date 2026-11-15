package com.example.csc490seniorproject.data

import com.example.csc490seniorproject.objects.CollaborationParticipant
import com.example.csc490seniorproject.objects.CollaborationSession
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.objects.Track
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

object CollaborationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val sessions = db.collection("collaborationSessions")

    private fun requireUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("You must be signed in.")
    }

    private fun sessionRef(sessionId: String) = sessions.document(sessionId)

    private fun tracksRef(sessionId: String) =
        sessionRef(sessionId).collection("tracks")

    private fun participantsRef(sessionId: String) =
        sessionRef(sessionId).collection("participants")

    private fun displayName(): String {
        val user = FirebaseAuth.getInstance().currentUser
        return user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email
            ?: "User"
    }

    private fun participantIdsOf(snapshot: DocumentSnapshot): List<String> =
        (snapshot.get("participantIds") as? List<*>)
            ?.filterIsInstance<String>()
            ?: emptyList()

    suspend fun createSession(song: Song): String {
        val uid = requireUserId()
        val sessionId = UUID.randomUUID().toString()

        // The rules require the host to own every track they initialize,
        // so force ownerId = uid instead of keeping whatever was there.
        val tracks = song.tracks.map { it.copy(ownerId = uid) }

        val session = CollaborationSession(
            id = sessionId,
            songId = song.id,
            songName = song.name,
            hostUserId = uid,
            participantIds = listOf(uid),
            bpm = song.bpm,
            stepsPerBeat = song.stepsPerBeat,
            beatsPerBar = song.beatsPerBar,
            bars = song.bars
        )

        val participant = CollaborationParticipant(
            userId = uid,
            displayName = displayName()
        )

        val batch = db.batch()
        batch.set(sessionRef(sessionId), session)
        batch.set(participantsRef(sessionId).document(uid), participant)
        tracks.forEach { track ->
            batch.set(tracksRef(sessionId).document(track.id), track)
        }
        batch.commit().await()

        return sessionId
    }

    /** Joins the session and returns its songId (so the caller can open the editor). */
    suspend fun joinSession(sessionId: String): String {
        val uid = requireUserId()
        val ref = sessionRef(sessionId)

        return db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)

            if (!snapshot.exists()) {
                throw IllegalStateException("Session not found.")
            }
            if (snapshot.getString("status") != "active") {
                throw IllegalStateException("This session is no longer active.")
            }

            val participants = participantIdsOf(snapshot)

            // Already in (host, or rejoining): nothing to write. Re-writing the
            // participant doc would be an update, which the rules deny.
            if (uid in participants) return@runTransaction snapshot.getString("songId").orEmpty()

            if (participants.size >= 2) {
                throw IllegalStateException("This session is full.")
            }

            // The rules require updatedAt == request.time on every session update.
            transaction.update(
                ref,
                mapOf(
                    "participantIds" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            transaction.set(
                participantsRef(sessionId).document(uid),
                CollaborationParticipant(userId = uid, displayName = displayName())
            )

            snapshot.getString("songId").orEmpty()
        }.await()
    }

    /**
     * Accepts a COOP_INVITE chat message: joins the session and marks the message
     * ACCEPTED in ONE transaction. The chatMessages update rule only allows ACCEPTED
     * when the session (via getAfter) already contains the caller, so the two writes
     * must be atomic.
     */
    suspend fun acceptInvite(messageId: String, sessionId: String) {
        val uid = requireUserId()
        val messageRef = db.collection("chatMessages").document(messageId)
        val ref = sessionRef(sessionId)

        db.runTransaction { transaction ->
            val invite = transaction.get(messageRef)

            check(invite.exists()) { "Invitation does not exist." }
            check(invite.getString("type") == "COOP_INVITE") { "Invalid invitation." }
            check(invite.getString("recipientId") == uid) { "You aren't the recipient." }
            check(invite.getString("status") == "PENDING") { "Invitation already handled." }
            check(invite.getString("sessionId") == sessionId) { "Invitation data changed." }

            val snapshot = transaction.get(ref)

            check(snapshot.exists()) { "Session not found." }
            check(snapshot.getString("status") == "active") {
                "This session is no longer active."
            }
            check(snapshot.getString("hostUserId") == invite.getString("senderId")) {
                "Invalid invitation."
            }

            val participants = participantIdsOf(snapshot)
            check(uid in participants || participants.size < 2) { "This session is full." }

            if (uid !in participants) {
                transaction.update(
                    ref,
                    mapOf(
                        "participantIds" to FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.set(
                    participantsRef(sessionId).document(uid),
                    CollaborationParticipant(userId = uid, displayName = displayName())
                )
            }

            transaction.update(messageRef, "status", "ACCEPTED")
            null
        }.await()
    }

    suspend fun leaveSession(sessionId: String) {
        val uid = requireUserId()
        val ref = sessionRef(sessionId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)

            if (!snapshot.exists()) return@runTransaction null

            val participants = participantIdsOf(snapshot)
            if (uid !in participants) return@runTransaction null

            if (snapshot.getString("hostUserId") == uid) {
                // Host leaving closes the session for everyone.
                transaction.update(
                    ref,
                    mapOf(
                        "status" to "closed",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                transaction.update(
                    ref,
                    mapOf(
                        "participantIds" to FieldValue.arrayRemove(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.delete(participantsRef(sessionId).document(uid))
            }

            null
        }.await()
    }

    fun observeSession(sessionId: String): Flow<CollaborationSession?> =
        callbackFlow {
            val listener = sessionRef(sessionId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.toObject(CollaborationSession::class.java))
                }
            awaitClose { listener.remove() }
        }

    fun observeTracks(sessionId: String): Flow<List<Track>> =
        callbackFlow {
            val listener = tracksRef(sessionId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val tracks = snapshot?.documents?.mapNotNull {
                        it.toObject(Track::class.java)
                    } ?: emptyList()
                    trySend(tracks)
                }
            awaitClose { listener.remove() }
        }

    /** Adds a new track owned by the caller (either participant, per the rules). */
    suspend fun addTrack(sessionId: String, track: Track) {
        val uid = requireUserId()
        tracksRef(sessionId)
            .document(track.id)
            .set(track.copy(ownerId = uid))
            .await()
    }

    suspend fun updateTrack(sessionId: String, track: Track) {
        val uid = requireUserId()
        val ref = tracksRef(sessionId).document(track.id)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)

            if (!snapshot.exists()) {
                throw IllegalStateException("Track not found.")
            }

            val ownerId = snapshot.getString("ownerId")
            if (ownerId != uid) {
                throw IllegalStateException("You do not own this track.")
            }

            // Keep ownership as-is; transfers go through assignTrack.
            transaction.set(ref, track.copy(ownerId = ownerId))
            null
        }.await()
    }

    suspend fun deleteTrack(sessionId: String, trackId: String) {
        tracksRef(sessionId).document(trackId).delete().await()
    }

    suspend fun assignTrack(
        sessionId: String,
        trackId: String,
        newOwnerId: String
    ) {
        val uid = requireUserId()
        val session = sessionRef(sessionId)
        val track = tracksRef(sessionId).document(trackId)

        db.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(session)
            val trackSnapshot = transaction.get(track)

            val participants = participantIdsOf(sessionSnapshot)

            if (uid !in participants || newOwnerId !in participants) {
                throw IllegalStateException("Both users must belong to this session.")
            }
            if (!trackSnapshot.exists()) {
                throw IllegalStateException("Track not found.")
            }
            if (trackSnapshot.getString("ownerId") != uid) {
                throw IllegalStateException("Only the current owner can transfer this track.")
            }

            transaction.update(track, "ownerId", newOwnerId)
            null
        }.await()
    }
}