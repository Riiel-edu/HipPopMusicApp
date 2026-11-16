package com.example.csc490seniorproject.data

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

data class UserProfile(
    val username: String = "",
    val email: String = "",
    val bio: String = "",
    val profilePhotoURL: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val ageGroup: String = ""
)

fun registerUserProfile(
    firebaseUser: FirebaseUser,
    username: String,
    ageGroup: String,
    birthDate: String,
    onSuccess: () -> Unit,
    onUsernameTaken: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val usernameKey = username.lowercase()
    val usernameRef = db.collection("usernames").document(usernameKey)
    val userRef = db.collection("users").document(firebaseUser.uid)
    val privateRef = userRef.collection("private").document("profile")

    val profile = UserProfile(
        username = username,
        email = firebaseUser.email ?: "",
        ageGroup = ageGroup
    )

    db.runTransaction { transaction ->
        val usernameSnapshot = transaction.get(usernameRef)
        if (usernameSnapshot.exists()) {
            throw FirebaseFirestoreException(
                "Username already taken",
                FirebaseFirestoreException.Code.ALREADY_EXISTS
            )
        }
        transaction.set(usernameRef, mapOf("uid" to firebaseUser.uid, "ageGroup" to ageGroup))
        transaction.set(userRef, profile)
        transaction.set(privateRef, mapOf("birthDate" to birthDate))   // new
        null
    }.addOnSuccessListener {
        onSuccess()
    }.addOnFailureListener { e ->
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
            onUsernameTaken()
        } else {
            onFailure(e)
        }
    }
}
fun getUserProfile(
    uid: String,
    onSuccess: (UserProfile) -> Unit,
    onFailure: (Exception) -> Unit
) {
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(uid)
        .get()
        .addOnSuccessListener { snapshot ->
            val profile = snapshot.toObject(UserProfile::class.java)
            if (profile != null) {
                onSuccess(profile)
            } else {
                onFailure(Exception("Profile not found"))
            }
        }
        .addOnFailureListener { e -> onFailure(e) }
}

fun getEmailForUsername(
    username: String,
    onSuccess: (String) -> Unit,
    onNotFound: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val usernameKey = username.lowercase()
    db.collection("usernames").document(usernameKey).get()
        .addOnSuccessListener { usernameSnap ->
            val uid = usernameSnap.getString("uid")
            if (!usernameSnap.exists() || uid == null) {
                onNotFound()
                return@addOnSuccessListener
            }
            db.collection("users").document(uid).get()
                .addOnSuccessListener { userSnap ->
                    val email = userSnap.getString("email")
                    if (email != null) onSuccess(email) else onNotFound()
                }
                .addOnFailureListener { e -> onFailure(e) }
        }
        .addOnFailureListener { e -> onFailure(e) }
}

