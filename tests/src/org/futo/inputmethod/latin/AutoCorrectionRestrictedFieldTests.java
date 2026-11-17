package org.futo.inputmethod.latin;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import org.futo.inputmethod.latin.settings.Settings;

/** Enabling the user preference must not override the editor's restriction. */
public class AutoCorrectionRestrictedFieldTests extends InputTestsBase {
    @Override
    protected EditorInfo enrichEditorInfo(final EditorInfo info) {
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        return info;
    }

    private void checkToggle(final String separator) {
        final boolean previous = getBooleanPreference(Settings.PREF_AUTO_CORRECTION, true);
        try {
            setBooleanPreference(Settings.PREF_AUTO_CORRECTION, false, true);
            Settings.getInstance().onSharedPreferenceChanged(null, Settings.PREF_AUTO_CORRECTION);
            type("tgis");
            setBooleanPreference(Settings.PREF_AUTO_CORRECTION, true, true);
            Settings.getInstance().onSharedPreferenceChanged(null, Settings.PREF_AUTO_CORRECTION);
            assertFalse(Settings.getInstance().getCurrent().mAutoCorrectionEnabledPerUserSettings);
            type(separator);
            assertEquals("editor restriction survives toggle", "tgis" + separator,
                    mEditText.getText().toString());
        } finally {
            setBooleanPreference(Settings.PREF_AUTO_CORRECTION, previous, true);
            Settings.getInstance().onSharedPreferenceChanged(null, Settings.PREF_AUTO_CORRECTION);
        }
    }

    public void testEnableBeforeSpaceInRestrictedField() {
        checkToggle(" ");
    }

    public void testEnableBeforePunctuationInRestrictedField() {
        checkToggle(".");
    }
}
