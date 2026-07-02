# Keep kotlinx.serialization metadata for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.raycc.nearness.**$$serializer { *; }
-keepclassmembers class com.raycc.nearness.** {
    *** Companion;
}

# Keep SignalType enum (emoji + label fields used at runtime)
-keepclassmembers enum com.raycc.nearness.domain.SignalType { *; }
-keepclassmembers enum com.raycc.nearness.domain.ActivityType { *; }
-keepclassmembers enum com.raycc.nearness.domain.WhiteboardItemType { *; }

# Keep Navigation Compose @Serializable Screen routes
-keep,includedescriptorclasses class com.raycc.nearness.ui.Screen$** { *; }

# Supabase / Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
