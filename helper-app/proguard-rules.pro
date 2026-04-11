# Add project specific ProGuard rules here.
# See http://developer.android.com/guide/developing/tools/proguard.html

# Keep data classes used with Gson/Retrofit serialization
-keepclassmembers class com.digibuddy.core.model.** { *; }

# Socket.io
-keep class io.socket.** { *; }
-keep class io.socket.client.** { *; }
-keep class io.socket.engineio.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }

# Uncomment to preserve line numbers in stack traces for debugging.
#-keepattributes SourceFile,LineNumberTable
#-renamesourcefileattribute SourceFile
