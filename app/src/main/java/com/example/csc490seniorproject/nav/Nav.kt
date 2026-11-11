package com.example.csc490seniorproject.nav

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.csc490seniorproject.data.SongRepository
import com.example.csc490seniorproject.objects.Beatmap
import com.example.csc490seniorproject.screens.BeatmapEditorScreen
import com.example.csc490seniorproject.screens.BlockedUsersScreen
import com.example.csc490seniorproject.screens.EditorScreen
import com.example.csc490seniorproject.screens.GameScreen
import com.example.csc490seniorproject.screens.LandingScreen
import com.example.csc490seniorproject.screens.LoginScreen
import com.example.csc490seniorproject.screens.MySongsScreen
import com.example.csc490seniorproject.screens.ProfileScreen
import com.example.csc490seniorproject.screens.RegisterScreen
import com.example.csc490seniorproject.screens.SearchScreen
import com.example.csc490seniorproject.screens.SettingsScreen
import com.example.csc490seniorproject.screens.SongScreen
import com.example.csc490seniorproject.viewmodels.BeatmapEditorVM
import com.example.csc490seniorproject.viewmodels.EditorScreenVM
import com.example.csc490seniorproject.viewmodels.GameVM
import com.example.csc490seniorproject.viewmodels.LandingScreenVM
import com.example.csc490seniorproject.viewmodels.ProfileScreenVM
import com.example.csc490seniorproject.viewmodels.SearchScreenVM
import com.example.csc490seniorproject.viewmodels.SongScreenVM
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.csc490seniorproject.screens.ChatBox

@Composable
fun Nav(
    navController: NavHostController,
    modifier: Modifier
) {
    val app = LocalContext.current.applicationContext as Application

    val landingScreenVM = viewModel { LandingScreenVM(app) }
    val searchScreenVM = viewModel { SearchScreenVM(app) }
    val profileScreenVM = viewModel { ProfileScreenVM(app) }
    val songScreenVM = viewModel { SongScreenVM(app) }
    var currentUsername by rememberSaveable {
        mutableStateOf("Username")
    }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null && currentUsername == "Username") {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { document ->
                    document.getString("username")?.let { username ->
                        currentUsername = username
                    }
                }
                .addOnFailureListener { e ->
                    println("NAV: Failed to resolve returning user's username: $e")
                }
        }
    }

    NavHost(
        navController = navController,
        startDestination =
            if (FirebaseAuth.getInstance().currentUser != null) "LandingScreen" else "LoginScreen",
        modifier = modifier
    ) {

        composable(route = "LoginScreen") {
            LoginScreen(
                onLoginSuccess = { username ->
                    currentUsername = username

                    navController.navigate("LandingScreen") {
                        popUpTo("LoginScreen") {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onRegisterClick = {
                    navController.navigate("RegisterScreen")
                }
            )
        }

        composable(route = "RegisterScreen") {
            RegisterScreen(
                onRegistrationSuccess = { username ->
                    currentUsername = username

                    navController.navigate("LandingScreen") {
                        popUpTo("LoginScreen") {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onLoginClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(route = "LandingScreen") {
            LandingScreen(
                viewModel = landingScreenVM,
                navController = navController,
                username = currentUsername
            )
        }

        composable("SongScreen/{songId}") { entry ->
            SongScreen(
                viewModel = songScreenVM,
                songId = entry.arguments?.getString("songId").orEmpty(),
                navController = navController
            )
        }

        composable(route = "ProfileScreen") {
            ProfileScreen(
                viewModel = profileScreenVM,
                navController = navController,
                username = currentUsername,
                profileID = null
            )
        }

        composable(route = "ProfileScreen/{uid}/{username}") { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid")
            val username = backStackEntry.arguments?.getString("username") ?: ""

            ProfileScreen(
                viewModel = profileScreenVM,
                navController = navController,
                username = username,
                profileID = uid
            )
        }

        composable(route = "SearchScreen") {
            SearchScreen(
                viewModel = searchScreenVM,
                navController = navController,
            )
        }

        composable(route = "MessagesScreen") {
            ChatBox(
                modifier = Modifier.fillMaxSize(),
                navController = navController
            )
        }

        // MySongsVM is created inside the screen with viewModel()
        composable(route = "MySongsScreen") {
            MySongsScreen(navController = navController)
        }

        composable(route = "SettingsScreen") {
            SettingsScreen(
                viewModel = landingScreenVM,
                onProfileClick = {
                    navController.navigate("ProfileScreen")
                },
                onBlockedUsersClick = {
                    navController.navigate("BlockedUsersScreen")
                },
                onLogoutClick = {
                    FirebaseAuth.getInstance().signOut()
                    currentUsername = "Username"

                    navController.navigate("LoginScreen") {
                        popUpTo(navController.graph.id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(route = "BlockedUsersScreen") {
            BlockedUsersScreen(navController = navController)
        }

        composable(
            route = "EditorScreen/{songId}",
            arguments = listOf(navArgument("songId") { type = NavType.StringType })
        ) { entry ->
            val songId = entry.arguments?.getString("songId") ?: return@composable
            val editorViewModel: EditorScreenVM = viewModel()
            LaunchedEffect(songId) { editorViewModel.openSong(songId) }
            EditorScreen(
                viewModel = editorViewModel,
                navController = navController,
                modifier = Modifier
            )
        }

        composable(
            route = "BeatmapEditorScreen/{songId}",
            arguments = listOf(navArgument("songId") { type = NavType.StringType })
        ) { entry ->
            val songId = entry.arguments?.getString("songId") ?: return@composable
            val beatmapViewModel: BeatmapEditorVM = viewModel()
            LaunchedEffect(songId) { beatmapViewModel.openBeatmap(songId) }
            BeatmapEditorScreen(
                viewModel = beatmapViewModel,
                navController = navController,
                modifier = Modifier
            )
        }

        composable(
            route = "GameScreen/{songId}?roomId={roomId}",
            arguments = listOf(
                navArgument("songId") {
                    type = NavType.StringType
                },
                navArgument("roomId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->

            val songId = entry.arguments?.getString("songId")
                ?: return@composable

            val roomId = entry.arguments?.getString("roomId")

            val gameViewModel: GameVM = viewModel()

            val result by produceState<Result<Beatmap?>?>(
                initialValue = null,
                songId
            ) {
                value = try {
                    Result.success(SongRepository.loadBeatmap(songId))
                } catch (e: Exception) {
                    Log.e("GameLoad", "failed for $songId", e)
                    Result.failure(e)
                }
            }

            when {
                result == null -> {
                    Box(
                        Modifier.fillMaxSize(),
                        Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                result!!.isFailure -> {
                    Box(
                        Modifier.fillMaxSize(),
                        Alignment.Center
                    ) {
                        Text(
                            "Couldn't load beatmap: ${result!!.exceptionOrNull()?.message}"
                        )
                    }
                }

                result!!.getOrNull() == null -> {
                    Box(
                        Modifier.fillMaxSize(),
                        Alignment.Center
                    ) {
                        Text("No beatmap found.")
                    }
                }

                else -> {
                    GameScreen(
                        viewModel = gameViewModel,
                        beatmap = result!!.getOrNull()!!,
                        navController = navController,
                        multiplayerRoomId = roomId
                    )
                }
            }
        }
    }
}