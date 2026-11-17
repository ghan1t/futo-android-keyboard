# Keyboard action toggles and Fleksy gestures

## Scope and execution order

This is an implementation handoff. The current task creates this document only; do not interpret its creation as implementation or testing of the features below.

Implement the two stages **sequentially**:

1. Import and validate the autocorrect action, then commit it as one squashed commit on our fork.
2. Starting from that committed result, adapt the Fleksy branch, validate it, then commit the gesture feature separately.

Do not mix the stages in one commit. Do not merge an entire donor fork or import its unrelated changes. Preserve attribution to the original authors in commit messages. This is a permanent fork feature: upstream acceptance is not a dependency.

Our fork: https://github.com/ghan1t/futo-android-keyboard

Research baseline: local `31e720ddc` (2026-10-20). Recheck the actual checkout and repository instructions before implementation. The earlier investigation made no application code changes and ran only patch-application dry runs, not builds or device tests.

## Sources and reviewed revisions

- Autocorrect action: https://github.com/VibedByKaKi/futo-keyboard/pull/4
  - Reviewed squash commit: `0fdfcfd79c8954890432ac437e2d6311e0e5695a`.
  - https://github.com/VibedByKaKi/futo-keyboard/commit/0fdfcfd79c8954890432ac437e2d6311e0e5695a
  - Seven files, 93 additions / 3 deletions. Its isolated diff applied cleanly to the research baseline. The donor's manual checklist was unchecked and the commit's build check was cancelled.
- Fleksy starting implementation: https://github.com/futo-org/android-keyboard/pull/1898
  - Branch: `fleksy-swipe-mode`; reviewed head: `05c0fa6fd2ac1dee0538e3e435f21b58b899ecc1`.
  - 18 files, 1,116 additions / 92 deletions. Its diff applied cleanly to the research baseline.
  - Use this implementation as the starting point and preserve relevant bug fixes, then refactor and change behavior as described below. Do not merely retain its suggestion carousel under different labels.
- Extended swipe deletion reference: https://github.com/futo-org/android-keyboard/pull/2199
  - Reviewed head: `44421c66705227ca03d5e56dbd68e08bac5ae405`.
  - Reuse useful detector structure and tests. Do not import a second competing touch dispatcher wholesale.
  - Its actual behavior selects one word initially, then allows selection adjustment after holding; deletion occurs on release. It does **not** itself implement continuous deletion from a stationary hold. Our requested behavior below extends it.
- Existing suggestion history: upstream commit `ed45a6b18bcc8c2fd7152204747d1d4c8dd4572a`, already present at the research baseline. Inspect `InputLogic.mRememberedSuggestedWords` before introducing overlapping history machinery.

Resolve and record the donor revisions actually used. If donor branches have advanced, inspect the changes before replacing these reviewed revisions. Temporary research downloads may exist under `/tmp/futo-pr-review`, but are not durable inputs; fetch missing material from GitHub. Register any PR actively worked on with the available T3 PR-linking tool.

## Stage 1: autocorrect action — one commit

### Required behavior

- Add an action users can place on the existing keyboard action bar.
- Pressing it toggles the existing `Settings.PREF_AUTO_CORRECTION` preference.
- Show the saved on/off state through the icon and accessible feedback.
- Apply the setting without opening settings or restarting the keyboard.
- Preserve normal text-field restrictions: enabling the user preference must not force autocorrect in fields that prohibit it.
- Use the existing action customization screen to place the action. No new settings screen is needed.

### Implementation

1. Inspect the donor PR and its squash commit. Import only the autocorrect feature changes, consolidating any necessary donor commits into a single local commit; the reviewed squash commit may already contain the complete change.
2. Reuse the existing action registry, shared-preference observation, and settings refresh mechanisms where practical.
3. Keep the stateful action icon support reusable for the Fleksy toggle in stage 2. Avoid a general action-system redesign.
4. Verify whether changing the preference also refreshes a pending autocorrect decision for the currently composed word. Fix a stale suggestion/commit decision if necessary without restarting the editor session.
5. Keep donor build workflows, release configuration, branding, and unrelated fixes out of this commit.

### Validation and completion gate

- Compile the affected app variant and run relevant existing checks.
- Place the action in the bar; toggle in both directions and verify the icon and accessibility feedback.
- Type a misspelling, toggle before pressing space, and verify the resulting commit follows the new setting. Repeat with punctuation.
- Verify suggestions remain usable with autocorrect disabled.
- Verify persistence after keyboard recreation and that changes through the existing settings UI update the action state.
- Check a normal text field and a field that disables autocorrect.

Make one commit, for example `Add autocorrect toggle action`, including source attribution and validation results. **Finish this stage and commit it before starting stage 2.** If device validation is unavailable, explicitly record what remains unverified; do not claim a device-tested result.

## Stage 2: adapt and isolate Fleksy gestures

### Mode toggle and interaction rules

- Add a second stateful action, `Fleksy mode`, using stage 1's action infrastructure.
- One persisted boolean enables/disables all gestures in this feature. Default to off.
- No new settings menu, radio group, per-gesture settings, or sensitivity controls.
- While enabled, Fleksy gestures take priority over swipe typing. Preserve the user's underlying swipe-typing preference and restore its effective behavior when Fleksy mode is off.
- Keep autocorrect and Fleksy mode independent. Disabling automatic correction must not disable explicit suggestion acceptance.
- Preserve spacebar cursor/language gestures and backspace-key gestures. Whole-keyboard gestures apply across ordinary typing keys; these special keys retain their existing behavior.
- Make precedence against flick keys explicit and test it. Prefer preserving dedicated flick-key behavior and modifier keys rather than silently turning those inputs into destructive gestures.
- On mode changes, cancel pending gesture timers, active gestures, and temporary undo/redo state. Prevent a cancelled gesture from generating a trailing character tap.
- Only enable gesture consumption where the active input engine implements the operation. Unsupported engines must retain usable normal input rather than consume gestures as no-ops.

### Left swipe: delete whole words, with extension and hold repeat

User-selected rule: **delete the whole word when the cursor is inside it; otherwise delete backward.** `|` denotes the cursor.

- `hello wo|rld` → `hello |`: remove the entire word containing the cursor, including its suffix to the right.
- `hello |world` → `|world`: at a word's start, remove the preceding word and its separating space; do not remove `world`.
- `hello world|` → `hello |`: remove the preceding word.
- `hello world |` → `hello |`: remove the trailing space and preceding word in one action.
- A cursor counts as inside a word only when it lies strictly within that word's span, not at either boundary. Use locale-aware word boundaries rather than ASCII-only letter checks.
- Trigger one deletion after a deliberate left-swipe threshold.
- Continuing farther left deletes additional preceding words at distance thresholds.
- Holding after activation deletes additional preceding words after an initial repeat delay and at a controlled interval. A normal stationary key hold before swipe activation must retain its normal behavior.
- Treat distance and elapsed time as two ways to advance the same deletion sequence; avoid duplicate deletion from the same movement/timer processing.
- Stop repeating on release, cancellation, another pointer, mode change, editor change, or exhaustion of deletable text. Rightward motion must not cause additional backward deletions or resurrect text.
- Use conservative named internal constants for thresholds/delays; no new settings UI. Tune on device and document the selected values.
- Honor an existing selection by deleting it first, consistent with normal backspace; do not unexpectedly expand it into surrounding words.
- Preserve Unicode graphemes, punctuation boundaries, and newline behavior. Define edge cases in tests. If a safe word range cannot be established, fail safely rather than pretending character deletion is word deletion.

This deliberately goes beyond #2199's select/adjust/release interaction. Borrow its isolated detection and test approach, not its behavior unchanged.

### Swipe up: undo an autocorrection

- Restore the originally typed text for the applicable correction at the cursor, including corrections that split a word (`alot` → `a lot`).
- Affect a validated correction only; do not send application-level Ctrl+Z or undo arbitrary editing actions.
- Save enough information to redo the **exact correction that was undone**, including relevant separator/cursor behavior.
- Repeated up gestures after an undo are no-ops; they must not cycle suggestions or punctuation.
- For older words, reuse existing position-aware suggestion history only where it can establish the original/corrected pair unambiguously. Do not guess from the current suggestion ranking or a word-string-only map. At minimum support the most recent correction reliably; document any deliberate history limitation.

### Swipe down: redo that autocorrection

- Reapply the exact corrected text saved by the preceding up gesture.
- Repeated down gestures after redo are no-ops. Repeated up/down alternation on the same unchanged correction must be stable.
- Do not recalculate the replacement from new model predictions, accept an unrelated suggestion, or cycle alternatives.
- Invalidate pending redo after intervening text edits, cursor/selection movement away from the target, editor/session changes, or mode changes. Distinguish internal selection callbacks from genuine user movement.
- Validate both the target position and actual editor text before every replacement. Duplicate words elsewhere in the document must never receive another occurrence's correction.
- With no valid undo/redo target, do nothing.

### Swipe forward/right: apply suggestion / space / punctuation

- Preserve the relevant #1898 behavior through FUTO's normal space/commit path: accept the applicable suggestion/autocorrection and insert spacing as normal typing would.
- Preserve normal repeated-space punctuation behavior when the existing double-space-full-stop setting is enabled.
- Respect the autocorrect toggle and existing spacing/punctuation preferences; right swipe must not secretly force autocorrection when disabled.
- Inspect the donor's actual right-swipe behavior and capture it in tests before refactoring. Retain useful trailing-space/punctuation handling only where needed for this behavior.
- Up/down are exclusively correction undo/redo; do not retain the donor's punctuation cycling on those directions.

### Architecture and upstream merge maintenance

Move most fork-specific implementation into dedicated classes, with narrow integration points. Suggested responsibilities (names may follow repository conventions):

- `SwipeActionDetector`: direction recognition, activation, pointer lifecycle, distance thresholds, and hold-repeat state. Keep it independently testable with an injectable clock/scheduler or explicit time inputs.
- `FleksyGestureController`: dispatch and mode lifecycle; coordinates editing through a small interface.
- `CorrectionGestureHistory`: validated original/corrected spans and undo/redo state. Reuse existing history where sound; avoid two competing sources of correction truth.
- A word-deletion helper: range selection, boundary rules, and repeated deletion. Keep editor mutation routed through the input engine's established transaction mechanisms.

Keep only necessary hooks in `PointerTracker`, the active IME dispatch path, `GeneralIME`, `InputLogic`, settings loading, and the action registry. Separate classes must not bypass composing-state updates, suggestion refresh, shift state, learning/history bookkeeping, or input connection cache maintenance.

Avoid scattering tiny changes throughout upstream files. Prefer a few cohesive adapter calls at stable event boundaries. Do not add artificial surrounding edits to make a one-line change larger: line count itself does not determine Git conflicts. Do not reformat or reorder unrelated upstream code.

Where helpful, mark genuinely non-obvious integration points with a concise comment such as `// Fork: Fleksy gesture integration; see FleksyGestureController.` Comments aid manual merges; they do not prevent Git conflicts. Document the integration points and invariants for future upstream updates.

Do not retain the donor's unrelated settings component refactoring, resource submodule changes, or independent punctuation/suggestion-carousel features. Preserve source attribution for adapted code.

### Validation and completion gate

Add meaningful tests for:

- Activation thresholds, direction ambiguity, taps, cancellation, multitouch, distance extension, and stationary hold repeat with deterministic time.
- Every deletion example above, repeated deletion, selections, start/end of document, whitespace, punctuation, newlines, long words, and Unicode/emoji boundaries.
- Exact up/down alternation, split-word corrections, trailing spaces, stale callbacks, changed text, moved cursors, duplicate words, editor switches, and mode changes.
- Right swipe with autocorrect on/off and with double-space punctuation enabled/disabled.
- Toggle persistence, preservation of the swipe-typing preference, and absence of gesture behavior when disabled.

Run appropriate existing tests and compile the app. Device-test typing and editing in ordinary Android text fields and a WebView/browser editor; test a terminal-style editor if available. Check spacebar/backspace gestures, long presses, flick layouts, and language changes. Record unavailable coverage explicitly.

Review the final diff against the committed stage-1 baseline: most new logic should be in dedicated files, upstream integration edits should be small and explainable, and no unrelated submodule/UI changes should remain.

Commit stage 2 separately, for example `Add toggleable Fleksy gestures with correction undo and redo`, with donor attribution. Include a short maintenance note listing integration points, behavior differences from #1898/#2199, tested commands/devices, and unresolved limitations. Do not claim reliability based solely on a clean patch application or successful compilation.
