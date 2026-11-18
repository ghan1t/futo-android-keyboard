# Maintaining Fleksy mode

The configurable **Fleksy mode** action stores one boolean, defaulting to off. It uses the same icon and accessibility machinery as the autocorrect action. GeneralIME handles gestures in this mode. Other engines keep their ordinary input path.

## Integration points

- The action registry appends the action to preserve existing numeric IDs. IMEManager exposes the active, enabled GeneralIME and forwards selection changes before they can be coalesced.
- PointerTracker starts detection on ordinary keys. Space, delete, modifiers, repeatable keys and dedicated flick keys keep their existing handlers. Fleksy takes precedence at pointer-down without writing the glide-typing preference.
- SwipeActionDetector latches the first direction for the lifetime of the contact. Activation takes 0.85 key widths with a dominant-axis ratio of 1.45. A left swipe deletes one word, including on a long drag or hold.
- A right swipe restores one deletion at a time, most recent first, including its original separators. Restoration checks the cursor and a digest of the editor text after deletion. Typing, cursor movement, a mode toggle or an editor change clears the stack. When the stack is empty, LatinIMELegacy sends its ordinary space event.
- GeneralIME records an actual correction after its input transaction. Up and down validate the exact committed or typed text, separator and cursor. The history covers the latest uninterrupted correction. Older suggestion rankings cannot establish which replacement was actually committed.
- InputLogic performs range edits through RichInputConnection, updates composing and learning state, and flushes the buffered edit inside the batch. An empty commit on its own can disappear during buffer merging. Editors must provide text and selection sufficient to verify the range. Ambiguous ranges are skipped.

## Donor revisions

The four directions, normal space event and special-key exclusions derive from ndom91’s [Fleksy implementation at 05c0fa6](https://github.com/futo-org/android-keyboard/commit/05c0fa6fd2ac1dee0538e3e435f21b58b899ecc1). Up/down now undo and redo the same correction, including split words. The detector tests follow the isolated approach in djurcola’s [extended swipe deletion at 44421c6](https://github.com/futo-org/android-keyboard/commit/44421c66705227ca03d5e56dbd68e08bac5ae405). Left deletes immediately upon activation.

## Validation

The latest emulator deployment used `assembleUnstableDebug` and `assembleUnstableDebugAndroidTest` with the submodules at the revisions pinned on `master`.

All five gesture tests passed on an API 35 emulator. The AndroidJUnitRunner package `org.futo.inputmethod.gestures` exercises native EditText and WebView touch input, both double-space settings, disabled autocorrect, exact split-word undo/redo, cursor movement, mode toggling during a touch, long holds, one-at-a-time deletion and restoration, glide typing with Fleksy off, and multitouch cancellation. The correction test supplies a fixed prediction and exercises the actual commit and editor callbacks. Range and detector tests cover Unicode, punctuation, newlines, reversed selections, duplicate corrections and single activation across multiple movement events. Existing `PointerTrackerQueueTests` and `ActionIconTests` cover the shared infrastructure.

Physical-device tuning, terminal editors, live flick layouts, language changes and end-to-end spacebar/backspace drags remain unverified. The gesture suite checks special-key eligibility directly.
