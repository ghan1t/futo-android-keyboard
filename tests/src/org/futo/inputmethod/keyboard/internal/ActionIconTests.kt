package org.futo.inputmethod.keyboard.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.test.AndroidTestCase
import androidx.appcompat.content.res.AppCompatResources
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.futo.inputmethod.latin.uix.actions.AllActions
import org.futo.inputmethod.latin.uix.actions.AutoCorrectToggleAction
import org.futo.inputmethod.latin.uix.theme.presets.DefaultDarkScheme

class ActionIconTests : AndroidTestCase() {
    private fun render(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 48, 48)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    fun testKeyboardActionIconsFollowSavedStateWithoutRecreatingProvider() {
        val prefs = PreferenceUtils.getDefaultSharedPreferences(context)
        val key = Settings.PREF_AUTO_CORRECTION
        val original = if (prefs.contains(key)) prefs.getBoolean(key, true) else null
        try {
            prefs.edit().putBoolean(key, false).commit()
            val provider = BasicThemeProvider(context, DefaultDarkScheme.obtainColors(context))
            val icons = KeyboardIconsSet().apply { loadIcons(null, provider) }
            val names = listOf(
                "action_toggle_autocorrect",
                "action_${AllActions.indexOf(AutoCorrectToggleAction)}"
            )
            val staticIcon = icons.getIconDrawable("action_emoji")

            for (enabled in listOf(false, true, false)) {
                prefs.edit().putBoolean(key, enabled).commit()
                val expected = render(AppCompatResources.getDrawable(context,
                    if (enabled) R.drawable.icon_spellcheck else R.drawable.icon_spellcheck_off)!!)
                for (name in names) {
                    assertTrue("Wrong icon for $name with autocorrect=$enabled",
                        expected.sameAs(render(icons.getIconDrawable(name)!!)))
                }
                assertSame(staticIcon, icons.getIconDrawable("action_emoji"))
            }

            prefs.edit().remove(key).commit()
            val expectedDefault = render(AppCompatResources.getDrawable(context, R.drawable.icon_spellcheck)!!)
            assertTrue(expectedDefault.sameAs(render(icons.getIconDrawable(names.first())!!)))
        } finally {
            prefs.edit().apply {
                if (original == null) remove(key) else putBoolean(key, original)
            }.commit()
        }
    }
}
