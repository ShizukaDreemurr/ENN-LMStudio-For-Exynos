-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class dev.exynoslm.**$$serializer { *; }
# Strip verbose logging in release (no prompts/tokens in Logcat).
-assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); }
