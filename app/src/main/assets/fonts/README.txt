Drop a TrueType/OpenType font named exactly:

    keyboard.ttf

into THIS folder to change the keyboard's typeface. KeyboardView loads it
automatically at startup (see the `keyFont` field) and falls back to the system
sans-serif if it's absent.

Apple's "SF Pro" is proprietary and must NOT be redistributed. Use a
metric-compatible, openly-licensed font (e.g. Inter, Roboto, or another OFL/Apache
font) if you want a closer look.
