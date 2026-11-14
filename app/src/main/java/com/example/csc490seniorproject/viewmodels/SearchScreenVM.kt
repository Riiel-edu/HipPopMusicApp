package com.example.csc490seniorproject.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.objects.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class SearchScreenVM(application: Application) : AndroidViewModel(application) {
    val songsList = mutableStateListOf<Song>()
    val userResults = mutableStateListOf<User>()


    val friendStatuses = mutableStateMapOf<String, FriendStatus>()

    var songsError by mutableStateOf<String?>(null)
        private set

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()


    private var publicSongsListener: ListenerRegistration? = null
    private var ownSongsListener: ListenerRegistration? = null
    private var sharedSongsListener: ListenerRegistration? = null
    private var friendSongsListener: ListenerRegistration? = null

    // Uids this account has blocked, used to keep them out of search
    // results. The reverse direction (did this candidate block me) is
    // checked per-result in searchUsers, same as refreshFriendStatus does
    // for friend state.
    private val blockedByMe = mutableSetOf<String>()
    private var blockedUsersListener: ListenerRegistration? = null

    // This account's friends, used to pull in friends-only songs.
    private val friendUids = mutableSetOf<String>()
    private var friendsListener: ListenerRegistration? = null

    private val publicSongs = mutableMapOf<String, Song>()
    private val ownSongs = mutableMapOf<String, Song>()
    private val sharedSongs = mutableMapOf<String, Song>()
    private val friendSongs = mutableMapOf<String, Song>()

    private var songsGeneration = 0
    private var searchGeneration = 0

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        songsGeneration++
        val generation = songsGeneration

        publicSongsListener?.remove()
        ownSongsListener?.remove()
        sharedSongsListener?.remove()
        friendSongsListener?.remove()
        blockedUsersListener?.remove()
        friendsListener?.remove()
        publicSongsListener = null
        ownSongsListener = null
        sharedSongsListener = null
        friendSongsListener = null
        blockedUsersListener = null
        friendsListener = null

        publicSongs.clear()
        ownSongs.clear()
        sharedSongs.clear()
        friendSongs.clear()
        friendUids.clear()
        songsList.clear()
        userResults.clear()
        friendStatuses.clear()
        blockedByMe.clear()
        songsError = null
        searchGeneration++

        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            songsError = "Please log in to see songs."
            return@AuthStateListener
        }

        blockedUsersListener = db.collection("users").document(uid)
            .collection("blockedUsers")
            .addSnapshotListener { snapshot, error ->
                if (generation != songsGeneration) return@addSnapshotListener
                if (error == null) {
                    blockedByMe.clear()
                    blockedByMe.addAll(snapshot?.documents.orEmpty().map { it.id })
                }
            }

        fun mergeAndPublish() {
            if (generation != songsGeneration) return
            val merged = (publicSongs.values + ownSongs.values + sharedSongs.values + friendSongs.values)
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
            songsList.clear()
            songsList.addAll(merged)
        }

        fun attach(query: Query, target: MutableMap<String, Song>): ListenerRegistration {
            return query.addSnapshotListener { snapshot, error ->
                if (generation != songsGeneration) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    songsError = error.localizedMessage
                        ?: "Unable to load songs."
                    return@addSnapshotListener
                }

                try {
                    target.clear()
                    snapshot?.documents.orEmpty().forEach { document ->
                        document.toObject(Song::class.java)
                            ?.copy(id = document.id)
                            ?.let { target[document.id] = it }
                    }
                    songsError = null
                    mergeAndPublish()
                } catch (e: Exception) {
                    songsError = e.localizedMessage
                        ?: "A song has an invalid field."
                }
            }
        }

        fun attachFriendSongs() {
            friendSongsListener?.remove()
            friendSongs.clear()

            if (friendUids.isEmpty()) {
                mergeAndPublish()
                return
            }

            // whereIn tops out at 30 values -- fine for now, revisit if
            // accounts end up with huge friend lists.
            friendSongsListener = attach(
                db.collection("songs")
                    .whereIn("ownerId", friendUids.take(30))
                    .whereEqualTo("friendsOnly", true),
                friendSongs
            )
        }

        friendsListener = db.collection("users").document(uid)
            .collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (generation != songsGeneration) return@addSnapshotListener
                if (error == null) {
                    friendUids.clear()
                    friendUids.addAll(snapshot?.documents.orEmpty().map { it.id })
                    attachFriendSongs()
                }
            }

        publicSongsListener = attach(
            db.collection("songs").whereEqualTo("published", true),
            publicSongs
        )
        ownSongsListener = attach(
            db.collection("songs").whereEqualTo("ownerId", uid),
            ownSongs
        )
        sharedSongsListener = attach(
            db.collection("songs").whereArrayContains("sharedWith", uid),
            sharedSongs
        )
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    fun searchUsers(query: String) {
        val myUid = auth.currentUser?.uid
        val lowerQuery = query.trim().lowercase()
        searchGeneration++
        val thisGeneration = searchGeneration

        userResults.clear()
        if (lowerQuery.isEmpty() || myUid == null) return

        db.collection("usernames")
            .orderBy(FieldPath.documentId())
            .startAt(lowerQuery)
            .endAt(lowerQuery + "\uf8ff")
            .limit(20)
            .get()
            .addOnSuccessListener usernamesLoaded@{ snapshot ->
                if (thisGeneration != searchGeneration) {
                    return@usernamesLoaded
                }

                for (doc in snapshot.documents) {
                    val uid = doc.getString("uid") ?: continue
                    if (uid == myUid || userResults.any { it.uid == uid }) continue
                    if (uid in blockedByMe) continue

                    val username = doc.getString("username") ?: doc.id
                    val privacy = doc.getBoolean("privacy") ?: false
                    val email = doc.getString("email") ?: ""

                    // Reverse direction: did this candidate block ME? Only
                    // added to results once we know they haven't -- fails
                    // closed (stays out of results) on a read error rather
                    // than risk a blocked account resurfacing in search.
                    db.collection("users").document(uid)
                        .collection("blockedUsers").document(myUid)
                        .get()
                        .addOnSuccessListener blockCheck@{ blockDoc ->
                            if (thisGeneration != searchGeneration) return@blockCheck
                            if (blockDoc.exists()) return@blockCheck
                            if (userResults.any { it.uid == uid }) return@blockCheck

                            userResults.add(
                                User(
                                    uid = uid,
                                    username = username,
                                    first_name = "",
                                    last_name = "",
                                    privacy = privacy,
                                    DoB = "",
                                    email = email,
                                    songs = arrayListOf(),
                                    friends = arrayListOf()
                                )
                            )
                            refreshFriendStatus(myUid, uid, thisGeneration)
                        }
                }
            }
            .addOnFailureListener { error ->
                println("FIREBASE: searchUsers failed: $error")
            }
    }

    private fun refreshFriendStatus(myUid: String, targetUid: String, generation: Int) {
        db.collection("users").document(myUid)
            .collection("friends").document(targetUid)
            .get()
            .addOnSuccessListener { friendDoc ->
                if (generation != searchGeneration) return@addOnSuccessListener

                if (friendDoc.exists()) {
                    friendStatuses[targetUid] = FriendStatus.FRIENDS
                    return@addOnSuccessListener
                }

                db.collection("users").document(targetUid)
                    .collection("friendRequests").document(myUid)
                    .get()
                    .addOnSuccessListener { sentDoc ->
                        if (generation != searchGeneration) return@addOnSuccessListener

                        if (sentDoc.exists()) {
                            friendStatuses[targetUid] = FriendStatus.REQUEST_SENT
                            return@addOnSuccessListener
                        }

                        db.collection("users").document(myUid)
                            .collection("friendRequests").document(targetUid)
                            .get()
                            .addOnSuccessListener { receivedDoc ->
                                if (generation != searchGeneration) return@addOnSuccessListener

                                friendStatuses[targetUid] = if (receivedDoc.exists()) {
                                    FriendStatus.REQUEST_RECEIVED
                                } else {
                                    FriendStatus.NONE
                                }
                            }
                            .addOnFailureListener {
                                if (generation == searchGeneration) {
                                    friendStatuses[targetUid] = FriendStatus.NONE
                                }
                            }
                    }
                    .addOnFailureListener {
                        if (generation == searchGeneration) {
                            friendStatuses[targetUid] = FriendStatus.NONE
                        }
                    }
            }
            .addOnFailureListener {
                if (generation == searchGeneration) {
                    friendStatuses[targetUid] = FriendStatus.NONE
                }
            }
    }

    fun sendFriendRequest(myUid: String, targetUid: String) {
        if (auth.currentUser?.uid != myUid || myUid == targetUid) return

        db.collection("users")
            .document(targetUid)
            .collection("friendRequests")
            .document(myUid)
            .set(
                mapOf(
                    "from" to myUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                if (auth.currentUser?.uid == myUid) {
                    friendStatuses[targetUid] = FriendStatus.REQUEST_SENT
                }
            }
            .addOnFailureListener { error ->
                println("FIREBASE: sendFriendRequest failed: $error")
            }
    }

    override fun onCleared() {
        songsGeneration++
        searchGeneration++
        auth.removeAuthStateListener(authListener)
        publicSongsListener?.remove()
        ownSongsListener?.remove()
        sharedSongsListener?.remove()
        friendSongsListener?.remove()
        blockedUsersListener?.remove()
        friendsListener?.remove()
        super.onCleared()
    }
}