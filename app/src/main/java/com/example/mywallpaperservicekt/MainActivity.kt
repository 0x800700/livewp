package com.example.mywallpaperservicekt

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mywallpaperservicekt.ui.theme.MyWallpaperServicektTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWallpaperServicektTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WallpaperScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun WallpaperScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "HyperJump Live Wallpaper",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            try {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                intent.putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, MyWallpaperService::class.java)
                )
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback for devices that don't support the direct intent
                try {
                    val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(context, "Error opening wallpaper picker", Toast.LENGTH_LONG).show()
                }
            }
        }) {
            Text("Set Wallpaper")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }) {
            Text("Settings")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyWallpaperServicektTheme {
        WallpaperScreen()
    }
}