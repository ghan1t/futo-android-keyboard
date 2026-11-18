package org.futo.inputmethod.gestures

import android.content.SharedPreferences
import android.view.inputmethod.ExtractedTextRequest
import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.keyboard.PointerTracker
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.PreferenceUtils

internal class FleksyGestureController(
    private val helper: IMEHelper,
    private val logic: InputLogic,
    private val refresh: () -> Unit
) {
    companion object { const val PREF = "fleksy_mode" }
    val history = CorrectionGestureHistory()
    private var prefs: SharedPreferences? = null
    var enabled = false
        private set
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (key == PREF || key == null) {
            enabled = preferences.getBoolean(PREF, false)
            reset()
            PointerTracker.cancelSwipeTypingForModeChange()
        }
    }

    private data class Deletion(val edit: Edit, val remainingDigest: ByteArray)
    private val deletions = ArrayDeque<Deletion>()
    private fun digest(text: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray())

    fun clearHistory() {
        history.clear()
        deletions.clear()
    }

    fun reset() {
        clearHistory()
        PointerTracker.cancelFleksyGestures()
    }

    fun start() {
        reset()
        prefs?.unregisterOnSharedPreferenceChangeListener(listener)
        prefs = PreferenceUtils.getDefaultSharedPreferences(helper.context).also {
            it.registerOnSharedPreferenceChangeListener(listener)
            enabled = it.getBoolean(PREF, false)
        }
    }

    fun finish() {
        enabled = false
        prefs?.unregisterOnSharedPreferenceChangeListener(listener)
        prefs = null
        reset()
    }

    fun perform(action: SwipeAction): Boolean {
        if (!enabled) return false
        if (action == SwipeAction.RIGHT && deletions.isEmpty()) return false
        // Read the editor, including its actual selection. The composing cache can be stale.
        val snapshot = helper.getCurrentInputConnection()?.getExtractedText(ExtractedTextRequest(), 0)
            ?: return false
        if (snapshot.startOffset != 0 || snapshot.partialStartOffset >= 0) return false
        val start = snapshot.selectionStart
        val end = snapshot.selectionEnd
        val text = snapshot.text?.toString() ?: return false
        if (start != logic.mConnection.mExpectedSelStart || end != logic.mConnection.mExpectedSelEnd) {
            clearHistory()
            return false
        }
        fun replace(edit: Edit): Boolean {
            if (!logic.replaceGestureRange(edit)) return false
            refresh()
            return true
        }
        if (action == SwipeAction.LEFT) {
            history.clear()
            val edit = WordDeletion.at(text, start, end, Settings.getInstance().current.mLocale)
                ?: return false
            if (!replace(edit)) return false
            deletions.addLast(Deletion(edit, digest(text.removeRange(edit.start, edit.end))))
            return true
        }
        if (start != end) {
            clearHistory()
            return false
        }
        if (action == SwipeAction.RIGHT) {
            val last = deletions.last()
            if (start != last.edit.start || !last.remainingDigest.contentEquals(digest(text))) {
                deletions.clear()
                return false
            }
            val insertion = Edit(start, "", last.edit.previous, restoredWord = last.edit.removedWord)
            if (replace(insertion)) deletions.removeLast() else deletions.clear()
            return true
        }
        return history.apply(action == SwipeAction.UP, text, end, ::replace)
    }
}
