# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.gowaist.**$$serializer { *; }
-keepclassmembers class com.gowaist.** { *** Companion; }
-keepclasseswithmembers class com.gowaist.** { kotlinx.serialization.KSerializer serializer(...); }
# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
