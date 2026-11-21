
package com.example.csc490seniorproject.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.csc490seniorproject.data.SongRepository
import com.example.csc490seniorproject.moderation.FinishedReport
import com.example.csc490seniorproject.moderation.ModerationReport
import com.example.csc490seniorproject.moderation.ReportCase
import com.example.csc490seniorproject.objects.FriendActivity
import com.example.csc490seniorproject.objects.Song
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LandingScreenVM(application: Application) :
    AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val _topSongs = MutableStateFlow<List<Song>>(emptyList())
    val topSongs = _topSongs.asStateFlow()

    private val _friendActivity =
        MutableStateFlow<List<FriendActivity>>(emptyList())
    val friendActivity = _friendActivity.asStateFlow()

    // Moderator data
    private val _pendingReports =
        MutableStateFlow<List<ReportCase>>(emptyList())
    val pendingReports = _pendingReports.asStateFlow()

    private val _finishedReports =
        MutableStateFlow<List<FinishedReport>>(emptyList())
    val finishedReports = _finishedReports.asStateFlow()

    private val _isModerator = MutableStateFlow(false)
    val isModerator = _isModerator.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        _error.value = null

        try {
            checkModerator()

            if (_isModerator.value) {
                loadPendingReports()
                loadFinishedReports()
            } else {
                loadTopSongs()
                loadFriendActivity()
            }

        } catch (e: Exception) {
            android.util.Log.e(
                "LandingScreenVM",
                "Landing screen refresh failed",
                e
            )

            _error.value = "Couldn't load landing screen: ${e.message}"

        } finally {
            _loading.value = false
        }
    }

    private suspend fun checkModerator() {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            _isModerator.value = false
            return
        }

        val snapshot = db.collection("moderators")
            .document(uid)
            .get()
            .await()

        _isModerator.value =
            snapshot.getBoolean("enabled") == true
    }

    private suspend fun loadPendingReports() {
        val queues = db.collection("reportQueues")
            .get()
            .await()

        val cases = mutableListOf<ReportCase>()

        for (queue in queues.documents) {
            val targetUid = queue.id
            val caseId = queue.getString("caseId") ?: continue
            val reportCount = queue.getLong("reportCount")?.toInt() ?: 0

            val review = db.collection("reportQueues")
                .document(targetUid)
                .collection("reviews")
                .document(caseId)
                .get()
                .await()

            if (review.exists()) continue

            val reportsSnapshot = db.collection("reportQueues")
                .document(targetUid)
                .collection("rounds")
                .document(caseId)
                .collection("reports")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .await()

            val reports = reportsSnapshot.documents.mapNotNull { doc ->
                val reporterId = doc.getString("reporterId")
                    ?: return@mapNotNull null

                ModerationReport(
                    reporterId = reporterId,
                    targetUid = doc.getString("targetUid") ?: targetUid,
                    caseId = doc.getString("caseId") ?: caseId,
                    reason = doc.getString("reason") ?: "",
                    evidence = doc.getString("evidence") ?: "",
                    createdAt = doc.getTimestamp("createdAt")
                )
            }

            if (reports.isNotEmpty()) {
                cases.add(
                    ReportCase(
                        targetUid = targetUid,
                        targetUsername =
                            queue.getString("targetUsername") ?: "Unknown User",
                        caseId = caseId,
                        reportCount = reportCount,
                        reports = reports
                    )
                )
            }
        }

        _pendingReports.value = cases
    }

    private suspend fun loadFinishedReports() {
        val reviews = db.collectionGroup("reviews")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        _finishedReports.value = reviews.documents.mapNotNull { doc ->
            val targetUid = doc.reference.parent.parent?.id
                ?: return@mapNotNull null

            FinishedReport(
                targetUid = targetUid,
                caseId = doc.getString("caseId") ?: doc.id,
                decision = doc.getString("decision") ?: "",
                reason = doc.getString("reason") ?: "",
                moderatorId = doc.getString("moderatorId") ?: "",
                reportCount =
                    doc.getLong("reportCount")?.toInt() ?: 0,
                createdAt = doc.getTimestamp("createdAt")
            )
        }
    }

    private suspend fun loadTopSongs() {
        val today = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())

        val publishedSongs = SongRepository.publishedSongs()

        val dailyViews = db.collection("dailySongViews")
            .whereEqualTo("date", today)
            .get()
            .await()

        val viewCounts = dailyViews.documents.associate { doc ->
            val songId = doc.getString("songId").orEmpty()
            val views = doc.getLong("views") ?: 0L

            songId to views
        }

        val topSongs = publishedSongs
            .sortedByDescending { viewCounts[it.id] ?: 0L }
            .take(5)

        _topSongs.value = topSongs.map { song ->
            val averageRating =
                SongRepository.getSongRating(song.id)

            song.copy(rating = averageRating)
        }
    }

    private suspend fun loadFriendActivity() {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            _friendActivity.value = emptyList()
            return
        }

        val friendsSnapshot = db.collection("users")
            .document(uid)
            .collection("friends")
            .get()
            .await()

        val friendIds = friendsSnapshot.documents.map { it.id }

        if (friendIds.isEmpty()) {
            _friendActivity.value = emptyList()
            return
        }

        val publishedSongs = SongRepository.publishedSongs()
            .filter { it.ownerId in friendIds }

        val friendsOnlySongs = friendIds.flatMap { friendId ->
            SongRepository.friendsOnlySongs(friendId)
        }

        val recentSongs = (publishedSongs + friendsOnlySongs)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt?.time ?: 0L }
            .take(10)

        val friendNames = mutableMapOf<String, String>()

        recentSongs.map { it.ownerId }.distinct().forEach { friendId ->
            val profile = db.collection("users")
                .document(friendId)
                .get()
                .await()

            friendNames[friendId] =
                profile.getString("displayName")
                    ?: profile.getString("username")
                            ?: "Friend"
        }

        _friendActivity.value = recentSongs.map { song ->
            FriendActivity(
                song = song,
                friendId = song.ownerId,
                friendName = friendNames[song.ownerId] ?: "Friend"
            )
        }
    }
}