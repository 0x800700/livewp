package com.example.mywallpaperservicekt

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.opengl.GLES20
import android.util.Log
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.ArrayList
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyWallpaperService : GLWallpaperService() {

    override fun getNewRenderer(): Renderer {
        return HyperRenderer(this)
    }

    inner class HyperRenderer(context: Context) : Renderer, SharedPreferences.OnSharedPreferenceChangeListener {
        
        // --- Shaders ---
        private val vertexShaderCode = """
            attribute vec4 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                gl_Position = aPosition;
                vColor = aColor;
            }
        """.trimIndent()

        private val fragmentShaderCode = """
            precision mediump float;
            varying vec4 vColor;
            void main() {
                gl_FragColor = vColor;
            }
        """.trimIndent()
        
        private var programId = 0
        
        // --- Data ---
        private val stars = ArrayList<Star>()
        private var numStars = 400
        private var width = 0
        private var height = 0
        private val random = Random()
        
        // Buffer arrays (reused)
        private lateinit var lineCoords: FloatArray
        private lateinit var lineColors: FloatArray

        // --- Settings ---
        private var prefs: SharedPreferences = context.getSharedPreferences("hyperjump_prefs", Context.MODE_PRIVATE)
        private var baseSpeed = 10f
        private var currentSpeed = 10f
        private var warpSpeed = 50f
        private val maxDepth = 2000f
        private var theme = "scifi"
        
        // Colors
        private val cyan = floatArrayOf(0f, 1f, 1f, 1f)
        private val magenta = floatArrayOf(1f, 0f, 1f, 1f)
        private val electricBlue = floatArrayOf(0f, 0.47f, 1f, 1f)
        private val white = floatArrayOf(1f, 1f, 1f, 1f)
        
        init {
            prefs.registerOnSharedPreferenceChangeListener(this)
            updateSettings()
            createStars()
        }

        // Multi-segment settings
        private val SEGMENTS = 12 
        
        
        // Settings State
        private var settingsDirty = true
        private var targetStarCount = 400
        private var targetTheme = "scifi"
        
        // Buffers
        private var vertexBuffer: FloatBuffer? = null
        private var colorBuffer: FloatBuffer? = null

        private fun updateSettings() {
            // ONLY update primitives here. Defer heavy lifting to GL Thread.
            targetStarCount = prefs.getInt("star_count", 400)
            targetTheme = prefs.getString("theme", "scifi") ?: "scifi"
            settingsDirty = true
        }
        
        // Called on GL Thread
        private fun applySettings() {
            numStars = targetStarCount
            theme = targetTheme
            
            val totalVerts = numStars * SEGMENTS * 2
            // Resize arrays
            lineCoords = FloatArray(totalVerts * 2) 
            lineColors = FloatArray(totalVerts * 4) 
            
            // Resize Native Buffers (Avoid allocation every frame)
            val vBb = ByteBuffer.allocateDirect(lineCoords.size * 4)
            vBb.order(ByteOrder.nativeOrder())
            vertexBuffer = vBb.asFloatBuffer()
            
            val cBb = ByteBuffer.allocateDirect(lineColors.size * 4)
            cBb.order(ByteOrder.nativeOrder())
            colorBuffer = cBb.asFloatBuffer()
            
            // Re-create stars safely
            createStars()
            settingsDirty = false
        }
        
        private fun createStars() {
            stars.clear()
            for (i in 0 until numStars) {
                stars.add(createRandomStar(true))
            }
        }
        
        private fun createRandomStar(randomZ: Boolean): Star {
            val range = 2000f 
            val x = (random.nextFloat() - 0.5f) * range * 4 
            val y = (random.nextFloat() - 0.5f) * range * 4
            val z = if (randomZ) random.nextFloat() * maxDepth else maxDepth
            
            // Chaos parameters
            val twistPhase = random.nextFloat() * Math.PI.toFloat() * 2f
            val twistFreq = 0.5f + random.nextFloat() * 1.0f  // 0.5 to 1.5
            val radialOsc = random.nextFloat() * Math.PI.toFloat() * 2f
            
            val star = Star(x, y, z, Color.WHITE, twistPhase, twistFreq, radialOsc)
            
            star.color = if (theme == "scifi") {
                 val r = random.nextFloat()
                 when {
                     r < 0.33f -> Color.CYAN
                     r < 0.66f -> Color.MAGENTA
                     else -> Color.rgb(0, 100, 255) // Blue
                 }
            } else {
                Color.WHITE
            }
            return star
        }
        
        private fun resetStar(star: Star) {
            val range = 2000f
            star.x = (random.nextFloat() - 0.5f) * range * 4
            star.y = (random.nextFloat() - 0.5f) * range * 4
            star.z = maxDepth
            
            // Re-randomize chaos parameters
            star.twistPhase = random.nextFloat() * Math.PI.toFloat() * 2f
            star.twistFreq = 0.5f + random.nextFloat() * 1.0f
            star.radialOscillation = random.nextFloat() * Math.PI.toFloat() * 2f
            
            star.color = if (theme == "scifi") {
                 val r = random.nextFloat()
                 when {
                     r < 0.33f -> Color.CYAN
                     r < 0.66f -> Color.MAGENTA
                     else -> Color.rgb(0, 100, 255)
                 }
            } else {
                Color.WHITE
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            updateSettings()
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)
            
            // Force init
            settingsDirty = true
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            this.width = width
            this.height = height
            GLES20.glViewport(0, 0, width, height)
        }
        
        // --- Main Loop ---
        fun setWarp(active: Boolean) {
            currentSpeed = if (active) warpSpeed else baseSpeed
        }
        
        private var time = 0f

        override fun onDrawFrame(gl: GL10?) {
             if (settingsDirty) {
                 applySettings()
             }
        
             updateStars()
             fillBuffers()
             
             GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
             GLES20.glUseProgram(programId)
             
             // Update Native Buffers from Arrays
             vertexBuffer?.put(lineCoords)?.position(0)
             colorBuffer?.put(lineColors)?.position(0)
             
             val positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
             GLES20.glEnableVertexAttribArray(positionHandle)
             GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
             
             val colorHandle = GLES20.glGetAttribLocation(programId, "aColor")
             GLES20.glEnableVertexAttribArray(colorHandle)
             GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
             
             // Bloom Pass
             GLES20.glEnable(GLES20.GL_BLEND)
             GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE) 
             
             val count = numStars * SEGMENTS * 2
             
             GLES20.glLineWidth(8f) 
             GLES20.glDrawArrays(GLES20.GL_LINES, 0, count)
             
             // Core Pass
             GLES20.glLineWidth(2f)
             GLES20.glDrawArrays(GLES20.GL_LINES, 0, count)
             
             GLES20.glDisableVertexAttribArray(positionHandle)
             GLES20.glDisableVertexAttribArray(colorHandle)
             GLES20.glDisable(GLES20.GL_BLEND)
        }
        
        private fun updateStars() {
            if (currentSpeed > baseSpeed && (currentSpeed < warpSpeed)) {
            }
            time += 0.01f * (currentSpeed / 10f)
            
            for (star in stars) {
                star.z -= currentSpeed
                if (star.z <= 0) resetStar(star)
            }
        }
        
        private fun fillBuffers() {
            var idxPos = 0
            var idxCol = 0
            
            val aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f

            val twistBase = 0.002f  // Reduced slightly to prevent over-twisting
            val waveFreq = 0.01f
            val waveAmp = 1.2f   // Increased for more chaos
            
            val totalTailLength = if (currentSpeed > baseSpeed) 600f else 300f
            val segmentLen = totalTailLength / SEGMENTS
            
            for (star in stars) {
                if (star.z <= 0) continue
                
                val r = Color.red(star.color) / 255f
                val g = Color.green(star.color) / 255f
                val b = Color.blue(star.color) / 255f
                
                for (i in 0 until SEGMENTS) {
                    val zHead = star.z + i * segmentLen
                    val zTail = star.z + (i + 1) * segmentLen
                    
                    // --- Point A (Head) with Chaos ---
                    val twistA = (maxDepth - zHead) * twistBase * star.twistFreq + time + star.twistPhase
                    val waveA = Math.sin((zHead * waveFreq + time).toDouble()).toFloat() * waveAmp
                    val angleA = twistA + waveA
                    
                    // Radial oscillation (breathing)
                    val radiusModA = 1f + Math.sin((zHead * 0.005f + star.radialOscillation).toDouble()).toFloat() * 0.3f
                    
                    val cosA = Math.cos(angleA.toDouble()).toFloat()
                    val sinA = Math.sin(angleA.toDouble()).toFloat()
                    
                    val rxA = star.x * radiusModA * cosA - star.y * radiusModA * sinA
                    val ryA = star.x * radiusModA * sinA + star.y * radiusModA * cosA
                    
                    val kA = 1.5f / zHead
                    val xA = rxA * kA / aspectRatio
                    val yA = ryA * kA
                    
                    // --- Point B (Tail) with Chaos ---
                    val twistB = (maxDepth - zTail) * twistBase * star.twistFreq + time + star.twistPhase
                    val waveB = Math.sin((zTail * waveFreq + time).toDouble()).toFloat() * waveAmp
                    val angleB = twistB + waveB
                    
                    val radiusModB = 1f + Math.sin((zTail * 0.005f + star.radialOscillation).toDouble()).toFloat() * 0.3f
                    
                    val cosB = Math.cos(angleB.toDouble()).toFloat()
                    val sinB = Math.sin(angleB.toDouble()).toFloat()
                    
                    val rxB = star.x * radiusModB * cosB - star.y * radiusModB * sinB
                    val ryB = star.x * radiusModB * sinB + star.y * radiusModB * cosB
                    
                    val kB = 1.5f / zTail
                    val xB = rxB * kB / aspectRatio
                    val yB = ryB * kB

                    // Alpha gradient
                    val distFade = (1f - (zHead / maxDepth)).coerceIn(0f, 1f)
                    val segFadeHead = 1f - (i.toFloat() / SEGMENTS)
                    val segFadeTail = 1f - ((i + 1).toFloat() / SEGMENTS)
                    
                    val alphaA = distFade * segFadeHead
                    val alphaB = distFade * segFadeTail
                    
                    // Add segment
                    lineCoords[idxPos++] = xA
                    lineCoords[idxPos++] = yA
                    lineColors[idxCol++] = r; lineColors[idxCol++] = g; lineColors[idxCol++] = b; lineColors[idxCol++] = alphaA
                    
                    lineCoords[idxPos++] = xB
                    lineCoords[idxPos++] = yB
                    lineColors[idxCol++] = r; lineColors[idxCol++] = g; lineColors[idxCol++] = b; lineColors[idxCol++] = alphaB
                }
            }
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                 GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }
    }
    
    override fun onCreateEngine(): Engine {
        return HyperEngine()
    }
    
    inner class HyperEngine : GLWallpaperService.GLEngine() {
         override fun onTouchEvent(event: MotionEvent?) {
             super.onTouchEvent(event)
             if (renderer is HyperRenderer) {
                 val r = renderer as HyperRenderer
                 when (event?.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> r.setWarp(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> r.setWarp(false)
                 }
             }
         }
    }
}