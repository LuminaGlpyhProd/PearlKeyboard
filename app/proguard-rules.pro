# Keep the InputMethodService and its public entry points (referenced from the system).
-keep class com.pearl.keyboard.ime.IosKeyboardService { *; }
-keep class * extends android.inputmethodservice.InputMethodService { *; }

# Keep View constructors used from XML / reflection.
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep our own (small) codebase intact; let R8 shrink the libraries instead. This makes
# the release build safe to ship without per-class reflection auditing.
-keep class com.pearl.keyboard.** { *; }

# Inline autofill style classes are resolved reflectively by the platform.
-keep class androidx.autofill.inline.** { *; }
-dontwarn androidx.autofill.inline.**
