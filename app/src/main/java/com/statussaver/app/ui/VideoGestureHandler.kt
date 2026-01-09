package com.statussaver.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Handles gesture detection for video playback controls.
 * Supports:
 * - Double-tap left: rewind 3 seconds
 * - Double-tap right: forward 3 seconds
 * - Double-tap center: toggle play/pause
 * - Long-press left: reverse at 2x speed
 * - Long-press right: forward at 2x speed
 */
class VideoGestureHandler(
    context: Context,
    private val view: View,
    private val listener: GestureListener
) {
    
    interface GestureListener {
        fun onDoubleTapSeek(forward: Boolean) // true = forward, false = rewind
        fun onDoubleTapCenter() // toggle play/pause
        fun onLongPressStart(isRightSide: Boolean) // true = 2x forward, false = reverse
        fun onLongPressEnd()
        fun onSingleTap()
    }
    
    companion object {
        const val SEEK_DURATION_MS = 3000 // 3 seconds
        private const val LONG_PRESS_DELAY_MS = 300L
    }
    
    private var isLongPressing = false
    private var longPressZone: Zone = Zone.CENTER
    
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (longPressZone != Zone.CENTER) {
            isLongPressing = true
            listener.onLongPressStart(longPressZone == Zone.RIGHT)
        }
    }
    
    private enum class Zone {
        LEFT, CENTER, RIGHT
    }
    
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isLongPressing) {
                listener.onSingleTap()
            }
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Cancel any long press detection
            handler.removeCallbacks(longPressRunnable)
            
            val zone = getZone(e.x)
            android.util.Log.d("VideoSeek", "onDoubleTap: x=${e.x}, viewWidth=${view.width}, zone=$zone")
            when (zone) {
                Zone.LEFT -> listener.onDoubleTapSeek(false) // Rewind 3 seconds
                Zone.RIGHT -> listener.onDoubleTapSeek(true) // Forward 3 seconds
                Zone.CENTER -> listener.onDoubleTapCenter() // Toggle play/pause
            }
            return true
        }
        
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
    })
    
    private fun getZone(x: Float): Zone {
        val width = view.width
        val zone = when {
            x < width * 0.35f -> Zone.LEFT
            x > width * 0.65f -> Zone.RIGHT
            else -> Zone.CENTER
        }
        android.util.Log.d("VideoSeek", "getZone: x=$x, width=$width, threshold35%=${width * 0.35f}, threshold65%=${width * 0.65f}, result=$zone")
        return zone
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
                if (isLongPressing) {
                    isLongPressing = false
                    listener.onLongPressEnd()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Cancel long press if moved to different zone
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
    
    fun cleanup() {
        handler.removeCallbacks(longPressRunnable)
        isLongPressing = false
    }
}
