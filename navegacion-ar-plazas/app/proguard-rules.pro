# kotlinx.serialization genera serializadores por cada @Serializable.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.rumbo.nucleo.modelo.** {
    *** Companion;
}
-keepclasseswithmembers class com.rumbo.nucleo.modelo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
