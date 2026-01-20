package com.example.mywallpaperservicekt

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLES20
import android.util.Log
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.ArrayList
import java.util.Random
import kotlin.math.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyWallpaperService : GLWallpaperService() {

    override fun getNewRenderer(): Renderer {
        return EnergyBurstRenderer(this)
    }

    inner class EnergyBurstRenderer(private val context: Context) : Renderer, 
        SharedPreferences.OnSharedPreferenceChangeListener, SensorEventListener {
        
        // ============== SHADERS ==============
        
        // V12 Energy Burst Vertex Shader
        private val vertexShaderCode = """
            attribute vec3 aDirection;
            attribute vec4 aData;
            attribute vec4 aColor;
            attribute float aLayer;
            
            uniform float uTime;
            uniform float uPulse; // V12: Energy pulse
            uniform vec2 uParallax;
            uniform float uAspectRatio;
            
            varying vec4 vColor;
            varying float vType;
            varying float vAngle;
            
            vec3 rotateX(vec3 p, float a) {
                float s = sin(a);
                float c = cos(a);
                return vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c);
            }
            
            vec3 rotateY(vec3 p, float a) {
                float s = sin(a);
                float c = cos(a);
                return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c);
            }
            
            void main() {
                float offset = aData.x;
                float speed = aData.y;
                float size = aData.z;
                vType = aData.w;
                
                // Continuous radial burst
                float t = fract(uTime * speed * 0.15 + offset);
                
                // Exponential flow: starts slow near core, accelerates outward
                // V12: Pulse affects distance near the core
                float dist = pow(t, 2.5) * 5.0 + (1.0 - t) * uPulse * 0.1; 
                
                vec3 pos = aDirection * dist;
                
                // Layered Parallax: Closer layers (0.0) move more, FAR layers (1.0) move least
                float parallaxScale = (1.0 - aLayer) * 0.8; 
                pos = rotateY(pos, uParallax.x * parallaxScale);
                pos = rotateX(pos, uParallax.y * parallaxScale);
                
                // Project
                float z = pos.z + 3.0; // View distance
                if (z < 0.1) z = 0.1;
                
                gl_Position = vec4(pos.x / uAspectRatio, pos.y, 0.0, z);
                
                // Point size attenuation + Pulse swell
                gl_PointSize = size * (4.0 / z) * (1.0 + (1.0 - t) * uPulse * 0.3);
                
                // Angle for fragment shader orientation (Points to movement direction)
                vAngle = atan(aDirection.y, aDirection.x);
                
                // Fade logic: High alpha near center (t=0), fades out at edges
                float alpha = 1.0;
                if (t < 0.1) alpha = t * 10.0; // Fade in from core
                if (t > 0.7) alpha = 1.0 - (t - 0.7) * 3.3; // Fade out at boundary
                
                // Brightness boost for center "Core" + Jitter
                float jitter = sin(uTime * 10.0 + offset * 100.0) * 0.1;
                float brightness = 1.0 + (1.0 - t) * 0.8 + jitter;
                vColor = vec4(aColor.rgb * brightness, aColor.a * alpha);
            }
        """.trimIndent()

        private val fragmentShaderCode = """
            precision mediump float;
            varying vec4 vColor;
            varying float vType;
            varying float vAngle;
            
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                
                // Rotate coord based on movement direction
                float s = sin(vAngle);
                float c = cos(vAngle);
                vec2 rotatedCoord = vec2(coord.x * c + coord.y * s, -coord.x * s + coord.y * c);
                
                float mask = 0.0;
                
                if (vType < 0.5) { 
                    // Type 0: Shard (Diamond-like jagged fragment) - REVERTED to preferred shape
                    float d = abs(rotatedCoord.x) + abs(rotatedCoord.y);
                    mask = 1.0 - smoothstep(0.3, 0.5, d);
                } else if (vType < 1.5) {
                    // Type 1: Short Line
                    float d = abs(rotatedCoord.y) * 4.0 + abs(rotatedCoord.x);
                    mask = 1.0 - smoothstep(0.2, 0.4, d);
                } else {
                    // Type 2: Elongated Streak
                    float d = abs(rotatedCoord.y) * 8.0 + abs(rotatedCoord.x) * 0.5;
                    mask = 1.0 - smoothstep(0.2, 0.5, d);
                }
                
                if (mask < 0.01) discard;
                gl_FragColor = vec4(vColor.rgb, vColor.a * mask);
            }
        """.trimIndent()
        
        // ============== DATA ==============
        
        private val NUM_PARTICLES = 4000
        private var particleProgram = 0
        
        private var directionBuffer: FloatBuffer? = null
        private var dataBuffer: FloatBuffer? = null // offset, speed, size, type
        private var colorBuffer: FloatBuffer? = null
        private var layerBuffer: FloatBuffer? = null
        
        private var time = 0f
        private var aspectRatio = 1f
        
        private var baseSpeed = 0.08f
        private var warpSpeed = 0.35f
        private var currentSpeed = 0.08f
        
        private var sensorManager: SensorManager? = null
        private var gyroscope: Sensor? = null
        private var parallaxX = 0f
        private var parallaxY = 0f
        private var targetParallaxX = 0f
        private var targetParallaxY = 0f
        
        private var prefs: SharedPreferences = context.getSharedPreferences("hyperjump_prefs", Context.MODE_PRIVATE)
        
        init {
            prefs.registerOnSharedPreferenceChangeListener(this)
            generateParticles()
            
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        }

        private fun generateParticles() {
            val dirData = FloatArray(NUM_PARTICLES * 3)
            val infoData = FloatArray(NUM_PARTICLES * 4)
            val colData = FloatArray(NUM_PARTICLES * 4)
            val lyrData = FloatArray(NUM_PARTICLES)
            
            val random = kotlin.random.Random(System.currentTimeMillis())
            
            val colors = listOf(
                Color.rgb(0, 200, 255),   // Cyan
                Color.rgb(80, 100, 255),  // Blue
                Color.rgb(200, 50, 255),  // Purple
                Color.rgb(255, 50, 120),  // Pink/Magenta
                Color.rgb(255, 80, 50)    // Red/Orange
            )
            
            for (i in 0 until NUM_PARTICLES) {
                // Radial Directions
                val phi = random.nextFloat() * PI.toFloat() * 2f
                val theta = acos(random.nextDouble() * 2.0 - 1.0).toFloat()
                
                dirData[i*3+0] = sin(theta.toDouble()).toFloat() * cos(phi.toDouble()).toFloat()
                dirData[i*3+1] = sin(theta.toDouble()).toFloat() * sin(phi.toDouble()).toFloat()
                dirData[i*3+2] = cos(theta.toDouble()).toFloat()
                
                // Layers: 0 (Near) to 1 (Far/Core)
                val layer = random.nextFloat()
                lyrData[i] = layer
                
                // Correlate: Near particles are faster and larger
                val nearMultiplier = 1.0f - layer 
                
                infoData[i*4+0] = random.nextFloat() // Offset
                infoData[i*4+1] = 0.5f + nearMultiplier * 1.5f // Speed correlation
                infoData[i*4+2] = 2.0f + nearMultiplier * 15.0f // Size correlation
                infoData[i*4+3] = random.nextInt(3).toFloat() // Type (0, 1, 2)
                
                // Colors
                val c = colors.random()
                colData[i*4+0] = Color.red(c) / 255f
                colData[i*4+1] = Color.green(c) / 255f
                colData[i*4+2] = Color.blue(c) / 255f
                colData[i*4+3] = 1.0f
            }
            
            directionBuffer = ByteBuffer.allocateDirect(dirData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(dirData)
            directionBuffer?.position(0)
            
            dataBuffer = ByteBuffer.allocateDirect(infoData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(infoData)
            dataBuffer?.position(0)
            
            colorBuffer = ByteBuffer.allocateDirect(colData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(colData)
            colorBuffer?.position(0)
            
            layerBuffer = ByteBuffer.allocateDirect(lyrData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(lyrData)
            layerBuffer?.position(0)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {}

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0.0f, 0.0f, 0.02f, 1f) 
            particleProgram = createProgram(vertexShaderCode, fragmentShaderCode)
            gyroscope?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }

        override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
            aspectRatio = w.toFloat() / h.toFloat()
            GLES20.glViewport(0, 0, w, h)
        }
        
        fun setWarp(active: Boolean) {
            currentSpeed = if (active) warpSpeed else baseSpeed
        }

        override fun onDrawFrame(gl: GL10?) {
            // Speed pulsation: subtle breathing rhythm
            val pulseSpeed = currentSpeed * (1.0f + sin(time * 0.5f) * 0.15f)
            time += (pulseSpeed * 0.1f)
            
            parallaxX += (targetParallaxX - parallaxX) * 0.08f
            parallaxY += (targetParallaxY - parallaxY) * 0.08f
            
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE) 
            
            GLES20.glUseProgram(particleProgram)
            
            GLES20.glUniform1f(GLES20.glGetUniformLocation(particleProgram, "uTime"), time)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(particleProgram, "uPulse"), sin(time * 2.0f) * 0.5f + 0.5f)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(particleProgram, "uAspectRatio"), aspectRatio)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(particleProgram, "uParallax"), parallaxX, parallaxY)
            
            val dirHandle = GLES20.glGetAttribLocation(particleProgram, "aDirection")
            GLES20.glEnableVertexAttribArray(dirHandle)
            GLES20.glVertexAttribPointer(dirHandle, 3, GLES20.GL_FLOAT, false, 0, directionBuffer)
            
            val dataHandle = GLES20.glGetAttribLocation(particleProgram, "aData")
            GLES20.glEnableVertexAttribArray(dataHandle)
            GLES20.glVertexAttribPointer(dataHandle, 4, GLES20.GL_FLOAT, false, 0, dataBuffer)
            
            val colHandle = GLES20.glGetAttribLocation(particleProgram, "aColor")
            GLES20.glEnableVertexAttribArray(colHandle)
            GLES20.glVertexAttribPointer(colHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
            
            val lyrHandle = GLES20.glGetAttribLocation(particleProgram, "aLayer")
            GLES20.glEnableVertexAttribArray(lyrHandle)
            GLES20.glVertexAttribPointer(lyrHandle, 1, GLES20.GL_FLOAT, false, 0, layerBuffer)
            
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, NUM_PARTICLES)
            
            GLES20.glDisableVertexAttribArray(dirHandle)
            GLES20.glDisableVertexAttribArray(dataHandle)
            GLES20.glDisableVertexAttribArray(colHandle)
            GLES20.glDisableVertexAttribArray(lyrHandle)
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                if (it.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    targetParallaxX += it.values[1] * 0.12f
                    targetParallaxY += it.values[0] * 0.12f
                    targetParallaxX = targetParallaxX.coerceIn(-0.6f, 0.6f)
                    targetParallaxY = targetParallaxY.coerceIn(-0.6f, 0.6f)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            return prog
        }
        
        private fun loadShader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) Log.e("V12", "Compile Error: ${GLES20.glGetShaderInfoLog(s)}")
            return s
        }
        
        fun cleanup() {
            sensorManager?.unregisterListener(this)
        }
    }
    
    override fun onCreateEngine(): Engine {
        return EnergyEngine()
    }
    
    inner class EnergyEngine : GLWallpaperService.GLEngine() {
        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (renderer is EnergyBurstRenderer) {
                val r = renderer as EnergyBurstRenderer
                when (event?.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> r.setWarp(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> r.setWarp(false)
                }
            }
        }
        
        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (!visible && renderer is EnergyBurstRenderer) (renderer as EnergyBurstRenderer).cleanup()
        }
    }
}