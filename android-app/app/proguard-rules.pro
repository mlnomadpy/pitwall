# Add project-specific ProGuard rules here. By default, keep Retrofit and kotlinx.serialization models.
-keep,allowobfuscation,allowshrinking class kotlin.Metadata { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.duckdb.**

-keep class androidx.room.** { *; }
-keep class com.google.mediapipe.** { *; }
