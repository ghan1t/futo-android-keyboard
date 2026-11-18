package org.futo.inputmethod.gestures

import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.latin.common.Constants

class FleksyTouchController(private val dispatch: (SwipeAction) -> Boolean) {
    companion object {
        @JvmStatic fun accepts(key: Key): Boolean {
            if (key.isModifier || key.hasFlick || key.isRepeatable || !key.isEnabled) return false
            if (key.code == Constants.CODE_OUTPUT_TEXT) return !key.outputText.isNullOrBlank()
            return Character.isValidCodePoint(key.code) && !Character.isISOControl(key.code)
                    && key.code != Constants.CODE_SPACE
        }
    }

    private val detector = SwipeActionDetector()
    val tracking get() = detector.tracking

    fun start(x: Int, y: Int, width: Int) = detector.start(x, y, width)

    fun move(x: Int, y: Int): Boolean {
        detector.move(x, y)?.let(dispatch)
        return detector.action != null
    }

    fun stop() = detector.stop()
}
