# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ankredev.loxwidget.** {
    *** Companion;
}
-keepclasseswithmembers class com.ankredev.loxwidget.** {
    kotlinx.serialization.KSerializer serializer(...);
}
