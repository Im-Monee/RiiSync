# RiiSync ProGuard Rules

# JGit rules (Essential for clone/pull logic)
-keep class org.eclipse.jgit.** { *; }
-keep interface org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-keepattributes *Annotation*, EnclosingMethod, Signature

# Shizuku / Rikka Rules (Essential for AIDL and Privileged API)
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Slf4j (JGit dependency)
-dontwarn org.slf4j.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    java.lang.String name;
}

# Android Crypto (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.errorprone.annotations.**

# JGit & Network
-keep class org.eclipse.jgit.** { *; }
-keep interface org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*, EnclosingMethod, Signature

# Shizuku / Rikka Rules (Essential for AIDL and Privileged API)
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Slf4j
-dontwarn org.slf4j.**

# Coil (Image Loading)
-keep class coil.** { *; }
-dontwarn coil.**
-dontwarn okio.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    java.lang.String name;
}
