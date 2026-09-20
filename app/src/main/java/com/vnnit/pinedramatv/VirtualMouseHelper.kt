package com.vnnit.pinedramatv

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView

class VirtualMouseHelper(
    private val rootView: ViewGroup,
    private val webView: WebView,
    private val cursorView: View
) {
    var cursorX = 500f
    var cursorY = 400f
    private val baseStep = 28f
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

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isEnabled) return false

        val repeatCount = event?.repeatCount ?: 0
        // Speed up when holding the key
        val step = baseStep + (repeatCount * 8f).coerceAtMost(60f)

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                cursorX -= step
                updateCursorPosition()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                cursorX += step
                updateCursorPosition()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                cursorY -= step
                updateCursorPosition()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                cursorY += step
                updateCursorPosition()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                performClick()
                return true
            }
        }
        return false
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

        webView.dispatchTouchEvent(downEvent)
        webView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()

        // Visual feedback: briefly animate cursor
        cursorView.animate().scaleX(0.7f).scaleY(0.7f).setDuration(80).withEndAction {
            cursorView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
        }.start()
    }
}
