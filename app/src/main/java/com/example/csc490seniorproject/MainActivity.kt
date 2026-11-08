package com.example.csc490seniorproject

import android.os.Bundle
import androidx.activity.SystemBarStyle
import android.graphics.Color
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.csc490seniorproject.nav.Nav
import com.example.csc490seniorproject.nav.NavItem
import com.example.csc490seniorproject.nav.TopBar
import com.example.csc490seniorproject.ui.theme.CSC490SeniorProjectTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var messageListener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

    private var isAppVisible = false
    private var listenerVersion = 0
    private var activeToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        setContent {
            CSC490SeniorProjectTheme {
                val navHostController = rememberNavController()
                val navBackStackEntry by
                navHostController.currentBackStackEntryAsState()

                val currentDestination = navBackStackEntry?.destination
                val currentRoute = currentDestination?.route

                val showNavigationBars =
                    currentRoute != null &&
                            currentRoute != "SplashScreen" &&
                            currentRoute != "LoginScreen" &&
                            currentRoute != "RegisterScreen" &&
                            currentRoute != "GameScreen"

                val navItemsList = listOf(
                    NavItem(
                        title = "For You",
                        iconSelected = Icons.Filled.Home,
                        iconUnselected = Icons.Outlined.Home,
                        route = "LandingScreen"
                    ),
                    NavItem(
                        title = "Search",
                        iconSelected = Icons.Filled.Search,
                        iconUnselected = Icons.Outlined.Search,
                        route = "SearchScreen"
                    ),
                    NavItem(
                        title = "Create",
                        iconSelected = Icons.Filled.Add,
                        iconUnselected = Icons.Outlined.Add,
                        route = "MySongsScreen"
                    ),
                    NavItem(
                        title = "Profile",
                        iconSelected = Icons.Filled.Person,
                        iconUnselected = Icons.Outlined.PersonOutline,
                        route = "ProfileScreen"
                    )
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (showNavigationBars) {
                            TopBar(navHostController)
                        }
                    },
                    bottomBar = {
                        if (showNavigationBars) {
                            NavigationBar {
                                navItemsList.forEach { item ->
                                    val isSelected =
                                        currentDestination?.hierarchy?.any {
                                            it.route == item.route
                                        } == true

                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            navHostController.navigate(item.route) {
                                                launchSingleTop = true
                                                restoreState = true

                                                popUpTo(
                                                    navHostController.graph
                                                        .findStartDestination().id
                                                ) {
                                                    saveState = true
                                                }
                                            }
                                            if (item.route == "ProfileScreen") {
                                                navHostController.popBackStack(
                                                    route = "ProfileScreen",
                                                    inclusive = false
                                                )
                                            }
                                        },
                                        label = {
                                            Text(item.title)
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) {
                                                    item.iconSelected
                                                } else {
                                                    item.iconUnselected
                                                },
                                                contentDescription = item.title
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Nav(
                        navController = navHostController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isAppVisible = true

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            listenForIncomingMessages(firebaseAuth.currentUser?.uid)
        }

        authListener = listener
        auth.addAuthStateListener(listener)
    }

    override fun onStop() {
        isAppVisible = false

        authListener?.let { auth.removeAuthStateListener(it) }
        authListener = null

        stopMessageListener()
        activeToast?.cancel()
        activeToast = null

        super.onStop()
    }

    private fun stopMessageListener() {
        listenerVersion++
        messageListener?.remove()
        messageListener = null
    }

    private fun listenForIncomingMessages(uid: String?) {
        stopMessageListener()

        if (uid == null || !isAppVisible) return

        val version = listenerVersion
        val seenMessageIds = mutableSetOf<String>()
        var initialMessagesLoaded = false

        messageListener = db.collection("chatMessages")
            .whereArrayContains("participants", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, exception ->

                if (!isAppVisible || version != listenerVersion) {
                    return@addSnapshotListener
                }

                if (exception != null) {
                    Log.e(
                        "ChatNotifications",
                        "Could not listen for messages",
                        exception
                    )
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.metadata.isFromCache) {
                    return@addSnapshotListener
                }

                // Establish a baseline without notifying about old messages.
                if (!initialMessagesLoaded) {
                    snapshot.documents.forEach { document ->
                        seenMessageIds.add(document.id)
                    }
                    initialMessagesLoaded = true
                    return@addSnapshotListener
                }

                snapshot.documents.forEach { document ->
                    if (document.metadata.hasPendingWrites()) {
                        return@forEach
                    }

                    if (!seenMessageIds.add(document.id)) {
                        return@forEach
                    }

                    val recipientId = document.getString("recipientId")
                    val senderId = document.getString("senderId")
                    val messageText = document.getString("text").orEmpty()

                    // Notify only for messages addressed to this account.
                    if (
                        recipientId == uid &&
                        senderId != null &&
                        senderId != uid &&
                        messageText.isNotBlank()
                    ) {
                        showIncomingMessage(
                            senderId = senderId,
                            messageText = messageText,
                            recipientUid = uid,
                            version = version
                        )
                    }
                }
            }
    }

    private fun showIncomingMessage(
        senderId: String,
        messageText: String,
        recipientUid: String,
        version: Int
    ) {
        fun showPopup(senderName: String) {
            // Ignore delayed results after leaving the app or switching accounts.
            if (
                !isAppVisible ||
                version != listenerVersion ||
                auth.currentUser?.uid != recipientUid
            ) {
                return
            }

            activeToast?.cancel()

            activeToast = Toast.makeText(
                this@MainActivity,
                "New message from $senderName: ${messageText.take(100)}",
                Toast.LENGTH_LONG
            )

            activeToast?.show()
        }

        db.collection("users")
            .document(senderId)
            .get()
            .addOnSuccessListener { document ->
                val username = document.getString("username")
                    ?.takeIf { it.isNotBlank() }
                    ?: "a friend"

                showPopup(username)
            }
            .addOnFailureListener {
                showPopup("a friend")
            }
    }
}