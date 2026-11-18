package org.futo.inputmethod.gestures

import androidx.lifecycle.findViewTreeLifecycleOwner
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.actions.ActionIdToInt
import org.futo.inputmethod.v2keyboard.Direction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.futo.inputmethod.keyboard.KeyboardSwitcher
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.junit.Assert.*
import org.junit.Test

class FleksyEditorTests {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val editor get() = By.clazz("android.widget.EditText")

    private fun awaitText(value: String) {
        val field = device.wait(Until.findObject(editor.text(value)), 3000)
        assertNotNull("Expected [$value], editor shows [${device.findObject(editor)?.text}]", field)
    }

    private fun keyPosition(letter: Char): Triple<Int, Int, Int> {
        var x = 0
        var y = 0
        var width = 0
        instrumentation.runOnMainSync {
            val view = KeyboardSwitcher.getInstance().mainKeyboardView
            val keyboard = requireNotNull(view.keyboard)
            val key = requireNotNull(keyboard.getKey(letter.code) ?: keyboard.getKey(letter.uppercaseChar().code))
            val origin = IntArray(2)
            view.getLocationOnScreen(origin)
            width = key.width
            x = origin[0] + view.paddingLeft + key.x + key.width / 2
            y = origin[1] + view.paddingTop + key.y + key.height / 2
        }
        return Triple(x, y, width)
    }

    private fun key(letter: Char, dx: Double = 0.0, dy: Double = 0.0) {
        val (x, y, width) = keyPosition(letter)
        if (dx == 0.0 && dy == 0.0) device.click(x, y)
        else device.swipe(x, y, x + (width * dx).toInt(), y + (width * dy).toInt(), 3)
    }

    private fun correctionGestures(prefix: String) {
        for (c in "alot") key(c)
        awaitText(prefix + "alot")
        // Fix the prediction so this test exercises the real commit and editor callbacks
        // independently of the installed language model and its ranking.
        instrumentation.runOnMainSync {
            val view = KeyboardSwitcher.getInstance().mainKeyboardView
            val service = view.rootView.findViewTreeLifecycleOwner() as LatinIME
            val ime = requireNotNull(service.imeManager.fleksyEngine)
            ime.ensureSuggestionsCompleted()
            val typed = SuggestedWordInfo("alot", "", 1, SuggestedWordInfo.KIND_TYPED, null, 0, 0)
            val correction = SuggestedWordInfo("a lot", "", 2, SuggestedWordInfo.KIND_CORRECTION, null, 0, 0)
            ime.showSuggestionStrip(SuggestedWords(arrayListOf(typed, correction), null, typed,
                false, true, false, SuggestedWords.INPUT_STYLE_TYPING, 0))
        }
        key('g', 1.1)
        awaitText(prefix + "a lot ")
        repeat(2) {
            repeat(2) { key('g', dy = -1.1) }
            awaitText(prefix + "alot ")
            repeat(2) { key('g', dy = 1.1) }
            awaitText(prefix + "a lot ")
        }
        key('g', dy = -1.1)
        device.pressDPadLeft()
        device.pressDPadRight()
        key('g', dy = 1.1)
        awaitText(prefix + "alot ")
    }

    private fun toggleAndHold(prefix: String) {
        val prefs = PreferenceUtils.getDefaultSharedPreferences(instrumentation.targetContext)
        val originalGlide = prefs.all[Settings.PREF_GESTURE_INPUT]
        val (x, y, width) = keyPosition('g')
        var down = SystemClock.uptimeMillis()
        fun touch(action: Int, offset: Int = 0, second: Boolean = false) {
            val points = if (second) 2 else 1
            val ids = Array(points) { index -> MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            val coords = Array(points) { index -> MotionEvent.PointerCoords().also {
                it.x = (x + if (index == 0) offset else width).toFloat()
                it.y = y.toFloat()
                it.pressure = 1f
                it.size = 1f
            } }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, points, ids,
                coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        fun toggle() = instrumentation.runOnMainSync {
            val view = KeyboardSwitcher.getInstance().mainKeyboardView
            val service = view.rootView.findViewTreeLifecycleOwner() as LatinIME
            service.uixManager.triggerActionInternalFromIme(ActionIdToInt.getValue("fleksy_mode"), false)
        }
        touch(MotionEvent.ACTION_DOWN)
        toggle()
        touch(MotionEvent.ACTION_UP)
        awaitText(prefix)
        assertFalse(prefs.getBoolean(FleksyGestureController.PREF, true))
        assertEquals(originalGlide, prefs.all[Settings.PREF_GESTURE_INPUT])
        toggle()
        assertTrue(prefs.getBoolean(FleksyGestureController.PREF, false))
        assertEquals(originalGlide, prefs.all[Settings.PREF_GESTURE_INPUT])

        instrumentation.runOnMainSync {
            val key = requireNotNull(KeyboardSwitcher.getInstance().keyboard?.getKey('g'.code))
            assertTrue(FleksyTouchController.accepts(key))
            for (code in listOf(Constants.CODE_DELETE, Constants.CODE_SPACE, Constants.CODE_SHIFT)) {
                assertFalse(FleksyTouchController.accepts(key.copy(code = code)))
            }
            assertFalse(FleksyTouchController.accepts(key.copy(flickKeys = mapOf(Direction.North to key))))
        }

        "one two".forEach { key(it) }
        down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, -width)
        SystemClock.sleep(550)
        touch(MotionEvent.ACTION_MOVE, -3 * width)
        touch(MotionEvent.ACTION_UP, -3 * width)
        awaitText(prefix + "one ")
        key('g', -1.1)
        awaitText(prefix)
        key('g', 1.1)
        awaitText(prefix + "one ")
        key('g', 1.1)
        awaitText(prefix + "one two")

        down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, -width)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            -width, second = true)
        SystemClock.sleep(550)
        touch(MotionEvent.ACTION_CANCEL, -width, second = true)
        // The second contact owns its normal long press. The first contact must
        // have stopped deleting before it reached "one".
        assertTrue(device.findObject(editor).text.startsWith(prefix + "one "))
        toggle()
        val path = "test".map { letter ->
            val (keyX, keyY) = keyPosition(letter)
            android.graphics.Point(keyX, keyY)
        }.toTypedArray()
        assertTrue(device.swipe(path, 12))
        device.waitForIdle()
        key('g')
        assertTrue(device.findObject(editor).text.endsWith("g"))

    }

    private fun exercise(web: Boolean, period: Boolean) {
        val ime = instrumentation.targetContext.packageName + "/org.futo.inputmethod.latin.LatinIME"
        val previousIme = device.executeShellCommand("settings get secure default_input_method").trim()
        val enabledImes = device.executeShellCommand("ime list -s").lines()
        val prefs = PreferenceUtils.getDefaultSharedPreferences(instrumentation.targetContext)
        val settings = mapOf(FleksyGestureController.PREF to true,
            Settings.PREF_AUTO_CORRECTION to false, Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD to period)
        val previous = prefs.all
        fun configure() = instrumentation.runOnMainSync {
            prefs.edit().apply { settings.forEach { (key, value) -> putBoolean(key, value) } }.commit()
        }
        try {
            configure()
            device.executeShellCommand("ime enable $ime")
            device.executeShellCommand("ime set $ime")
            val host = instrumentation.context.packageName + "/" + GestureEditorActivity::class.java.name
            device.executeShellCommand("am start -W -f 0x10008000 -n $host --ez web $web")
            val field = device.wait(Until.findObject(editor), 10000)
            assertNotNull("Test editor did not open", field)
            val bounds = field.visibleBounds
            device.click(bounds.centerX(), bounds.top + bounds.height() * 3 / 4)
            val deadline = SystemClock.uptimeMillis() + 10000
            var visible = false
            while (!visible && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(100)
                instrumentation.runOnMainSync {
                    visible = KeyboardSwitcher.getInstance().mainKeyboardView?.isShown == true
                }
            }
            assertTrue("Keyboard did not open", visible)
            device.waitForIdle()
            for (c in "tgis") key(c)
            key('g', 1.1)
            key('g', 1.1)
            awaitText(if (period) "tgis. " else "tgis  ")
            for (c in "world") key(c)
            key('g', -1.1)
            awaitText(if (period) "tgis. " else "tgis  ")
            key('g', 1.1)
            awaitText((if (period) "tgis. " else "tgis  ") + "world")
            key('g', -1.1)
            if (!web) {
                instrumentation.runOnMainSync {
                    prefs.edit().putBoolean(Settings.PREF_AUTO_CORRECTION, true).commit()
                }
                correctionGestures("tgis. ")
                toggleAndHold("tgis. alot ")
            }
        } finally {
            device.pressHome()
            instrumentation.runOnMainSync {
                prefs.edit().apply {
                    settings.keys.forEach {
                        if (it in previous) putBoolean(it, previous[it] as Boolean) else remove(it)
                    }
                }.commit()
            }
            if (previousIme != ime) device.executeShellCommand("ime set $previousIme")
            if (ime !in enabledImes) device.executeShellCommand("ime disable $ime")
        }
    }

    @Test fun nativeEditor() = exercise(false, true)
    @Test fun webEditor() = exercise(true, false)
}
