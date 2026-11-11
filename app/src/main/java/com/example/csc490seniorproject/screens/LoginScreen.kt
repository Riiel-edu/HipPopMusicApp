package com.example.csc490seniorproject.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.csc490seniorproject.data.UserSession
import com.example.csc490seniorproject.data.getUserProfile
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme

@Composable
fun LoginScreen( onLoginSuccess: (String) -> Unit, onRegisterClick: () -> Unit) {

    val context = LocalContext.current
    val userSession = remember { UserSession(context) }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val purple = MaterialTheme.colorScheme.primary
    val lightPurple = MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightPurple)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "HipPop",
            color = purple,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Welcome back!",
            fontSize = 25.sp,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Log in to continue discovering music.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = ""
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = ""
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        passwordVisible = !passwordVisible
                    }
                ) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (passwordVisible) {
                            "Hide password"
                        } else {
                            "Show password"
                        }
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp))

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }

        TextButton(
            onClick = {
                // Password reset can be added later.
            }
        ) {
            Text(
                text = "Forgot password?",
                color = purple
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter your email and password."
                } else {
                    // No more username -> email lookup — signs in directly, so
                    // this no longer depends on usernames/{key} having an
                    // email field, and works for every account regardless of
                    // when it was created.
                    FirebaseAuth.getInstance()
                        .signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { authResult ->
                            val uid = authResult.user!!.uid
                            getUserProfile(
                                uid = uid,
                                onSuccess = { profile ->
                                    scope.launch {
                                        userSession.saveSession(uid, profile.username)
                                        onLoginSuccess(profile.username)
                                    }
                                },
                                onFailure = { e ->
                                    errorMessage = "Logged in, but couldn't load profile: ${e.localizedMessage}"
                                }
                            )
                        }
                        .addOnFailureListener { e ->
                            errorMessage = "Your email or password is incorrect."
                        }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = MaterialTheme.colorScheme.onPrimary),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "Log In",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onRegisterClick) {
            Text(text = "Don’t have an account? Sign up", color = purple)
        }
    }

}