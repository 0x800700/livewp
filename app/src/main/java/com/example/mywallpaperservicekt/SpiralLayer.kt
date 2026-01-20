package com.example.mywallpaperservicekt

import android.graphics.Color

data class SpiralLayer(
    val depth: Float,           // 0.0 (far) to 1.0 (near)
    val lineThickness: Float,   // OpenGL line width
    val brightness: Float,      // Alpha multiplier
    val parallaxFactor: Float,  // How much this layer moves with gyro
    val color: Int,             // Neon color
    var rotation: Float = 0f    // Current rotation angle
)
