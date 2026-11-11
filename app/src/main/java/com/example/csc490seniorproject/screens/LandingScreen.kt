package com.example.csc490seniorproject.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.csc490seniorproject.R
import com.example.csc490seniorproject.viewmodels.LandingScreenVM

@Composable
fun LandingScreen(viewModel: LandingScreenVM, navController: NavController, username: String) {

    val fredoka = FontFamily(Font(R.font.fredoka_medium, FontWeight.Normal))

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "♫ Welcome Back, $username! ♫",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = fredoka, fontSize = 28.sp,
                    color = Color(0xFF5d36eb)
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 30.dp,
                color = Color(0xFFBFB1FA)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "♪ Dive right back into the music! ♪",
                        fontFamily = fredoka,
                        fontSize = 20.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.5.dp))

                    HorizontalDivider(
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 2.dp
                    )

                    Spacer(modifier = Modifier.height(7.5.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                navController.navigate("MySongsScreen")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5d36eb),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Make New Song \uD83D\uDCBF",
                                fontFamily = fredoka,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                navController.navigate("SearchScreen")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5d36eb),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Find New Songs \uD83D\uDCC0",
                                fontFamily = fredoka,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Surface(
                color = Color(0xFFbfb1fa),
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Today's Top 10 \uD83D\uDD25",
                        fontFamily = fredoka,
                        fontSize = 26.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(2.5.dp))

                    HorizontalDivider(
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 2.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shadowElevation = 8.dp,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(2.dp, Color.Black)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.default_pfp),
                                contentScale = ContentScale.Crop,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Song name",
                                    fontFamily = fredoka,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    "★★★★★",
                                    fontFamily = fredoka,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 4.dp,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Description",
                                    fontFamily = fredoka,
                                    fontSize = 13.sp,
                                    color = Color(0xFF444444),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5d36eb),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Listen \uD83D\uDD0A",
                                fontFamily = fredoka,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                navController.navigate("SongScreen")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5d36eb),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1.6f)
                        ) {
                            Text(
                                "More details ☰",
                                fontFamily = fredoka,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Surface(
                color = Color(0xFFbfb1fa),
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "Friend's Recent Activity",
                        fontFamily = fredoka,
                        fontSize = 26.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}