package org.futo.inputmethod.gestures

import kotlin.math.abs

enum class SwipeAction { LEFT, RIGHT, UP, DOWN }

/** One action per contact, latched until release or cancellation. */
class SwipeActionDetector {
    companion object {
        private const val ACTIVATION_WIDTHS = 0.85f
        private const val AXIS_RATIO = 1.45f
    }

    var tracking = false
        private set
    var action: SwipeAction? = null
        private set
    private var originX = 0
    private var originY = 0
    private var width = 1

    fun start(x: Int, y: Int, keyWidth: Int) {
        stop()
        tracking = true
        originX = x
        originY = y
        width = keyWidth.coerceAtLeast(1)
    }

    fun stop() {
        tracking = false
        action = null
    }

    fun move(x: Int, y: Int): SwipeAction? {
        if (!tracking || action != null) return null
        val dx = x - originX
        val dy = y - originY
        val threshold = width * ACTIVATION_WIDTHS
        action = when {
            abs(dx) >= threshold && abs(dx) >= abs(dy) * AXIS_RATIO ->
                if (dx < 0) SwipeAction.LEFT else SwipeAction.RIGHT
            abs(dy) >= threshold && abs(dy) >= abs(dx) * AXIS_RATIO ->
                if (dy < 0) SwipeAction.UP else SwipeAction.DOWN
            else -> null
        }
        return action
    }
}
