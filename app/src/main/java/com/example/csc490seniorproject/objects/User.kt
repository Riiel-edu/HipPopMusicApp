package com.example.csc490seniorproject.objects
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentId

@Entity
class User (
    @DocumentId val uid: String = "",
    @ColumnInfo val username: String = "",
    @ColumnInfo var first_name: String,
    @ColumnInfo var last_name: String,
    @ColumnInfo var privacy: Boolean,
    @ColumnInfo var DoB: String,
    @ColumnInfo var email: String,
    @ColumnInfo val genre: List<String> = emptyList(),
    @ColumnInfo var songs: List<Song>,
    @ColumnInfo var friends: List<User>,
    val ageGroup: String = ""

    ){
}