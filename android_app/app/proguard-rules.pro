# Android MAD Proguard Rules for Ali Chat App

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses,Signature
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Socket.IO & OkHttp
-keep class io.socket.** { *; }
-keep class io.socket.client.** { *; }
-keep class io.socket.engineio.client.** { *; }
-keep class okhttp3.** { *; }
-dontwarn io.socket.**
-dontwarn okhttp3.**

# Koin
-keep class org.koin.** { *; }

# Coil
-keep class coil.** { *; }

# Data Models
-keep class com.ali.textchat.data.models.** { *; }
