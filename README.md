# FUTO Keyboard — ghan1t fork

This is [ghan1t's fork](https://github.com/ghan1t/futo-android-keyboard) of
[FUTO Keyboard](https://github.com/futo-org/android-keyboard). It keeps a small set
of additional features and uses separate app names and package IDs so the fork
can be installed alongside upstream.

## Changes added to upstream

| Change | Source / attribution |
| --- | --- |
| Autocorrect toggle in the configurable action bar, with on/off icons and accessibility feedback | Adapted from [VibedByKaKi/futo-keyboard #4](https://github.com/VibedByKaKi/futo-keyboard/pull/4), commit [`0fdfcfd`](https://github.com/VibedByKaKi/futo-keyboard/commit/0fdfcfd79c8954890432ac437e2d6311e0e5695a), by KaKi87. |
| Separate stable/development app names and IDs; update link points to this fork | Implemented in this fork. <br/>stable: `FUTO Keyboard [ghan1t]` / `org.ghan1t.inputmethod.latin`; <br/>development: `FUTO Keyboard [ghan1t Dev]` / `org.ghan1t.inputmethod.latin.unstable`. |
| Git-derived `-ghan1t.N` versions and signed GitHub releases: automatic on `master`, manual on feature branches | Implemented in this fork; build setup informed by [VibedByKaKi's workflows](https://github.com/VibedByKaKi/futo-keyboard/tree/dev/.github/workflows). See the [release workflow](.github/workflows/fork-release.yml) and [versioning/signing guide](tools/release/README.md). |

See [Releases](https://github.com/ghan1t/futo-android-keyboard/releases) for published fork builds.
Fork releases use their own signing key and are not official FUTO releases.

## About upstream

The goal is to make a good modern keyboard that stays offline and doesn't spy on you. This keyboard is a fork of [LatinIME, The Android Open-Source Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME), with significant changes made to it.

Check out the [FUTO Keyboard website](https://keyboard.futo.tech/) for official upstream downloads and more information.

The code is licensed under the [FUTO Source First License 1.1](LICENSE.md).

## Issue tracking and contributing

Report problems with this fork in [this repository's issue tracker](https://github.com/ghan1t/futo-android-keyboard/issues).
For issues reproducible in official FUTO builds, use the [upstream issue tracker](https://github.com/futo-org/android-keyboard/issues).

## Building

When cloning the repository, you must perform a recursive clone to fetch all dependencies:
```
git clone --recursive https://github.com/ghan1t/futo-android-keyboard.git
```

If you forgot to specify recursive clone, use this to fetch submodules:
```
git submodule update --init --recursive
```

You can then open the project in Android Studio and build it that way, or use gradle commands:
```
./gradlew assembleUnstableDebug
./gradlew assembleStableRelease
```

## APK signing

Fork release signing is described in the [signing guide](tools/release/README.md#signing-setup).
The fingerprints below belong to **official upstream builds only** and do not
identify this fork's APKs.

```
Signing key fingerprint for all versions except Google Play:

MD5: 3A:BB:71:C6:BB:E4:92:27:B1:E3:5D:81:01:48:6A:B0
SHA1: 5D:15:B3:6E:C9:6A:96:28:41:09:DD:62:93:0D:9C:39:9F:5F:06:43
SHA-256: 74:3F:AD:58:64:AB:C4:26:50:0B:2D:C2:C4:7C:8A:D3:24:CB:CD:16:03:3F:80:16:99:48:41:35:63:74:F9:95

```
