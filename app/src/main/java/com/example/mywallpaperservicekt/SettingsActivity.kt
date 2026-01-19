package com.example.mywallpaperservicekt

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mywallpaperservicekt.ui.theme.MyWallpaperServicektTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWallpaperServicektTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hyperjump_prefs", Context.MODE_PRIVATE) }
    
    // State
    var fpsOption by remember { mutableStateOf(prefs.getString("fps_option", "60") ?: "60") }
    var starCount by remember { mutableStateOf(prefs.getInt("star_count", 400).toFloat()) }
    var themeOption by remember { mutableStateOf(prefs.getString("theme", "scifi") ?: "scifi") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Wallpaper Settings", style = MaterialTheme.typography.headlineMedium)

        // FPS Selection
        Text("Target FPS", style = MaterialTheme.typography.titleMedium)
        val fpsOptions = listOf("30", "60", "90", "120", "Max")
        Row(modifier = Modifier.selectableGroup()) {
            fpsOptions.forEach { text ->
                RadioButton(
                    selected = (text == fpsOption),
                    onClick = {
                        fpsOption = text
                        prefs.edit().putString("fps_option", text).apply()
                    }
                )
                Text(
                    text = text,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 16.dp)
                )
            }
        }

        // Star Count
        Text("Star Count: ${starCount.toInt()}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = starCount,
            onValueChange = { 
                starCount = it
                prefs.edit().putInt("star_count", it.toInt()).apply()
            },
            valueRange = 100f..2000f
        )

        // Theme Selection
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = themeOption == "classic",
                onClick = {
                    themeOption = "classic"
                    prefs.edit().putString("theme", "classic").apply()
                }
            )
            Text("Classic White", modifier = Modifier.padding(end = 16.dp))
            
            RadioButton(
                selected = themeOption == "scifi",
                onClick = {
                    themeOption = "scifi"
                    prefs.edit().putString("theme", "scifi").apply()
                }
            )
            Text("Sci-Fi (Cyan/Magenta)")
        }
        
        Text(
            "Note: Settings apply immediately, but you may need to reset the wallpaper or wait a moment for updates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
