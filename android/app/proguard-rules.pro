# kotlinx.serialization keeps its serializers in companion objects and
# synthetic $serializer classes; R8 must not strip them.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class club.nuva.app.**$$serializer { *; }
-keepclassmembers class club.nuva.app.** {
    *** Companion;
}
-keepclasseswithmembers class club.nuva.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp pull in optional platform classes that are absent on Android.
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn kotlinx.coroutines.debug.**
-keepclassmembers class io.ktor.** { volatile <fields>; }

# Keep crash-report line numbers readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
