# Room genera implementaciones por reflexión en algunos puntos.
-keep class com.nfckeyblock.data.local.** { *; }
# El AccessibilityService y los receivers se instancian por nombre desde el sistema.
-keep class com.nfckeyblock.service.** { *; }
-keepattributes *Annotation*
