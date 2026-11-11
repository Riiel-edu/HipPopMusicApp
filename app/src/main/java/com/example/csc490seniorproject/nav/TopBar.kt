package com.example.csc490seniorproject.nav

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.csc490seniorproject.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(navController: NavHostController) {
    val barColor = NavigationBarDefaults.containerColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
    ) {
        // Area behind Android's time, Wi-Fi and battery icons.
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(barColor)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = barColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(
                        width = 70.dp,
                        height = 48.dp
                    )
                ) {
                    Image(
                        painter = painterResource(R.drawable.hippo_image),
                        contentDescription = "HipPop",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp).align(Alignment.TopCenter)
                    )

                    val letterX = listOf(7, 18, 25, 36, 47, 58)
                    val letterY = listOf(27, 30, 31, 31, 30, 27)
                    val angles = listOf(16f, 10f, 3f, -3f, -10f, -16f)

                    "HIPPOP".forEachIndexed { index, letter ->
                        Text(
                            text = letter.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily(Font(R.font.fredoka_medium, FontWeight.Medium)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.offset(x = letterX[index].dp, y = letterY[index].dp).rotate(angles[index])
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = {
                        navController.navigate("ProfileScreen") {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = {
                        navController.navigate("SettingsScreen") {
                            launchSingleTop = true
                            restoreState = true

                            popUpTo(navController.graph.findStartDestination().id
                            ) {
                                saveState = true
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}