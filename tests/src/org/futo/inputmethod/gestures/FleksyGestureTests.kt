package org.futo.inputmethod.gestures

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class FleksyGestureTests {
    @Test fun touchThresholdAndDirection() {
        val detector = SwipeActionDetector()
        for ((point, direction) in listOf((85 to 10) to SwipeAction.RIGHT,
            (-85 to 10) to SwipeAction.LEFT, (20 to -90) to SwipeAction.UP,
            (10 to 85) to SwipeAction.DOWN)) {
            detector.start(0, 0, 100)
            assertNull(detector.move(20, 25))
            assertNull(detector.move(84, 0))
            assertNull(detector.move(100, 100))
            assertEquals(direction, detector.move(point.first, point.second))
            assertNull(detector.move(-500, 0))
            assertEquals(direction, detector.action)
            detector.stop()
            assertNull(detector.move(-500, 0))
        }
    }

    @Test fun wordRanges() {
        for ((marked, result) in listOf(
            "hello wo|rld" to "hello |", "hello |world" to "|world",
            "hello world|" to "hello |", "hello world |" to "hello |",
            "one, |two" to "one|two", "one\n|two" to "one|two",
            "one\n   |two" to "one\n|two", "   |abc" to "|abc",
            "é e\u0301|x" to "é |", "x 👩🏽‍💻|" to "x |",
            "x 👩|🏽‍💻" to "x |", "x " + "a".repeat(4096) + "|b" to "x |"
        )) {
            val pos = marked.indexOf('|')
            val text = marked.replace("|", "")
            val edit = WordDeletion.at(text, pos, pos, Locale.US)!!
            assertEquals(result, text.replaceRange(edit.start, edit.start + edit.previous.length, "|"))
        }
        assertNull(WordDeletion.at("word", 0, 0, Locale.US))
        assertEquals(Edit(1, "or"), WordDeletion.at("word", 3, 1, Locale.US))
    }

    @Test fun exactCorrectionAndInvalidation() {
        val history = CorrectionGestureHistory()
        var text = "a lot a lot "
        history.remember(text.length, "alot", "a lot", " ")
        fun apply(undo: Boolean, cursor: Int = text.length) = history.apply(undo, text, cursor) {
            text = text.replaceRange(it.start, it.start + it.previous.length, it.replacement)
            true
        }
        repeat(3) {
            assertTrue(apply(true))
            assertEquals("a lot alot ", text)
            assertFalse(apply(true))
            assertTrue(apply(false))
            assertEquals("a lot a lot ", text)
            assertFalse(apply(false))
        }
        assertFalse(apply(true, 6))
        assertFalse(apply(true))
        history.remember(text.length, "alot", "a lot", " ")
        text = "a lot b lot "
        assertFalse(apply(true))
        text = "a lot a lot "
        assertFalse(apply(true))
        history.remember(text.length, "alot", "a lot", " ")
        assertTrue(apply(true))
        text += "elsewhere"
        assertFalse(apply(false, 11))
        text = "a lot a lot "
        history.remember(text.length, "alot", "a lot", " ")
        assertTrue(apply(true))
        history.clear()
        assertFalse(apply(false))
    }
}
