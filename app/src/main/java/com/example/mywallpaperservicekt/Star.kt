package com.example.mywallpaperservicekt

import android.graphics.Color

class Star(
    var x: Float, 
    var y: Float, 
    var z: Float, 
    var color: Int = Color.WHITE,
    var twistPhase: Float = 0f,      // Random phase offset for unique starting angle
    var twistFreq: Float = 1f,        // Random frequency multiplier (0.5 to 1.5)
    var radialOscillation: Float = 0f // Random offset for breathing effect
) {
    companion object {
    }
}


