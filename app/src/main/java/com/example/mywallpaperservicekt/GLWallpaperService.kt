package com.example.mywallpaperservicekt

import android.content.Context
import android.opengl.EGL14
import android.opengl.GLUtils
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface
import javax.microedition.khronos.opengles.GL10

abstract class GLWallpaperService : WallpaperService() {

    abstract fun getNewRenderer(): Renderer

    interface Renderer {
        fun onSurfaceCreated(gl: GL10?, config: EGLConfig?)
        fun onSurfaceChanged(gl: GL10?, width: Int, height: Int)
        fun onDrawFrame(gl: GL10?)
    }

    open inner class GLEngine : Engine() {
        protected var renderer: Renderer? = null
        private var glThread: GLThread? = null

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
             renderer = getNewRenderer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                glThread?.resumeRendering()
            } else {
                glThread?.pauseRendering()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            if (holder == null) return
            if (glThread == null) {
                glThread = GLThread(holder, renderer!!)
                glThread?.start()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            glThread?.requestExitAndWait()
            glThread = null
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            glThread?.onWindowResize(width, height)
        }
        
        override fun onDestroy() {
            super.onDestroy()
            glThread?.requestExitAndWait()
            glThread = null
        }
    }

    class GLThread(
        private val surfaceHolder: SurfaceHolder,
        private val renderer: Renderer
    ) : Thread() {
        @Volatile private var running = true
        @Volatile private var paused = false
        private var width = 0
        private var height = 0
        private var sizeChanged = false
        private val lock = Object()

        fun requestExitAndWait() {
            running = false
            synchronized(lock) {
                lock.notifyAll()
            }
            try {
                join()
            } catch (e: InterruptedException) {
                currentThread().interrupt()
            }
        }

        fun onWindowResize(w: Int, h: Int) {
            synchronized(lock) {
                width = w
                height = h
                sizeChanged = true
                lock.notifyAll()
            }
        }

        fun pauseRendering() {
            synchronized(lock) {
                paused = true
                lock.notifyAll()
            }
        }

        fun resumeRendering() {
            synchronized(lock) {
                paused = false
                lock.notifyAll()
            }
        }

        override fun run() {
            val egl = EGLContext.getEGL() as EGL10
            val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            
            val version = IntArray(2)
            egl.eglInitialize(display, version)
            
            val configAttribs = intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                EGL10.EGL_NONE
            )
            
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            egl.eglChooseConfig(display, configAttribs, configs, 1, numConfigs)
            val config = configs[0]

            val contextAttribs = intArrayOf(0x3098, 2, EGL10.EGL_NONE) // EGL_CONTEXT_CLIENT_VERSION, 2
            val context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, contextAttribs)

            var surface: EGLSurface? = null
            var gl: GL10? = null
            
            var surfaceCreated = false

            while (running) {
                synchronized(lock) {
                    while (running && paused) {
                        try { lock.wait() } catch (e: InterruptedException) {}
                    }
                    if (!running) return@synchronized
                }
                
                if (surface == null || sizeChanged) {
                    if (surface != null) {
                        egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                        egl.eglDestroySurface(display, surface)
                    }
                    
                    try {
                        surface = egl.eglCreateWindowSurface(display, config, surfaceHolder, null)
                    } catch (e: Exception) {
                        // Sometimes surface creation fails if surface is not valid yet
                        try { Thread.sleep(100) } catch (e2: Exception) {}
                        continue
                    }
                    
                    if (!egl.eglMakeCurrent(display, surface, surface, context)) {
                         try { Thread.sleep(100) } catch (e2: Exception) {}
                         continue
                    }
                    
                    gl = context.gl as GL10
                    
                    if (!surfaceCreated) {
                        renderer.onSurfaceCreated(gl, config)
                        surfaceCreated = true
                    }
                    
                    if (sizeChanged) {
                        renderer.onSurfaceChanged(gl, width, height)
                        sizeChanged = false
                    }
                }

                renderer.onDrawFrame(gl)
                egl.eglSwapBuffers(display, surface)
                
                // Cap frame rate slightly to avoid excessive battery drain if loop is too tight?
                // For "Max" FPS we let it fly, effectively vsync limited by eglSwapBuffers (usually)
                // If eglSwapBuffers blocks, we are good.
            }
            
            egl.eglDestroyContext(display, context)
            egl.eglTerminate(display)
        }
    }
}
