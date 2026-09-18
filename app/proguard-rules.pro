# CRANK Music ProGuard Keep Rules for Production Release

# Keep Room Database Entities and DAOs
-keep class androidx.room.** { *; }
-keep class com.crank.music.data.local.** { *; }

# Keep Domain Models and Serialization Data Classes
-keep class com.crank.music.domain.model.** { *; }
-keep class com.crank.music.data.remote.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep Hilt Modules and Injected Classes
-keep class com.crank.music.di.** { *; }
-keep class dagger.hilt.** { *; }

# Keep Media3 ExoPlayer and AudioFX
-keep class androidx.media3.** { *; }
-keep class android.media.audiofx.** { *; }

# Keep Ktor Client Engine and Serialization
-keep class io.ktor.** { *; }

# Keep Coil Image Loading
-keep class coil3.** { *; }
