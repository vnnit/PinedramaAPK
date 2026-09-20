# Keep NanoHTTPD
-keep class fi.iki.elonen.** { *; }

# Keep Gson models
-keep class com.vnnit.pinedramatv.models.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
