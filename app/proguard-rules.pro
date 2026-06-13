# Preserve youtubedl-android related classes
-keep class com.yausername.youtubedl.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

# Preserve Apache Commons Compress classes (specifically zip which uses reflection for ExtraFieldUtils)
-keep class org.apache.commons.compress.** { *; }

# Keep attributes for error tracing
-keepattributes LineNumberTable,SourceFile

# Ignore warnings for optional dependencies used by Apache Commons Compress
-dontwarn org.tukaani.xz.**
-dontwarn org.apache.commons.compress.**
