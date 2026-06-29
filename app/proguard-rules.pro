# Keep kotlinx.serialization metadata for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.yourname.nearness.**$$serializer { *; }
-keepclassmembers class com.yourname.nearness.** {
    *** Companion;
}

# Supabase / Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
