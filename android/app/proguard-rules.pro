# kotlinx.serialization: conservar serializadores de los modelos
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class mx.finanzas.pareja.data.** {
    *** Companion;
}
-keepclasseswithmembers class mx.finanzas.pareja.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
