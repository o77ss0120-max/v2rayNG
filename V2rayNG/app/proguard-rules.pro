-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.tencent.mmkv.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.v2ray.ang.dto.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
