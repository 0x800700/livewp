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