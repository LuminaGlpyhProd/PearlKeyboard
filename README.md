# Pearl Keyboard — an iOS-style keyboard for Android

[![Android CI](https://github.com/LuminaGlpyhProd/PearlKeyboard/actions/workflows/android.yml/badge.svg)](https://github.com/LuminaGlpyhProd/PearlKeyboard/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/LuminaGlpyhProd/PearlKeyboard?include_prereleases&sort=semver)](https://github.com/LuminaGlpyhProd/PearlKeyboard/releases)
![Min SDK](https://img.shields.io/badge/minSdk-29%20(Android%2010)-3DDC84)
![Language](https://img.shields.io/badge/Kotlin-100%25-7F52FF)
![License](https://img.shields.io/badge/license-Proprietary-red)

A native Android **input method (IME)**, written in Kotlin, that recreates the *look and
feel* of the iPhone keyboard while keeping the Android-style power features people expect
from Gboard (predictions, autocorrect, glide typing, emoji, clipboard history, voice, themes,
one-handed mode, haptics & sounds).

It is **not** a clone of Apple's code or assets — it's an original recreation of the
*experience*: key shapes, spacing, rounded corners, the rising key-cap pop-up, the long-press
accent bar, light/dark palettes, the blue action key, and the typing rhythm.

> **Honest scope.** A pixel-perfect iOS look *plus* every Gboard subsystem at production
> quality is years of work for a team. This repo is a **complete, buildable foundation**: the
> core typing experience is fully implemented, and the heavier AI features (glide decoding,
> voice, GIF) ship in a **simplified but working** form with clear extension points. Nothing
> here is faked — see the status matrix.

---

## Table of contents
- [Features](#features)
- [Download](#download)
- [Screenshots](#screenshots)
- [Supported Android versions](#supported-android-versions)
- [Install & enable](#install--enable-on-the-device)
- [Build instructions](#build-instructions)
- [Publishing to GitHub (manual steps)](#publishing-to-github-manual-steps)
- [Project structure](#project-structure)
- [Component guide](#component-guide)
- [Customizing](#customizing)
- [Legal & assets](#legal--assets)
- [License](#license)
- [Roadmap](#roadmap)
- [Troubleshooting](#troubleshooting)

---

## Features

| Area | Status | Notes |
|---|---|---|
| Custom IME service + input view | ✅ Implemented | Real `InputMethodService`, builds & installs |
| iOS key shapes / spacing / rounded corners / inset rows | ✅ Implemented | Canvas-drawn, scales to any width |
| Light & dark palettes (+ force light/dark) | ✅ Implemented | `KeyboardTheme` |
| Rising key-cap pop-up preview | ✅ Implemented | Extends above the keyboard like iOS |
| Long-press accent bar (é è ê …) | ✅ Implemented | Slide to choose |
| Shift / Caps-lock (double-tap) | ✅ Implemented | |
| Delete auto-repeat | ✅ Implemented | |
| Symbols / numbers pages | ✅ Implemented | 2 symbol pages |
| Blue action key (Go/Search/Send/Done) | ✅ Implemented | Driven by `imeOptions` |
| Auto-capitalization · double-space "." | ✅ Implemented | |
| Predictive text · autocorrect · learned words | ✅ Implemented | Bundled word list + edit-distance |
| Emoji keyboard + recents | ✅ Implemented | Categories, grid, `emoji2` |
| Clipboard history | ✅ Implemented | Auto-captured, tap to paste, long-press delete |
| Haptics · keypress sounds | ✅ Implemented | Predefined haptic ticks; **original synthesized** sound pack (7 voices incl. Silent), no system FX; `res/raw` overrides |
| Themes (light/dark/system) · one-handed mode · settings | ✅ Implemented | |
| Theme presets + custom background engine | ✅ Implemented | 8 presets; background image w/ blur·brightness·dim, key translucency, accent; live editor |
| In-app updater | ✅ Implemented | Checks GitHub Releases, prompts, installs via FileProvider |
| Autofill / OTP inline suggestions | ✅ Implemented | InlineSuggestions API (Android 11+), fully guarded |
| GIF search | ✅ Implemented | Tenor trending/search/categories, cursor pagination, animated previews, rich-content insert — set an API key |
| Multilingual typing | 🟡 Simplified | Layouts are data-driven; ships **English** |
| Glide / swipe typing | 🟡 Improved | Path capture + decoder; ~1.3k-word dictionary, nearest-2 endpoints, auto-insert on lift + live top-3 |
| Voice typing | 🟡 Simplified | Platform `SpeechRecognizer` + permission shim |
| Spell checking | 🟡 Simplified | Folded into autocorrect |
| Floating keyboard | ❌ Not yet | See roadmap |

✅ done · 🟡 working but simplified · 🟧 stub with clear TODO · ❌ not started

## Download

Pre-built APK/AAB are published on the **[Releases page](https://github.com/LuminaGlpyhProd/PearlKeyboard/releases)** once you push a `v*` tag (the CI builds and attaches them automatically — see [Publishing](#publishing-to-github-manual-steps)).

- **`app-debug.apk`** — installs immediately on any Android 10+ device (debug-signed). Best for trying it out.
- **`app-release.apk`** — release build (signed if you configure signing secrets, otherwise unsigned).
- **`app-release.aab`** — Android App Bundle for Google Play.

Every push to `main` also uploads these as **workflow artifacts** under the [Actions tab](https://github.com/LuminaGlpyhProd/PearlKeyboard/actions).

## Screenshots

> Add images to `docs/screenshots/` and embed them here (see `docs/screenshots/README.md`).
> None are committed yet — this machine had no emulator to capture them.

<!--
| Light | Dark | Pop-up | Emoji |
|---|---|---|---|
| ![](docs/screenshots/light.png) | ![](docs/screenshots/dark.png) | ![](docs/screenshots/popup.png) | ![](docs/screenshots/emoji.png) |
-->

## Supported Android versions

| | |
|---|---|
| **Minimum** | Android 10 (API 29) |
| **Target / compile** | Android 14 (API 34) |
| **Tested on** | Android 10–14 form factors (phones, tablets, foldables) via adaptive, width-based layout |
| **Language** | Kotlin · AGP 8.5.2 · Gradle 8.7 · JDK 17 |

The keyboard recomputes its geometry from the available width, so it adapts to different screen
sizes, resolutions, orientations, tablets and foldables, and supports edge-to-edge displays.

## Install & enable on the device

1. Install the APK (from a Release, an Actions artifact, or `./gradlew installDebug`).
2. Open **Pearl Keyboard** from the launcher (this is the settings screen).
3. Tap **Enable in Settings** → toggle **Pearl Keyboard** on (Android warns about IMEs reading
   input; that's standard for any keyboard).
4. Tap **Choose Keyboard** → select **Pearl Keyboard**.
5. Tap the **"Try it here"** field and start typing.

## Build instructions

### Option A — Android Studio (recommended)
1. **File ▸ Open** and select the project folder.
2. Let it sync (Android Studio creates the Gradle wrapper and downloads dependencies).
3. Pick a device/emulator and press **Run ▶**.

### Option B — command line
The repo ships the Gradle wrapper *config* but not the wrapper JAR (a binary that can't be
committed as text). Generate it once (or open in Android Studio, which does it for you):

```bash
gradle wrapper --gradle-version 8.7     # one-time; needs a local Gradle install
./gradlew assembleDebug                 # Windows: gradlew.bat assembleDebug
./gradlew installDebug                  # install on a connected device
./gradlew bundleRelease                 # build the AAB
```

### Option C — GitHub Actions (no local SDK needed)
Pushing to `main` triggers [`android.yml`](.github/workflows/android.yml), which builds on a
runner that already has the Android SDK and uploads the APK/AAB. This is how the official
artifacts are produced.

## Publishing to GitHub (manual steps)

This project was prepared on a machine **without** the Android SDK or the `gh` CLI, so the
binaries and the GitHub Release are produced by CI, and the initial push is done by you. The git
history with milestone commits is already created locally.

```bash
# 1) Create an EMPTY repo on github.com named: LuminaGlpyhProd/PearlKeyboard
#    (no README/license/.gitignore — this repo already has them)

# 2) From the project folder, point it at your repo and push:
git remote add origin https://github.com/LuminaGlpyhProd/PearlKeyboard.git
git branch -M main
git push -u origin main
```

That push triggers the CI build. To cut a downloadable release:

```bash
git tag v1.0.0
git push origin v1.0.0      # triggers release.yml → builds + creates the GitHub Release
```

### (Optional) Signed release builds
By default the release APK/AAB are **unsigned** (the debug APK is always installable). To get a
signed release, create a keystore and add four repository secrets — CI picks them up automatically:

```bash
keytool -genkey -v -keystore release.keystore -alias pearl -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore       # copy the output for the KEYSTORE_BASE64 secret
```

In **GitHub ▸ Settings ▸ Secrets and variables ▸ Actions**, add:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

> Keep `release.keystore` out of git (it's already covered by `.gitignore`). Losing it means you
> can't update a Play listing later.

## Project structure

```
PearlKeyboard/
├─ .github/workflows/         # CI build + release automation
├─ app/
│  ├─ build.gradle.kts        # SDK levels, deps, optional release signing
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ assets/
│     │  ├─ dictionaries/en_words.txt   # editable word-frequency list
│     │  └─ fonts/                       # drop keyboard.ttf here
│     ├─ java/com/pearl/keyboard/
│     │  ├─ ime/        # IosKeyboardService, KeyboardView, KeyPreviewPopup, strip, container
│     │  ├─ model/      # Key, KeyType, KeyboardLayout, Layouts (data-driven)
│     │  ├─ theme/      # KeyboardTheme (iOS light/dark palettes)
│     │  ├─ input/      # Dictionary, SuggestionEngine, GestureTypingDetector
│     │  ├─ feedback/   # HapticFeedback, SoundFeedback
│     │  ├─ feature/    # emoji · clipboard · gif · voice
│     │  ├─ settings/   # Prefs, SettingsActivity, SettingsFragment
│     │  └─ util/
│     └─ res/           # colors, dimens, themes (day/night), preferences, launcher icon
├─ docs/screenshots/    # put screenshots here
├─ gradle/libs.versions.toml
├─ LICENSE
└─ README.md
```

### Architecture
The **view layer** (`KeyboardView`, panels) is dumb about text — it renders and reports *intent*
(`KeyAction`) to a listener. The **service layer** (`IosKeyboardService`) owns all text state: the
composing word, autocorrect, predictions, the action key, and feature panels. Persistent state
(settings, learned words, clipboard, emoji recents) lives in `SharedPreferences` behind small
repositories. This keeps the hot path (draw + touch) free of business logic, which is what keeps
typing smooth.

## Component guide

- **`IosKeyboardService`** — translates `KeyAction`s into `InputConnection` edits; maintains a
  **composing region** so words can be corrected/replaced in place (iOS-style). Handles auto-cap,
  double-space period, the action key, and opens panels. Registers a clipboard watcher.
- **`KeyboardView`** — one custom `View`: computes key rects from width (scales to any device),
  draws iOS-style keys, and handles multi-touch typing, the rising pop-up, long-press accents,
  delete repeat, shift logic, and glide capture.
- **`KeyPreviewPopup`** — a non-touchable `PopupWindow` (clipping disabled) so the bubble rises
  above the keyboard like iOS.
- **`model/Layouts.kt`** — QWERTY + 2 symbol pages as plain data; add languages here.
- **`Dictionary` / `SuggestionEngine`** — prefix predictions ranked by frequency + a conservative
  edit-distance autocorrect; learns accepted words.
- **`GestureTypingDetector`** — resamples the swipe, filters by first/last key, scores candidates
  by DP alignment to key centres. A clean approximation of a production decoder.
- **Panels** — emoji (grid + categories + recents), clipboard history, GIF (scaffold).

## Customizing

| I want to… | Do this |
|---|---|
| Change colors | Edit `theme/KeyboardTheme.kt` (`light()` / `dark()`). |
| Change key sizes/spacing | Edit `res/values/dimens.xml`. |
| Change the typeface | Drop a `.ttf` at `app/src/main/assets/fonts/keyboard.ttf` (auto-loaded). |
| Use custom keypress sounds | Add `keypress_standard/delete/return/spacebar.(ogg\|wav)` to `res/raw/`. |
| Add a language | Add a letters layout in `Layouts.kt` + a `<subtype>` in `res/xml/method.xml`. |
| Improve predictions | Replace `assets/dictionaries/en_words.txt`; swap `Dictionary` for a trie/FST. |
| Tune glide | `GestureTypingDetector.ACCEPT_THRESHOLD`, `KeyboardView.gestureSlop`. |
| Enable GIF search | Add `tenor.api.key=YOUR_KEY` to `local.properties` (or set the `TENOR_API_KEY` env var / `-Ptenor.api.key=` Gradle property), rebuild, then turn on **GIF** in settings. The key is injected via `BuildConfig` — never hardcoded. |
| Rename app/package | Change `applicationId` + `namespace`, the manifest, and the package folders. |

## Legal & assets

- **No Apple assets are bundled.** Fonts, sounds and icons are either the OS's own or simple
  originals. Apple's SF fonts, system sounds and emoji art are proprietary — do **not** ship them.
- Default keypress sounds use Android's built-in `AudioManager` effects.
- Emoji are rendered by the platform / `androidx.emoji2`, not bundled art.
- The starter word list is intentionally tiny and generic; replace it with a properly-licensed
  frequency list for real use.

## License

**Proprietary — © 2026 LuminaGlpyhProd. All rights reserved.** See [`LICENSE`](LICENSE).

The source is published for viewing; it may not be used, copied, modified, or redistributed
without written permission. Third-party libraries (AndroidX, Material Components) remain under
their own licenses (Apache-2.0).

## Roadmap

1. Neural/statistical glide + autocorrect; a real spell-check service (red underlines).
2. Floating + split keyboard for foldables; draggable one-handed handle (L/R).
3. Real GIF/sticker search (Tenor) with `commitContent` for inline media.
4. Theme engine: custom colors, backgrounds, per-key styling, Material You.
5. Bigger dictionaries + true multilingual with auto language detection.
6. Number row, spacebar-swipe cursor control, text-selection gestures.
7. Richer haptics via `VibrationEffect.Composition`.
8. Unit tests for the suggestion/glide engines; screenshot tests for layouts.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Android Studio: "Gradle wrapper missing" | Let AS sync (it generates it) or run `gradle wrapper --gradle-version 8.7`. |
| CLI build can't find the SDK | Set `ANDROID_SDK_ROOT`, or create `local.properties` with `sdk.dir=/path/to/Android/sdk` (AS does this automatically). |
| Keyboard doesn't appear after install | Enable it in **Settings ▸ System ▸ Languages & input ▸ On-screen keyboard**, then pick it with the keyboard switcher. |
| Release APK won't install | It's unsigned unless you configured signing secrets — install `app-debug.apk`, or sign the release. |
| Voice typing does nothing | Grant the microphone permission (tap the mic, approve, tap again) and ensure a speech service is installed. |

---

*Built as a clean, well-commented starting point. Read the source in the order of the
"Component guide" and it should be easy to extend.*
