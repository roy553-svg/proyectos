# kotlinx.serialization: conserva los serializadores generados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.elprofeta.app.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.elprofeta.app.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit.
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
