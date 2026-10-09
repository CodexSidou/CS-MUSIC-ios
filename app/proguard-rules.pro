# ── yt-dlp / youtubedl-android ──────────────────────────────────────────────
-keep class com.yausername.yt_dlp.** { *; }
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-dontwarn com.yausername.**

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { <init>(); }
# Keep Room DAO implementations (generated via KSP)
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }

# ── jAudioTagger ─────────────────────────────────────────────────────────────
# jAudioTagger uses reflection to find tag implementations
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Coil ─────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── General Kotlin reflection ─────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
