package com.example.csc490seniorproject.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.csc490seniorproject.data.AgeGate
import com.google.firebase.auth.FirebaseAuth
import com.example.csc490seniorproject.data.registerUserProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun RegisterScreen ( onRegistrationSuccess: (String) -> Unit, onLoginClick: () -> Unit) {

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val purple = Color(0xFF6737F5)
    val lightPurple = Color(0xFFE9E2FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightPurple)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "HipPop", color = purple, fontSize = 42.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Create your account", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)

        Text(text = "Join the community and start sharing music.", color = Color.DarkGray, fontSize = 15.sp)

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(value = username, onValueChange = {
                username = it
                errorMessage = ""
                                                            },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp))

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField( value = email, onValueChange = {
                email = it
                errorMessage = ""
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions( keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp))

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(value = password, onValueChange = {
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

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(value = confirmPassword, onValueChange = {
                confirmPassword = it
                errorMessage = ""
            },
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        confirmPasswordVisible = !confirmPasswordVisible
                    }
                ) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = if (confirmPasswordVisible) {
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

            Text(text = errorMessage, color = Color.Red, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(birthDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "Date of birth")
        }

        if (showDatePicker) {
            val pickerState = rememberDatePickerState(yearRange = 1900..LocalDate.now().year)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let {
                            birthDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            errorMessage = ""
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
            ) { DatePicker(state = pickerState) }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val birth = birthDate

                when {
                    username.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                        errorMessage = "Please complete every field."
                    }

                    !email.contains("@") -> {
                        errorMessage = "Please enter a valid email."
                    }

                    password.length < 6 -> {
                        errorMessage = "Password must contain at least 6 characters."
                    }

                    password != confirmPassword -> {
                        errorMessage = "Passwords do not match."
                    }

                    birth == null -> {
                        errorMessage = "Please enter your date of birth."
                    }

                    AgeGate.ageOf(birth) < AgeGate.MIN_AGE -> {
                        errorMessage = "Sorry, you must be 13 or older to use this app."
                    }

                    else -> {
                        val ageGroup = AgeGate.groupFor(AgeGate.ageOf(birth))
                        FirebaseAuth.getInstance()
                            .createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener { authResult ->
                                val user = authResult.user!!
                                registerUserProfile(
                                    firebaseUser = user,
                                    username = username,
                                    ageGroup = ageGroup,
                                    birthDate = birth.toString(),
                                    onSuccess = { onRegistrationSuccess(username) },
                                    onUsernameTaken = {
                                        errorMessage = "That username is already taken. Please choose another."
                                        user.delete()
                                    },
                                    onFailure = { e ->
                                        errorMessage = "Account created, but profile setup failed: ${e.localizedMessage}"
                                        user.delete()
                                    }
                                )
                            }
                            .addOnFailureListener { e ->
                                errorMessage = e.localizedMessage ?: "Registration failed. Please try again."
                            }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = purple),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(text = "Create Account", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onLoginClick) {
            Text(text = "Already have an account? Log in", color = purple)
        }
    }

}