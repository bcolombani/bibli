# R8 est désactivé en v1 (voir app/build.gradle.kts). Ces règles sont là pour le jour
# où `isMinifyEnabled` repassera à true ; elles couvrent les trois points sensibles.

# --- kotlinx.serialization ---------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations
-dontnote kotlinx.serialization.**
-keepclassmembers class fr.bcolombani.bibli.** {
    *** Companion;
}
-keepclasseswithmembers class fr.bcolombani.bibli.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room --------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- ML Kit (modèle de scan embarqué) ----------------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**
