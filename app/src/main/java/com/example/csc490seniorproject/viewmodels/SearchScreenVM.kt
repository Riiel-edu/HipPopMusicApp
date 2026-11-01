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

    // Real relationship status per uid (NONE / REQUEST_SENT / REQUEST_RECEIVED / FRIENDS),
    // looked up from Firestore so search results reflect reality instead of only
    // remembering requests sent during this screen's lifetime.
    val friendStatuses = mutableStateMapOf<String, FriendStatus>()

    var songsError by mutableStateOf<String?>(null)
        private set

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Three scoped listeners instead of one broad `songs` query. The songs rule
    // now depends on `published` / `ownerId` / `sharedWith`, fields that differ
    // per document. An unscoped query can't be proven safe against a rule like
    // that -- Firestore denies the whole request rather than filtering results
    // (security rules "aren't filters"). Each query below IS constrained to
    // match the rule's own condition exactly, so each one is provably safe on
    // its own and Firestore allows it.
    private var publicSongsListener: ListenerRegistration? = null
    private var ownSongsListener: ListenerRegistration? = null
    private var sharedSongsListener: ListenerRegistration? = null

    private val publicSongs = mutableMapOf<String, Song>()
    private val ownSongs = mutableMapOf<String, Song>()
    private val sharedSongs = mutableMapOf<String, Song>()

    private var songsGeneration = 0
    private var searchGeneration = 0

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        songsGeneration++
        val generation = songsGeneration

        publicSongsListener?.remove()
        ownSongsListener?.remove()
        sharedSongsListener?.remove()
        publicSongsListener = null
        ownSongsListener = null
        sharedSongsListener = null

        publicSongs.clear()
        ownSongs.clear()
        sharedSongs.clear()
        songsList.clear()
        userResults.clear()
        friendStatuses.clear()
        songsError = null
        searchGeneration++

        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            songsError = "Please log in to see songs."
            return@AuthStateListener
        }

        fun mergeAndPublish() {
            if (generation != songsGeneration) return
            val merged = (publicSongs.values + ownSongs.values + sharedSongs.values)
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
            .endAt(lowerQuery + "")
            .limit(20)
            .get()
            .addOnSuccessListener usernamesLoaded@{ snapshot ->
                if (thisGeneration != searchGeneration) {
                    return@usernamesLoaded
                }

                val uids = snapshot.documents
                    .mapNotNull { it.getString("uid") }
                    .distinct()
                    .filter { it != myUid } // never show yourself in your own search

                for (uid in uids) {
                    db.collection("users")
                        .document(uid)
                        .get()
                        .addOnSuccessListener userLoaded@{ document ->
                            if (thisGeneration != searchGeneration) {
                                return@userLoaded
                            }

                            if (
                                document.exists() &&
                                userResults.none { it.uid == uid }
                            ) {
                                userResults.add(
                                    User(
                                        uid = uid,
                                        username = document.getString("username") ?: "",
                                        first_name = document.getString("first_name") ?: "",
                                        last_name = document.getString("last_name") ?: "",
                                        privacy = document.getBoolean("privacy") ?: false,
                                        DoB = document.getString("dob") ?: "",
                                        email = document.getString("email") ?: "",
                                        songs = arrayListOf(),
                                        friends = arrayListOf()
                                    )
                                )
                                refreshFriendStatus(myUid, uid, thisGeneration)
                            }
                        }
                        .addOnFailureListener { error ->
                            println("FIREBASE: Loading user failed: $error")
                        }
                }
            }
            .addOnFailureListener { error ->
                println("FIREBASE: searchUsers failed: $error")
            }
    }

    // Checks the actual friends/friendRequests subcollections so a result shows
    // Friends / Request Sent / Already follows you instead of always "Add Friend".
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
        super.onCleared()
    }
}