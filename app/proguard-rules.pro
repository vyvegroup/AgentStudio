# ===========================================
# VenCA Security Framework - ProGuard Rules
# Enterprise-Grade DEX Protection
# ===========================================

# ==================== VenCA Security ====================
# Obfuscate all security classes
-repackageclasses 'v'
-allowaccessmodification
-optimizationpasses 5

# Keep VenCA public API
-keep class com.agentstudio.security.VenCA { 
    public *; 
}
-keep class com.agentstudio.security.SecurityConfig { *; }
-keep class com.agentstudio.security.SecurityReport { *; }
-keep class com.agentstudio.security.ObfuscatedString { *; }

# Obfuscate string encryption methods internally
-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# ==================== String Encryption ====================
# Encrypt all string constants
-adaptclassstrings com.agentstudio.**
-adaptclassstrings com.agentstudio.security.**

# ==================== Anti-Debug ====================
# Remove debug logs in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# ==================== Anti-Tamper ====================
# Keep signature verification
-keep class android.content.pm.PackageManager { *; }
-keep class android.content.pm.Signature { *; }

# ==================== Data Models ====================
# Keep for JSON serialization
-keep class com.agentstudio.data.model.** { *; }
-keep class com.agentstudio.data.api.** { *; }

# Keep @Serializable classes
-keep class kotlinx.serialization.Serializable {
    @kotlinx.serialization.<fields>;
}
-keepclassmembers class kotlinx.serialization.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class * implements kotlinx.serialization.InternalSerializationConverter
-keep class * implements kotlinx.serialization.KSerializer

# ==================== Build Config ====================
-keep class com.agentstudio.BuildConfig { *; }

# ==================== OkHttp ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Certificate pinning
-keep class okhttp3.CertificatePinner { *; }
-keep class okhttp3.CertificatePinner$Builder { *; }

# ==================== Kotlin & Coroutines ====================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ==================== Compose ====================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ==================== Security Libraries ====================
-keep class androidx.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# ==================== Anti-Decompilation ====================
# Make code harder to analyze
-repackageclasses
-allowaccessmodification
-mergeinterfacesaggressively

# Remove unused code
-shrink
-optimizationpasses 5

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==================== Native NDK ====================
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI methods
-keep class com.agentstudio.native.** { *; }
