package org.futo.inputmethod.latin.uix.actions

import org.futo.inputmethod.latin.R
import org.futo.inputmethod.gestures.FleksyGestureController
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.PreferenceUtils

val FleksyModeAction = Action(
    icon = R.drawable.swipe_icon,
    iconInactive = R.drawable.swipe_icon_off,
    activeStatePrefKey = FleksyGestureController.PREF,
    activeStateDefault = false,
    activeStateDescription = R.string.fleksy_mode_enabled,
    inactiveStateDescription = R.string.fleksy_mode_disabled,
    name = R.string.fleksy_mode,
    simplePressImpl = { manager, _ ->
        val context = manager.getContext()
        val prefs = PreferenceUtils.getDefaultSharedPreferences(context)
        val enabled = !prefs.getBoolean(FleksyGestureController.PREF, false)
        prefs.edit().putBoolean(FleksyGestureController.PREF, enabled).apply()
        manager.announce(context.getString(
            if (enabled) R.string.fleksy_mode_enabled else R.string.fleksy_mode_disabled
        ))
    },
    windowImpl = null,
)
