# Aero Browser R8 rules. Keep these rules scoped to code reached reflectively;
# Android Gradle Plugin, Room, and Hilt also contribute their own consumer rules.

# Room: database/entity/DAO annotations and the generated implementations are
# used by Room's generated code. Keep entity constructor/data members stable so
# annotation-driven mapping and migrations remain compatible after obfuscation.
-keep @androidx.room.Database class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Hilt application entry points are instantiated by Android/application wiring.
-keep,allowoptimization @dagger.hilt.android.HiltAndroidApp class *
-keep,allowoptimization @dagger.hilt.android.lifecycle.HiltViewModel class *
-keepclassmembers,allowoptimization class * {
    @javax.inject.Inject <init>(...);
}

# WebView JS bridges (none are currently registered; retain annotated bridge
# methods if a reviewed bridge is introduced in a future change).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Conditional keeps only affect models if a corresponding serialization
# library is added; they preserve annotation-addressed members while allowing
# obfuscation of unrelated model code.
-if class kotlinx.serialization.Serializable
-keep,allowoptimization @kotlinx.serialization.Serializable class * { *; }
-if class com.google.gson.Gson
-keepclassmembers,allowoptimization class * { @com.google.gson.annotations.SerializedName <fields>; }
-if class com.squareup.moshi.JsonAdapter
-keepclassmembers,allowoptimization class * { @com.squareup.moshi.Json <fields>; }

# AndroidX Security Crypto/Tink references Error Prone annotations only at
# compile time; the annotations are absent from the runtime artifact.
-dontwarn com.google.errorprone.annotations.**
