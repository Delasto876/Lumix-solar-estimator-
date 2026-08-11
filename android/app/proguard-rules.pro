# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lumix.estimator.**$$serializer { *; }
-keepclassmembers class com.lumix.estimator.** {
    *** Companion;
}
-keepclasseswithmembers class com.lumix.estimator.** {
    kotlinx.serialization.KSerializer serializer(...);
}
