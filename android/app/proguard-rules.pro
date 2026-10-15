# kotlinx.serialization: conservar serializadores de los modelos
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class mx.finanzas.pareja.data.** {
    *** Companion;
}
-keepclasseswithmembers class mx.finanzas.pareja.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Otras clases @Serializable que se guardan como JSON en el teléfono (cola de cobros, no reconocidas)
-keepclassmembers class mx.finanzas.pareja.domain.** { *** Companion; }
-keepclasseswithmembers class mx.finanzas.pareja.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class mx.finanzas.pareja.capture.** { *** Companion; }
-keepclasseswithmembers class mx.finanzas.pareja.capture.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# PDFBox para Android carga fuentes y recursos por nombre y usa reflexión
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**

# WorkManager instancia el worker por reflexión
-keep class mx.finanzas.pareja.alerts.CycleAlertWorker { *; }
