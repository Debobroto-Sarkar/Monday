# Add project specific ProGuard rules here.
# Monday Assistant ProGuard Rules

# Keep Gemini/OkHttp data classes
-keep class com.monday.assistant.ai.** { *; }
-keep class com.monday.assistant.actions.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Retrofit
-keepattributes Exceptions
-keepclassmembernames,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
