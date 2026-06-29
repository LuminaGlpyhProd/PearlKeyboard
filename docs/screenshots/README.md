# Screenshots

Drop screenshots / GIFs here and they'll show up in the main `README.md`.

Suggested captures (light **and** dark mode):

| File name | What to show |
|---|---|
| `light.png` | Letters layout, light mode |
| `dark.png` | Letters layout, dark mode |
| `popup.png` | A key-cap pop-up mid-press |
| `accents.png` | Long-press accent bar (e.g. holding `e`) |
| `symbols.png` | Symbols / numbers page |
| `emoji.png` | Emoji panel |
| `suggestions.png` | Predictive strip with a correction highlighted |
| `glide.gif` | A swipe-typed word with the trail |

How to capture: run the app on a device/emulator, open any text field, and use
`adb exec-out screencap -p > docs/screenshots/light.png` (or the emulator's camera button).
For a GIF, record with `adb shell screenrecord` and convert with `ffmpeg`.

After adding images, update the **Screenshots** section of the root README to embed them.
