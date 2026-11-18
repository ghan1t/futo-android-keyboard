package org.futo.inputmethod.gestures

import android.icu.text.BreakIterator
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.security.MessageDigest
import java.util.Locale

internal data class Edit(
    val start: Int,
    val previous: String,
    val replacement: String = "",
    val removedWord: String = previous.trim(),
    val restoredWord: String? = null
) {
    val end get() = start + previous.length

    fun matchesAtCursor(editor: InputConnection): Boolean {
        val snapshot = editor.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val localStart = start - snapshot.startOffset
        val localEnd = end - snapshot.startOffset
        return snapshot.partialStartOffset < 0 &&
                snapshot.selectionStart == localEnd && snapshot.selectionEnd == localEnd &&
                snapshot.text?.regionMatches(localStart, previous, 0, previous.length) == true
    }
}

internal object WordDeletion {
    fun at(text: String, anchor: Int, focus: Int, locale: Locale): Edit? {
        if (anchor !in 0..text.length || focus !in 0..text.length) return null
        if (anchor != focus) {
            val start = minOf(anchor, focus)
            return Edit(start, text.substring(start, maxOf(anchor, focus)))
        }
        if (focus == 0) return null
        val graphemes = BreakIterator.getCharacterInstance(locale).apply { setText(text) }
        val words = BreakIterator.getWordInstance(locale).apply { setText(text) }
        fun wordEndingAt(end: Int): Boolean {
            words.isBoundary(end)
            return words.ruleStatus >= BreakIterator.WORD_NUMBER
        }

        if (!words.isBoundary(focus)) {
            val end = words.following(focus)
            if (wordEndingAt(end)) {
                val start = words.preceding(focus)
                return Edit(start, text.substring(start, end))
            }
        }

        if (!graphemes.isBoundary(focus)) {
            val start = graphemes.preceding(focus)
            return Edit(start, text.substring(start, graphemes.following(focus)))
        }

        var end = focus
        while (end > 0 && Character.getType(text.codePointBefore(end)) == Character.SPACE_SEPARATOR.toInt()) {
            end = graphemes.preceding(end)
        }
        val start = when {
            end == 0 -> 0
            end < focus && text[end - 1] in "\r\n" -> end
            wordEndingAt(end) -> words.preceding(end)
            else -> graphemes.preceding(end)
        }
        return Edit(start, text.substring(start, focus))
    }
}

internal class CorrectionGestureHistory {
    private var correction: Edit? = null
    private var undone = false
    private var surroundings: ByteArray? = null

    // Detect edits elsewhere between gestures without retaining the editor contents.
    private fun surroundings(text: String, edit: Edit): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(text.substring(0, edit.start).toByteArray())
        return digest.digest(text.substring(edit.start + edit.previous.length).toByteArray())
    }

    fun clear() {
        surroundings = null
        correction = null
        undone = false
    }

    fun remember(cursor: Int, typed: String, committed: String, separator: String) {
        clear()
        val start = cursor - committed.length - separator.length
        if (typed != committed && start >= 0) {
            correction = Edit(start, committed + separator, typed + separator, removedWord = committed)
        }
    }

    fun apply(undo: Boolean, text: String, cursor: Int, replace: (Edit) -> Boolean): Boolean {
        val saved = correction ?: return false
        val edit = if (undone) {
            Edit(saved.start, saved.replacement, saved.previous, removedWord = "", restoredWord = saved.removedWord)
        } else saved
        if (cursor != edit.start + edit.previous.length ||
            !text.regionMatches(edit.start, edit.previous, 0, edit.previous.length)) {
            clear()
            return false
        }
        val context = surroundings(text, edit)
        if (surroundings?.contentEquals(context) == false) {
            clear()
            return false
        }
        if (undone == undo) return false
        if (!replace(edit)) {
            clear()
            return false
        }
        surroundings = context
        undone = undo
        return true
    }
}
