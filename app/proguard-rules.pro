# Keep the InputMethodService and its public entry points (referenced from the system).
-keep class com.pearl.keyboard.ime.IosKeyboardService { *; }
-keep class * extends android.inputmethodservice.InputMethodService { *; }

# Keep View constructors used from XML / reflection.
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
