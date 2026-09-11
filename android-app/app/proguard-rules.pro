-keepattributes *Annotation*
-keep class com.findle.ruaiunlocker.data.model.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
