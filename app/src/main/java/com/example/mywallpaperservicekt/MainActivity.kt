package com.example.mywallpaperservicekt

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.opengl.GLSurfaceView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mywallpaperservicekt.ui.theme.MyWallpaperServicektTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWallpaperServicektTheme(darkTheme = true, dynamicColor = false) {
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
    var renderer by remember { mutableStateOf<EnergyBurstRenderer?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Live Wallpaper Preview
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    val r = EnergyBurstRenderer(ctx)
                    renderer = r
                    setRenderer(object : GLSurfaceView.Renderer {
                        override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
                            r.onSurfaceCreated(gl, config)
                        }
                        override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
                            r.onSurfaceChanged(gl, width, height)
                        }
                        override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
                            r.onDrawFrame(gl)
                        }
                    })
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                renderer?.cleanup()
            }
        )

        // 2. Button Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "HyperJump Live Preview",
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        intent.putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(context, MyWallpaperService::class.java)
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            Toast.makeText(context, "Error opening wallpaper picker", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) {
                Text("Apply to Home Screen")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Open Preview Settings")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}