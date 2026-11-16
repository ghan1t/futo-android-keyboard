# Fork releases

The GitHub Actions `Fork release` workflow runs on branch pushes, never tag pushes.
Every run calculates the version, prints JSON, writes a job summary, and saves
`version.json` as an artifact. On `master`, the next job automatically reserves
the tag, builds the signed stable APK, and publishes a normal GitHub release.
On feature pushes it stops after displaying the calculated version. To publish a feature build, choose
**Actions → Fork release → Run workflow** and select the branch. That run performs
both jobs in the branch context and publishes an unstable APK as a prerelease.
The version job writes the calculated version prominently to the workflow run’s
**Summary** page (separate from the expandable step logs). No separate tag pipeline or
approval-environment setup is needed. GitHub only
shows the manual-run button after this workflow is on the default branch.

The runners are pinned to Ubuntu 24.04; JavaScript actions use Node.js 24.
The workflow always checks out the triggering SHA. Branch runs are serialized,
including version calculation, to prevent two master builds reserving the same
number. Published assets are never overwritten on retries.

## Versions

Upstream tags are fetched into `upstream/*`, separate from our `fork/*` tags.
The highest numeric upstream release reachable from HEAD is the base; unmerged
releases and release candidates are ignored. Two-part versions are padded with
`.0`; three- and four-part versions are preserved. Missing upstream tags cause
an error rather than an accidental `0.0.0` release.

- Stable: `0.1.30-ghan1t.1`, then `.2` after the first reservation.
- Feature: `0.1.30-ghan1t.2.branch.3` (next stable counter from reachable stable
  reservations, sanitized branch name, and commit count since the common ancestor
  with our fork's `origin/master`; local `master` is the fallback). The distance
  is not measured from an upstream release. Neither version names nor tag names
  contain hashes. The release plan records the commit and common ancestor so
  reservation remains consistent if master advances between jobs. Existing
  reservations retain their original version format.
- A new upstream version starts the stable suffix at `.1`.
- An annotated `fork/stable/...` or `fork/unstable/...` tag stores the complete
  plan. Rebuilding the same commit on the same branch reuses that plan, even if
  more upstream tags or later fork releases have appeared.

As requested, tagging precedes compilation. A failed build reserves its number;
retry the same commit to finish that release. Do not delete or move reservation
tags. Additional feature commits increase the distance. Rewriting a feature branch
can reuse a distance, and branch names must remain distinct after sanitization
(for example, `feat/example` becomes `feat-example`). A conflicting reservation
fails instead of force-pushing a tag.

Android `versionCode` is `100000 + git rev-list --count HEAD`: deterministic,
independent of the suffix reset, and increasing along master as long as history
is not rewritten. Feature branches can have equal counts or older codes than
other feature branches; switching to an older development build may require
uninstalling it (losing its app data). Stable and unstable have separate IDs.

## App identity

- Stable: `FUTO Keyboard [ghan1t]`, `org.ghan1t.inputmethod.latin`.
- Unstable: `FUTO Keyboard [ghan1t Dev]`, `org.ghan1t.inputmethod.latin.unstable`.

Both coexist with upstream and with each other. The manual update link opens
this fork's GitHub releases. Existing installs of our earlier upstream-ID debug
APK do not migrate automatically to these new IDs.

## Signing setup

Create a private keystore with Homebrew JDK 21's `keytool`, keep an encrypted
backup, and configure these repository Actions secrets:

- `KEYSTORE_BASE64`: base64-encoded contents of the keystore.
- `KEYSTORE_PASSWORD`: keystore password.
- `KEY_ALIAS`: signing-key alias.
- `KEY_PASSWORD`: key password (usually the same as the store password for PKCS12).

The workflow fails before reserving a tag if a secret is missing. It materializes
ignored signing files only on the runner and deletes them in an always-run step.
`REQUIRE_RELEASE_SIGNING=true` prohibits the build's usual debug-key fallback.
Use the same private key for all subsequent releases. No keys are generated or
uploaded by adding this workflow. Only trusted repository branches should be
allowed to run the signing job; external pull requests do not trigger it.

## Local use

```sh
git fetch --no-tags https://github.com/futo-org/android-keyboard.git '+refs/tags/*:refs/tags/upstream/*'
python3 tools/release/version.py --branch master --output /tmp/version.json
```

Set `VERSION_NAME` and `VERSION_CODE` from that JSON and use Homebrew JDK 21,
`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`, and the Gradle wrapper.
For a signed local build, provide ignored `keystore.properties` and run
`assembleStableRelease` or `assembleUnstableRelease`. Local version calculation
does not create or push a tag; `--reserve PLAN.json` creates the annotated local
tag explicitly. Publishing is performed only by the release job.

Version-script tests are available for local development and are not run by the
release workflow:

```sh
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
```
