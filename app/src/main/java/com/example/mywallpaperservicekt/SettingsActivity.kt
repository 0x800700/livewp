package com.example.mywallpaperservicekt

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mywallpaperservicekt.ui.theme.MyWallpaperServicektTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWallpaperServicektTheme(darkTheme = true, dynamicColor = false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent
                ) { innerPadding ->
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
    var burstSpeed by remember { mutableStateOf(prefs.getFloat("burst_speed", 1.0f)) }
    var themeOption by remember { mutableStateOf(prefs.getString("theme", "scifi") ?: "scifi") }
    
    // Multi-slot palette
    val customColors = remember {
        mutableStateListOf(
            prefs.getInt("custom_color_0", android.graphics.Color.WHITE),
            prefs.getInt("custom_color_1", android.graphics.Color.CYAN),
            prefs.getInt("custom_color_2", android.graphics.Color.MAGENTA),
            prefs.getInt("custom_color_3", android.graphics.Color.YELLOW),
            prefs.getInt("custom_color_4", android.graphics.Color.BLUE)
        )
    }
    var selectedSlot by remember { mutableStateOf(0) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.5f), // Glassmorphism-style overlay
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
        Text("Wallpaper Settings", style = MaterialTheme.typography.headlineMedium)

        // FPS Selection - Multi-row to prevent overflow
        Text("Target FPS", style = MaterialTheme.typography.titleMedium)
        val fpsOptions = listOf("30", "60", "90", "120", "Max")
        Column(modifier = Modifier.selectableGroup()) {
            // Split into two rows for portrait safety
            Row(verticalAlignment = Alignment.CenterVertically) {
                fpsOptions.take(3).forEach { text ->
                    RadioButton(
                        selected = (text == fpsOption),
                        onClick = {
                            fpsOption = text
                            prefs.edit().putString("fps_option", text).apply()
                        }
                    )
                    Text(text = text, modifier = Modifier.padding(end = 16.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                fpsOptions.drop(3).forEach { text ->
                    RadioButton(
                        selected = (text == fpsOption),
                        onClick = {
                            fpsOption = text
                            prefs.edit().putString("fps_option", text).apply()
                        }
                    )
                    Text(text = text, modifier = Modifier.padding(end = 16.dp))
                }
            }
        }

        // Burst Speed (Repurposed from Star Count)
        Text("Burst Speed: ${String.format("%.1fx", burstSpeed)}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = burstSpeed,
            onValueChange = { 
                burstSpeed = it
                prefs.edit().putFloat("burst_speed", it).apply()
            },
            valueRange = 0.5f..4.0f
        )

        // Theme Selection
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = themeOption == "scifi",
                    onClick = {
                        themeOption = "scifi"
                        prefs.edit().putString("theme", "scifi").apply()
                    }
                )
                Text("Sci-Fi (Default)", modifier = Modifier.padding(end = 16.dp))

                RadioButton(
                    selected = themeOption == "custom",
                    onClick = {
                        themeOption = "custom"
                        prefs.edit().putString("theme", "custom").apply()
                    }
                )
                Text("Custom Palette")
            }

            if (themeOption == "custom") {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Palette Slots
                Text("Slot Selection:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    customColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .aspectRatio(1f)
                                .background(androidx.compose.ui.graphics.Color(color), MaterialTheme.shapes.small)
                                .selectable(
                                    selected = selectedSlot == index,
                                    onClick = { selectedSlot = index }
                                )
                                .let { 
                                    if (selectedSlot == index) it.padding(2.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small).padding(2.dp).background(androidx.compose.ui.graphics.Color(color), MaterialTheme.shapes.small)
                                    else it 
                                }
                        )
                    }
                }

                // Grid Color Picker
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pick Color for Slot ${selectedSlot + 1}:", style = MaterialTheme.typography.labelLarge)
                ColorGrid(onColorSelected = { color ->
                    customColors[selectedSlot] = color.toArgb()
                    prefs.edit().putInt("custom_color_$selectedSlot", color.toArgb()).apply()
                })

                TextButton(onClick = {
                    themeOption = "scifi"
                    prefs.edit().putString("theme", "scifi").apply()
                }) {
                    Text("Reset to Default Colors")
                }
            }
        }
        
        Text(
            "Note: Settings apply immediately, but you may need to reset the wallpaper or wait a moment for updates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
}

@Composable
fun ColorGrid(onColorSelected: (androidx.compose.ui.graphics.Color) -> Unit) {
    val hues = listOf(0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f)

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        // Grayscale row
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0..11) {
                val b = i / 11f
                ColorCircle(androidx.compose.ui.graphics.Color(b, b, b), onColorSelected)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Hue grid (simplified)
        for (b in listOf(1.0f, 0.7f, 0.4f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                hues.forEach { h ->
                    val color = androidx.compose.ui.graphics.Color.hsv(h, 0.8f, b)
                    ColorCircle(color, onColorSelected)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun ColorCircle(color: androidx.compose.ui.graphics.Color, onColorSelected: (androidx.compose.ui.graphics.Color) -> Unit) {
    Surface(
        onClick = { onColorSelected(color) },
        modifier = Modifier.size(24.dp).aspectRatio(1f),
        shape = MaterialTheme.shapes.extraSmall,
        color = color
    ) {}
}
