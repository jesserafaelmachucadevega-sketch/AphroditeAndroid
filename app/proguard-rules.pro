# Keep JNI entry points for on-device inference.
-keepclasseswithmembernames class * {
    native <methods>;
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.aphrodite.**$$serializer { *; }
-keepclassmembers class com.aphrodite.** {
    *** Companion;
}
-keepclasseswithmembers class com.aphrodite.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Jakarta Mail / Angus
-dontwarn jakarta.activation.**
-dontwarn jakarta.mail.**
-dontwarn org.eclipse.angus.**
-keep class org.eclipse.angus.** { *; }
-keep class jakarta.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn com.sun.mail.**

# SSH
-dontwarn org.slf4j.**
-dontwarn org.ietf.jgss.**
-keep class com.jcraft.jsch.** { *; }
-keep class com.hierynomus.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# QuickJS
-keep class app.cash.quickjs.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compose
-dontwarn androidx.compose.**
