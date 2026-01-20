package com.example.mywallpaperservicekt

import android.graphics.Color
import kotlin.random.Random

/**
 * A light ray in the hyperspace tunnel.
 * Born at center (z=far), flies TOWARD viewer (z=near), expands as it approaches.
 */
class Particle(
    var angle: Float,        // Position angle around center
    var z: Float,            // Depth: 0 = at viewer, 1 = far (center)
    val layer: Float,        // For parallax
    var color: Int,
    val twistRate: Float     // Individual spiral rate
) {
    companion object {
        private val COLORS = listOf(
            Color.rgb(0, 220, 255),   // Bright Cyan
            Color.rgb(80, 120, 255),  // Blue
            Color.rgb(180, 50, 255),  // Purple
            Color.rgb(255, 50, 200),  // Magenta
            Color.rgb(100, 255, 220)  // Teal
        )
        
        fun createRandom(): Particle {
            return Particle(
                angle = Random.nextFloat() * Math.PI.toFloat() * 2f,
                z = Random.nextFloat(), // Random depth
                layer = Random.nextFloat(),
                color = COLORS.random(),
                twistRate = 0.3f + Random.nextFloat() * 0.4f // Varying twist
            )
        }
    }
    
    fun respawnAtCenter() {
        angle = Random.nextFloat() * Math.PI.toFloat() * 2f
        z = 1f // Start at far (center of screen)
        color = COLORS.random()
    }
}
