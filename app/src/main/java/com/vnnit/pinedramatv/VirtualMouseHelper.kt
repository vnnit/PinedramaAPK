package com.vnnit.pinedramatv

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

class VirtualMouseHelper(
    private val rootView: ViewGroup,
    private val webView: View,
    private val cursorView: View
) {
    var cursorX = 500f
    var cursorY = 400f
    private val baseStep = 36f
    var isEnabled = true

    init {
        cursorView.visibility = if (isEnabled) View.VISIBLE else View.GONE
        rootView.post {
            cursorX = rootView.width / 2f
            cursorY = rootView.height / 2f
            updateCursorPosition()
        }
    }

    fun setMouseEnabled(enabled: Boolean) {
        isEnabled = enabled
        cursorView.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun updateCursorPosition() {
        val maxX = (rootView.width - cursorView.width).toFloat().coerceAtLeast(0f)
        val maxY = (rootView.height - cursorView.height).toFloat().coerceAtLeast(0f)

        cursorX = cursorX.coerceIn(0f, maxX)
        cursorY = cursorY.coerceIn(0f, maxY)

        cursorView.x = cursorX
        cursorView.y = cursorY
    }

    fun isMouseDpadKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isEnabled) return false

        val keyCode = event.keyCode
        if (!isMouseDpadKey(keyCode)) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            val repeatCount = event.repeatCount
            // Speed up smoothly when holding the key
            val step = baseStep + (repeatCount * 12f).coerceAtMost(80f)

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    cursorX -= step
                    updateCursorPosition()
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    cursorX += step
                    updateCursorPosition()
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cursorY -= step
                    updateCursorPosition()
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    cursorY += step
                    updateCursorPosition()
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    performClick()
                }
            }
        }
        return true
    }

    private fun performClick() {
        val clickX = cursorX + (cursorView.width / 2f)
        val clickY = cursorY + (cursorView.height / 2f)

        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()

        val downEvent = MotionEvent.obtain(
            downTime,
            eventTime,
            MotionEvent.ACTION_DOWN,
            clickX,
            clickY,
            0
        )
        val upEvent = MotionEvent.obtain(
            downTime,
            eventTime + 50,
            MotionEvent.ACTION_UP,
            clickX,
            clickY,
            0
        )

        // Dispatch to rootView so both Native buttons (btnDoneLogin) and WebView get clicks
        rootView.dispatchTouchEvent(downEvent)
        rootView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()

        // Visual feedback: animate cursor scale
        cursorView.animate().scaleX(0.6f).scaleY(0.6f).setDuration(60).withEndAction {
            cursorView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
        }.start()
    }
}

