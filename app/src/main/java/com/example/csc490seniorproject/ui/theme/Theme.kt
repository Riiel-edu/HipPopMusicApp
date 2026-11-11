package com.example.csc490seniorproject.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private const val SETTINGS_FILE = "hippop_settings"
private const val APPEARANCE_KEY = "appearance"

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCDBBFF),
    onPrimary = Color(0xFF32116D),
    primaryContainer = Color(0xFF493079),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DB),
    background = Color(0xFF151218),
    onBackground = Color(0xFFE9E0EC),
    surface = Color(0xFF1D1922),
    onSurface = Color(0xFFE9E0EC),
    surfaceVariant = Color(0xFF30283D),
    onSurfaceVariant = Color(0xFFD0C4DA)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF683CF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0D2FF),
    onPrimaryContainer = Color(0xFF261444),
    secondary = Color(0xFF685879),
    background = Color(0xFFFFF9FF),
    onBackground = Color(0xFF211B27),
    surface = Color(0xFFFFF9FF),
    onSurface = Color(0xFF211B27),
    surfaceVariant = Color(0xFFE0D2FF),
    onSurfaceVariant = Color(0xFF50465C)
)

@Composable
fun rememberAppearance(): String {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences(
            SETTINGS_FILE,
            Context.MODE_PRIVATE
        )
    }

    var appearance by remember(preferences) {
        mutableStateOf(
            preferences.getString(
                APPEARANCE_KEY,
                "System default"
            ) ?: "System default"
        )
    }

    DisposableEffect(preferences) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                if (key == APPEARANCE_KEY) {
                    appearance = prefs.getString(
                        APPEARANCE_KEY,
                        "System default"
                    ) ?: "System default"
                }
            }

        preferences.registerOnSharedPreferenceChangeListener(listener)

        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return appearance
}

fun saveAppearance(context: Context, appearance: String) {
    if (appearance !in listOf("Light", "Dark", "System default")) {
        return
    }

    context.applicationContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).edit().putString(APPEARANCE_KEY, appearance).apply()
}

@Composable
fun CSC490SeniorProjectTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val appearance = rememberAppearance()
    val systemDark = isSystemInDarkTheme()

    val useDarkTheme = darkTheme ?: when (appearance) {
        "Light" -> false
        "Dark" -> true
        else -> systemDark
    }

    val context = LocalContext.current

    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        useDarkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}