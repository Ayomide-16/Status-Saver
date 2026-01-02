package com.statussaver.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Handles gesture detection for video playback controls.
 * Supports:
 * - Double-tap left/right for seek (±3 seconds)
 * - Long-press left/right for speed control
 * - Horizontal swipe for navigation (handled by ViewPager2)
 */
class VideoGestureHandler(
    context: Context,
    private val view: View,
    private val listener: GestureListener
) {
    
    interface GestureListener {
        fun onDoubleTapSeek(forward: Boolean) // true = forward, false = rewind
        fun onSpeedChange(speed: Float)
        fun onSpeedReset()
        fun onSingleTap()
    }
    
    companion object {
        const val SEEK_DURATION_MS = 3000 // 3 seconds
        private const val LONG_PRESS_DELAY_MS = 400L
        private const val SPEED_SLOW_1 = 0.75f
        private const val SPEED_SLOW_2 = 0.5f
        private const val SPEED_FAST_1 = 1.25f
        private const val SPEED_FAST_2 = 1.5f
        private const val SPEED_FAST_3 = 2.0f
        private const val SPEED_NORMAL = 1.0f
    }
    
    private var isLongPressing = false
    private var longPressStartTime = 0L
    private var currentSpeed = SPEED_NORMAL
    private var longPressZone: Zone = Zone.CENTER
    
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        isLongPressing = true
        longPressStartTime = System.currentTimeMillis()
        handleLongPressStart()
    }
    
    private val speedIncreaseRunnable = object : Runnable {
        override fun run() {
            if (isLongPressing) {
                increaseSpeedStep()
                handler.postDelayed(this, 800L) // Increase speed every 800ms
            }
        }
    }
    
    private enum class Zone {
        LEFT, CENTER, RIGHT
    }
    
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            listener.onSingleTap()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val zone = getZone(e.x)
            when (zone) {
                Zone.LEFT -> listener.onDoubleTapSeek(false) // Rewind
                Zone.RIGHT -> listener.onDoubleTapSeek(true) // Forward
                Zone.CENTER -> listener.onSingleTap() // Toggle controls
            }
            return true
        }
        
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
    })
    
    private fun getZone(x: Float): Zone {
        val width = view.width
        return when {
            x < width * 0.35f -> Zone.LEFT
            x > width * 0.65f -> Zone.RIGHT
            else -> Zone.CENTER
        }
    }
    
    fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressZone = getZone(event.x)
                if (longPressZone != Zone.CENTER) {
                    handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY_MS)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(speedIncreaseRunnable)
                if (isLongPressing) {
                    isLongPressing = false
                    currentSpeed = SPEED_NORMAL
                    listener.onSpeedReset()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Cancel long press if moved too far
                if (!isLongPressing) {
                    val currentZone = getZone(event.x)
                    if (currentZone != longPressZone) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                }
            }
        }
        
        return true
    }
    
    private fun handleLongPressStart() {
        currentSpeed = when (longPressZone) {
            Zone.LEFT -> SPEED_SLOW_1
            Zone.RIGHT -> SPEED_FAST_1
            Zone.CENTER -> SPEED_NORMAL
        }
        listener.onSpeedChange(currentSpeed)
        
        // Start gradual speed increase/decrease
        handler.postDelayed(speedIncreaseRunnable, 800L)
    }
    
    private fun increaseSpeedStep() {
        currentSpeed = when (longPressZone) {
            Zone.LEFT -> {
                when {
                    currentSpeed >= SPEED_SLOW_1 -> SPEED_SLOW_2
                    else -> SPEED_SLOW_2
                }
            }
            Zone.RIGHT -> {
                when {
                    currentSpeed <= SPEED_FAST_1 -> SPEED_FAST_2
                    currentSpeed <= SPEED_FAST_2 -> SPEED_FAST_3
                    else -> SPEED_FAST_3
                }
            }
            Zone.CENTER -> SPEED_NORMAL
        }
        listener.onSpeedChange(currentSpeed)
    }
    
    fun cleanup() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(speedIncreaseRunnable)
    }
}
