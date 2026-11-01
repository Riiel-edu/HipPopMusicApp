package com.example.csc490seniorproject.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.objects.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.collections.set
import kotlin.text.get
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
enum class FriendStatus { NONE, REQUEST_SENT, REQUEST_RECEIVED, FRIENDS }

class ProfileScreenVM(application: Application) : AndroidViewModel(application) {
    val songsList = mutableStateListOf<Song>()
    val friendsList = mutableStateListOf<User>()
    var messageList = mutableStateListOf<String>()

    var profileUid by mutableStateOf<String?>(null)
    var friendStatus by mutableStateOf(FriendStatus.NONE)
    val incomingRequests = mutableStateListOf<User>()
    var profileUsername by mutableStateOf("")
    var aboutMe by mutableStateOf("")
    var profilePictureUrl by mutableStateOf("")
    private val db = FirebaseFirestore.getInstance()

    // The profile's immutable login username (the usernames/{key} doc id) --
    // distinct from profileUsername, which is the editable display name.
    // setPrivacy() needs this one, since it's what the usernames mirror doc
    // is keyed by.
    var accountUsername by mutableStateOf("")

    // Whether the profile currently loaded has privacy turned on.
    var isPrivate by mutableStateOf(false)

    var profileLocked by mutableStateOf<Boolean?>(null)

    init {
        viewModelScope.launch {
            songsList.add(Song())
            songsList.add(Song())
            friendsList.add(User(
                username = "Username",
                first_name = "Fname",
                last_name = "Lname",
                privacy = false,
                DoB = "1/1/2020",
                email = "email@mmm",
                songs = songsList,
                friends = friendsList,
            ))
            friendsList.add(User(
                username = "Username2",
                first_name = "Fname",
                last_name = "Lname",
                privacy = false,
                DoB = "1/1/2020",
                email = "email@mmm",
                songs = songsList,
                friends = friendsList
            ))
            messageList.add("Hey")

        }
    }

    // Resolves a username to the real Firebase Auth uid, via the same `usernames`
    // lookup collection the login screen already uses.
    private fun resolveUid(username: String, onResolved: (String) -> Unit) {
        db.collection("usernames").document(username.lowercase()).get()
            .addOnSuccessListener { doc ->
                val uid = doc.getString("uid")
                if (uid != null) onResolved(uid)
                else println("FIREBASE: no uid found for username $username")
            }
            .addOnFailureListener { e -> println("FIREBASE: resolveUid failed: $e") }
    }

    // Determines whether `otherUid`'s profile should be shown in full or
    // locked down to just their username + friend-request button. Reads the
    // public usernames/{username} doc first (always readable, mirrors the
    // privacy flag) so this never has to touch the gated users/{uid} doc just
    // to find out whether it's gated. Only for a private account does it then
    // check the viewer's own friends subcollection (also publicly readable)
    // to see whether they're let in.
    fun checkProfileLock(
        username: String,
        myUid: String?,
        otherUid: String,
        onResult: (locked: Boolean, private: Boolean) -> Unit
    ) {
        if (myUid != null && myUid == otherUid) {
            onResult(false, isPrivate)
            return
        }

        db.collection("usernames").document(username.lowercase()).get()
            .addOnSuccessListener { doc ->
                val private = doc.getBoolean("privacy") ?: false

                if (!private) {
                    onResult(false, false)
                    return@addOnSuccessListener
                }

                if (myUid == null) {
                    onResult(true, true)
                    return@addOnSuccessListener
                }

                db.collection("users").document(otherUid)
                    .collection("friends").document(myUid).get()
                    .addOnSuccessListener { friendDoc ->
                        onResult(!friendDoc.exists(), true)
                    }
                    .addOnFailureListener {
                        onResult(true, true)
                    }
            }
            .addOnFailureListener {
                // Couldn't confirm either way -- fail closed rather than
                // risk showing a private profile.
                onResult(true, false)
            }
    }

    // Call once when ProfileScreen loads.
    fun loadProfile(username: String, currentUid: String?) {
        accountUsername = username
        profileLocked = null

        resolveUid(username) { uid ->
            profileUid = uid

            checkProfileLock(username, currentUid, uid) { locked, private ->
                profileLocked = locked
                isPrivate = private

                if (!locked) {
                    getSongs(uid)
                    getFriends(uid)
                } else {
                    songsList.clear()
                    friendsList.clear()
                }

                if (currentUid != null) {
                    if (currentUid == uid) getIncomingRequests(currentUid)
                    else checkFriendStatus(currentUid, uid)
                }
            }
        }
    }

    fun getSongs(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    songsList.clear()
                    val songs = document.get("songs") as? List<String> ?: emptyList()
                    for (songId in songs) {
                        db.collection("songs").document(songId).get()
                            .addOnSuccessListener { songDocument ->
                                if (songDocument.exists()) {
                                    songsList.add(Song(
                                        id = songId,
                                        name = songDocument.getString("name") ?: "",
                                        rating = songDocument.getDouble("rating") ?: 0.0
                                    ))
                                }
                            }
                    }
                }
            }
    }

    fun getFriends(uid: String) {
        println("friends: getFriends() called with uid = $uid")
        friendsList.clear()

        db.collection("users").document(uid).collection("friends").get()
            .addOnSuccessListener { snapshot ->
                val friendUids = snapshot.documents.map { it.id }

                println("friends: Found ${friendUids.size} friend UIDs")
                println("friends: Friend UIDs = $friendUids")

                for (friendUid in friendUids) {
                    println("friends: Loading friend UID = $friendUid")

                    db.collection("users").document(friendUid).get()
                        .addOnSuccessListener { friendDocument ->
                            if (friendDocument.exists()) {
                                val friend = User(
                                    username = friendDocument.getString("username") ?: "",
                                    first_name = friendDocument.getString("first_name") ?: "",
                                    last_name = friendDocument.getString("last_name") ?: "",
                                    privacy = friendDocument.getBoolean("privacy") ?: false,
                                    DoB = friendDocument.getString("dob") ?: "",
                                    email = friendDocument.getString("email") ?: "",
                                    songs = arrayListOf(),
                                    friends = arrayListOf(),
                                    uid = friendUid
                                )

                                friendsList.add(friend)

                                println("friends: Added ${friend.username}")
                                println("friends: Current friendsList size = ${friendsList.size}")
                            } else {
                                println("friends: User document not found for UID = $friendUid")
                            }
                        }
                        .addOnFailureListener { e ->
                            println("friends: Failed to load $friendUid: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                println("friends: Failed to load user's friends: ${e.message}")
            }
    }

    fun checkFriendStatus(myUid: String, otherUid: String) {
        db.collection("users").document(myUid).collection("friends").document(otherUid).get()
            .addOnSuccessListener { friendDoc ->
                if (friendDoc.exists()) { friendStatus = FriendStatus.FRIENDS; return@addOnSuccessListener }
                db.collection("users").document(otherUid).collection("friendRequests").document(myUid).get()
                    .addOnSuccessListener { sentDoc ->
                        if (sentDoc.exists()) { friendStatus = FriendStatus.REQUEST_SENT; return@addOnSuccessListener }
                        db.collection("users").document(myUid).collection("friendRequests").document(otherUid).get()
                            .addOnSuccessListener { receivedDoc ->
                                friendStatus = if (receivedDoc.exists()) FriendStatus.REQUEST_RECEIVED else FriendStatus.NONE
                            }
                    }
            }
    }

    fun sendFriendRequest(myUid: String, targetUid: String) {
        db.collection("users").document(targetUid).collection("friendRequests").document(myUid)
            .set(mapOf("from" to myUid, "createdAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { friendStatus = FriendStatus.REQUEST_SENT }
            .addOnFailureListener { e -> println("FIREBASE: sendFriendRequest failed: $e") }
    }

    fun acceptFriendRequest(myUid: String, requesterUid: String) {
        val batch = db.batch()
        batch.set(db.collection("users").document(myUid).collection("friends").document(requesterUid),
            mapOf("createdAt" to FieldValue.serverTimestamp()))
        batch.set(db.collection("users").document(requesterUid).collection("friends").document(myUid),
            mapOf("createdAt" to FieldValue.serverTimestamp()))
        batch.delete(db.collection("users").document(myUid).collection("friendRequests").document(requesterUid))
        batch.commit()
            .addOnSuccessListener {
                friendStatus = FriendStatus.FRIENDS
                incomingRequests.removeAll { it.uid == requesterUid }
                getFriends(myUid)
            }
            .addOnFailureListener { e -> println("FIREBASE: acceptFriendRequest failed: $e") }
    }

    fun declineFriendRequest(myUid: String, requesterUid: String) {
        db.collection("users").document(myUid).collection("friendRequests").document(requesterUid)
            .delete()
            .addOnSuccessListener {
                incomingRequests.removeAll { it.uid == requesterUid }
                if (friendStatus == FriendStatus.REQUEST_RECEIVED) friendStatus = FriendStatus.NONE
            }
            .addOnFailureListener { e -> println("FIREBASE: declineFriendRequest failed: $e") }
    }

    fun getIncomingRequests(myUid: String) {
        incomingRequests.clear()
        db.collection("users").document(myUid).collection("friendRequests").get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val requesterUid = doc.id
                    db.collection("users").document(requesterUid).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                incomingRequests.add(User(
                                    username = userDoc.getString("username") ?: "",
                                    first_name = userDoc.getString("first_name") ?: "",
                                    last_name = userDoc.getString("last_name") ?: "",
                                    privacy = userDoc.getBoolean("privacy") ?: false,
                                    DoB = userDoc.getString("dob") ?: "",
                                    email = userDoc.getString("email") ?: "",
                                    songs = arrayListOf(),
                                    friends = arrayListOf(),
                                    uid = requesterUid
                                ))
                            }
                        }
                }
            }
    }


    fun getProfileDetails(myUid: String, onLoaded: () -> Unit = {}) {
        println("PROFILE: getProfileDetails() called")
        println("PROFILE: myUid = $myUid")

        db.collection("users").document(myUid).get()
            .addOnSuccessListener { document ->
                println("FIREBASE: User document retrieved successfully")
                println("FIREBASE: Document exists = ${document.exists()}")

                if (document.exists()) {
                    val displayName = document.getString("displayName")
                    val username = document.getString("username") ?: ""

                    println("PROFILE: Firestore displayName = $displayName")
                    println("PROFILE: Firestore username = $username")

                    profileUsername = if (displayName.isNullOrBlank()) {
                        username
                    } else {
                        displayName
                    }

                    aboutMe = document.getString("aboutMe") ?: ""

                    val savedPictureUrl =
                        document.getString("profilePictureUrl") ?: ""

                    println("PROFILE: Raw profilePictureUrl = $savedPictureUrl")
                    println("PROFILE: Picture URL is blank = ${savedPictureUrl.isBlank()}")
                    println("PROFILE: Is content URI = ${savedPictureUrl.startsWith("content://")}")
                    println("PROFILE: Is HTTP URL = ${savedPictureUrl.startsWith("http://") || savedPictureUrl.startsWith("https://")}")

                    profilePictureUrl = savedPictureUrl

                    println("PROFILE: Assigned profilePictureUrl = $profilePictureUrl")

                    if (displayName.isNullOrBlank()) {
                        println("FIREBASE: displayName is blank; updating it")

                        db.collection("users").document(myUid)
                            .set(
                                mapOf("displayName" to username),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            .addOnSuccessListener {
                                println("FIREBASE: Created displayName = $username")
                                onLoaded()
                            }
                            .addOnFailureListener { e ->
                                println("FIREBASE: Failed to create displayName: ${e.message}")
                                e.printStackTrace()
                                onLoaded()
                            }
                    } else {
                        println("FIREBASE: Loaded displayName = $displayName")
                        onLoaded()
                    }
                } else {
                    println("FIREBASE: User document does not exist for UID = $myUid")
                    onLoaded()
                }
            }
            .addOnFailureListener { e ->
                println("FIREBASE: Failed to get profile details: ${e.message}")
                e.printStackTrace()
                onLoaded()
            }
    }

    fun uploadProfilePicture(uid: String, imageUri: Uri) {
        println("PROFILE: Starting image upload: $imageUri")

        val imageRef = FirebaseStorage.getInstance()
            .reference
            .child("profilePictures/$uid.jpg")

        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                println("PROFILE: Image uploaded successfully")

                imageRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        val url = downloadUri.toString()

                        println("PROFILE: Generated download URL = $url")

                        db.collection("users")
                            .document(uid)
                            .update("profilePictureUrl", url)
                            .addOnSuccessListener {
                                profilePictureUrl = url
                                println("PROFILE: Download URL saved successfully")
                            }
                            .addOnFailureListener { e ->
                                println("PROFILE: Failed to save URL: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        println("PROFILE: Failed to get download URL: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                println("PROFILE: Image upload failed: ${e.message}")
                e.printStackTrace()
            }
    }

    fun updateProfileDetails(myUid: String) {
        val profileData = mapOf(
            "displayName" to profileUsername,
            "aboutMe" to aboutMe,
            "profilePictureUrl" to profilePictureUrl
        )

        db.collection("users").document(myUid)
            .set(profileData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                println("FIREBASE: Profile details updated or created successfully")
            }
            .addOnFailureListener { e ->
                println("FIREBASE: Failed to save profile details: ${e.message}")
            }
    }

    // Flips the account's privacy flag on both the gated users/{uid} doc and
    // its public usernames/{username} mirror, so search and login can still
    // see the privacy flag without ever reading the gated doc.
    fun setPrivacy(myUid: String, username: String, private: Boolean) {
        val usernameKey = username.lowercase()

        db.collection("users").document(myUid)
            .update("privacy", private)
            .addOnSuccessListener {
                isPrivate = private

                db.collection("usernames").document(usernameKey)
                    .update("privacy", private)
                    .addOnFailureListener { e ->
                        println("FIREBASE: Failed to update usernames privacy mirror: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                println("FIREBASE: Failed to update privacy: ${e.message}")
            }
    }
}