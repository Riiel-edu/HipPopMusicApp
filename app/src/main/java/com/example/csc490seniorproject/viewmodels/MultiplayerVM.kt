
package com.example.csc490seniorproject.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.csc490seniorproject.objects.Beatmap
import com.example.csc490seniorproject.objects.MultiplayerPlayerStats
import com.example.csc490seniorproject.objects.MultiplayerRoom
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class MultiplayerMatchResult(
    val winnerId: String? = null,
    val isDraw: Boolean = false,
    val localScore: Int = 0,
    val opponentScore: Int = 0
)

class MultiplayerVM(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var cachedUsername: String? = null
    private val rooms = db.collection("multiplayerRooms")

    private var roomListener: ListenerRegistration? = null
    private var statsListener: ListenerRegistration? = null

    private var activeRoomId: String? = null
    private val _currentRoom = MutableStateFlow<MultiplayerRoom?>(null)
    val currentRoom: StateFlow<MultiplayerRoom?> =
        _currentRoom.asStateFlow()

    private val _localStats = MutableStateFlow<MultiplayerPlayerStats?>(null)
    val localStats: StateFlow<MultiplayerPlayerStats?> =
        _localStats.asStateFlow()

    private val _opponentStats = MutableStateFlow<MultiplayerPlayerStats?>(null)
    val opponentStats: StateFlow<MultiplayerPlayerStats?> =
        _opponentStats.asStateFlow()

    private val _matchResult = MutableStateFlow<MultiplayerMatchResult?>(null)
    val matchResult: StateFlow<MultiplayerMatchResult?> =
        _matchResult.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()


    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("You must be logged in to play versus mode.")
    }

    /**
     * Creates a room using the host's selected beatmap.
     *
     * The host is the only player initially.
     * The second player joins through joinRoom().
     */
    suspend fun createRoom(beatmap: Beatmap): String {
        val uid = requireUserId()

        require(beatmap.id.isNotBlank()) {
            "Invalid beatmap ID."
        }

        require(beatmap.songId.isNotBlank()) {
            "This beatmap does not have an associated song."
        }

        val roomRef = rooms.document()

        val roomData = mapOf(
            "hostId" to uid,
            "opponentId" to "",
            "playerIds" to listOf(uid),
            "beatmapId" to beatmap.id,
            "songId" to beatmap.songId,
            "status" to "waiting",
            "createdAt" to FieldValue.serverTimestamp()
        )

        roomRef.set(roomData).await()

        startListening(roomRef.id)

        return roomRef.id
    }

    /**
     * Joins an existing waiting room.
     *
     * A transaction prevents two players from joining
     * the same room simultaneously.
     */
    suspend fun joinRoom(roomId: String) {
        val uid = requireUserId()

        require(roomId.isNotBlank()) {
            "Invalid room ID."
        }

        val roomRef = rooms.document(roomId)

        db.runTransaction { transaction ->

            val snapshot = transaction.get(roomRef)

            check(snapshot.exists()) {
                "This multiplayer room does not exist."
            }

            val hostId = snapshot.getString("hostId") ?: ""
            val status = snapshot.getString("status") ?: ""

            @Suppress("UNCHECKED_CAST")
            val playerIds =
                snapshot.get("playerIds") as? List<String> ?: emptyList()

            check(hostId != uid) {
                "You cannot join your own room."
            }

            check(status == "waiting") {
                "This room is no longer accepting players."
            }

            check(playerIds.size == 1 && hostId in playerIds) {
                "This room is already full."
            }

            transaction.update(
                roomRef,
                mapOf(
                    "opponentId" to uid,
                    "playerIds" to listOf(hostId, uid),
                    "status" to "playing"
                )
            )

            null
        }.await()

        startListening(roomId)
    }

    suspend fun getCurrentUsername(): String {
        cachedUsername?.let { return it }

        val uid = requireUserId()

        val document = db.collection("users")
            .document(uid)
            .get()
            .await()

        val username = document.getString("username") ?: "Player"

        cachedUsername = username

        return username
    }

    fun resetMatchState() {
        activeRoomId = null
        _currentRoom.value = null
        _localStats.value = null
        _opponentStats.value = null
        _matchResult.value = null
        _error.value = null
    }


    /**
     * Accepts a VS invitation and creates a multiplayer room.
     *
     * Both players are added immediately, so the existing
     * multiplayer gameplay system can be reused.
     *
     * The invitation ID is also the room ID.
     */
    suspend fun acceptInvitation(inviteId: String): String {
        val uid = requireUserId()

        require(inviteId.isNotBlank()) {
            "Invalid invitation ID."
        }

        val inviteRef = db.collection("chatMessages")
            .document(inviteId)

        val roomRef = rooms.document(inviteId)

        db.runTransaction { transaction ->

            val invite = transaction.get(inviteRef)

            check(invite.exists()) {
                "Invitation does not exist."
            }

            check(invite.getString("type") == "VS_INVITE") {
                "Invalid invitation type."
            }

            check(invite.getString("recipientId") == uid) {
                "You are not the recipient of this invitation."
            }

            check(invite.getString("status") == "PENDING") {
                "This invitation is no longer available."
            }

            val senderId = invite.getString("senderId")
                ?: throw IllegalStateException("Missing sender.")

            val beatmapId = invite.getString("beatmapId")
                ?: throw IllegalStateException("Missing beatmap.")

            val songId = invite.getString("songId")
                ?: throw IllegalStateException("Missing song.")

            val existingRoom = transaction.get(roomRef)

            check(!existingRoom.exists()) {
                "A match already exists for this invitation."
            }

            // Create the same room structure used by normal VS matches.
            transaction.set(
                roomRef,
                mapOf(
                    "hostId" to senderId,
                    "opponentId" to uid,
                    "playerIds" to listOf(senderId, uid),
                    "beatmapId" to beatmapId,
                    "songId" to songId,
                    "status" to "playing",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            // Accept the invitation in the same transaction.
            transaction.update(
                inviteRef,
                "status",
                "ACCEPTED"
            )

            inviteId

        }.await()

        // Reuse your existing room listener and score tracking.
        startListening(inviteId)

        return inviteId
    }

    /**
     * Observes the room and both players' statistics.
     */
    fun startListening(roomId: String) {
        stopListening()

        activeRoomId = roomId

        val uid = requireUserId()
        val roomRef = rooms.document(roomId)

        roomListener = roomRef.addSnapshotListener { snapshot, exception ->

            if (exception != null) {
                _error.value = exception.message
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                _error.value = "Multiplayer room not found."
                return@addSnapshotListener
            }

            val room = snapshot.toObject(MultiplayerRoom::class.java)

            if (room == null) {
                _error.value = "Unable to load multiplayer room."
                return@addSnapshotListener
            }

            if (uid !in room.playerIds) {
                _error.value = "You are not a participant in this match."
                return@addSnapshotListener
            }

            _currentRoom.value = room
            updateMatchResult()
        }

        statsListener = roomRef
            .collection("players")
            .addSnapshotListener { snapshot, exception ->

                if (exception != null) {
                    _error.value = exception.message
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val room = _currentRoom.value ?: return@addSnapshotListener

                val opponentId = room.playerIds.firstOrNull {
                    it != uid
                }

                snapshot.documents.forEach { document ->

                    val stats =
                        document.toObject(MultiplayerPlayerStats::class.java)
                            ?: return@forEach

                    when (document.id) {
                        uid -> _localStats.value = stats
                        opponentId -> _opponentStats.value = stats
                    }
                }

                updateMatchResult()
            }
    }

    /**
     * Publishes the current player's statistics.
     *
     * Each player can only publish their own document.
     */
    suspend fun publishStats(stats: MultiplayerPlayerStats) {
        val uid = requireUserId()

        val roomId = activeRoomId
            ?: throw IllegalStateException("No active room ID.")

        val room = _currentRoom.value
            ?: throw IllegalStateException("Room has not loaded.")

        android.util.Log.d(
            "MP_STATS",
            "Publishing stats for $uid in room $roomId"
        )

        check(room.status == "playing")
        check(uid in room.playerIds)

        val playerStats = stats.copy(
            userId = uid,
            finished = true,
            combo = 0,
            isOnFire = false
        )

        rooms.document(roomId)
            .collection("players")
            .document(uid)
            .set(
                mapOf(
                    "userId" to playerStats.userId,
                    "username" to playerStats.username,
                    "score" to playerStats.score,
                    "percentage" to playerStats.percentage,
                    "combo" to playerStats.combo,
                    "maxCombo" to playerStats.maxCombo,
                    "isOnFire" to playerStats.isOnFire,
                    "totalStars" to playerStats.totalStars,
                    "perfectCount" to playerStats.perfectCount,
                    "goodCount" to playerStats.goodCount,
                    "missCount" to playerStats.missCount,
                    "finished" to playerStats.finished,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()

        android.util.Log.d(
            "MP_STATS",
            "Successfully uploaded stats for $uid"
        )
    }

    /**
     * Marks the local player as finished.
     *
     * The match result is calculated after both players
     * have submitted their final statistics.
     */
    suspend fun finishMatch(stats: MultiplayerPlayerStats) {
        publishStats(
            stats.copy(
                finished = true,
                combo = 0,
                isOnFire = false
            )
        )
    }

    private fun updateMatchResult() {
        val room = _currentRoom.value ?: return
        val local = _localStats.value ?: return
        val opponent = _opponentStats.value ?: return

        if (!local.finished || !opponent.finished) {
            _matchResult.value = null
            return
        }

        val uid = auth.currentUser?.uid ?: return

        val isDraw = local.score == opponent.score

        val winnerId = when {
            isDraw -> null
            local.score > opponent.score -> uid
            else -> opponent.userId
        }

        _matchResult.value = MultiplayerMatchResult(
            winnerId = winnerId,
            isDraw = isDraw,
            localScore = local.score,
            opponentScore = opponent.score
        )
    }

    fun stopListening() {
        roomListener?.remove()
        statsListener?.remove()

        roomListener = null
        statsListener = null
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
