# Autocorrect action

Adapted from KaKi87's VibedByKaKi/futo-keyboard PR #4, squash commit
`0fdfcfd79c8954890432ac437e2d6311e0e5695a` (also credits Cursor).
Only the autocorrect feature was imported. No donor workflows, branding,
release changes, or Fleksy gestures are included.

Add **Toggle autocorrect** through Settings → Actions. It initially appears in
More actions, including for existing action configurations. The action writes
`Settings.PREF_AUTO_CORRECTION`; the saved preference drives its icon,
accessibility description, and spoken confirmation.

## Integration notes

- `Action` supports an inactive icon, a preference key, a configurable default,
  and state descriptions. Existing actions and their numeric IDs stay stable.
- The action icon observes SharedPreferences directly because the keyboard action
  bar does not have the settings screen's `SharedPrefsCacheProvider`. The listener
  unregisters when the composable leaves composition and also handles clearing
  preferences. Changes made in settings use the same preference.
- The existing `LatinIME` settings listener forwards autocorrect changes through
  `IMEManager` to the active `GeneralIME`. This updates the legacy correction
  threshold and requests a new suggestion generation for the current typed word,
  without restarting input. Old in-flight generations are invalidated, including
  when suggestion lookup becomes disabled. The normal automatic commit path waits
  for the new generation.
- `InputLogic` also checks the effective setting when committing a cached
  correction. Swipe input still commits its recognized word. Field restrictions
  remain in `SettingsValues`; this action does not override them.

## Validation

Regression cases were added to the existing Android instrumentation suite for
both toggle directions before space and punctuation, explicit suggestion
selection while autocorrect is disabled, and fields prohibiting autocorrect.

Validated with Homebrew JDK 21 and the Android SDK at
`/opt/homebrew/share/android-commandlinetools`:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
GRADLE_USER_HOME=/tmp/futo-gradle \
./gradlew assembleUnstableDebug assembleUnstableDebugAndroidTest testUnstableDebugUnitTest --no-daemon
```

The app and instrumentation test APK compiled successfully. The unit-test task
reported `NO-SOURCE`, so this is compile validation, not an executed test pass.
`git diff --check` and parsing the changed XML resources also passed.
The build emitted warnings from existing sources/resources, including an invalid
TensorFlow Lite model warning; runtime model functionality was not validated.

No Android device or emulator was attached. Device execution of the regression
cases, action placement, icon updates, TalkBack feedback, persistence across
keyboard recreation, and settings-screen synchronization remain unverified.
