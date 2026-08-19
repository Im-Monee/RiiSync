plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.riisync.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.riisync.app"
        minSdk = 26          // Shizuku richiede almeno API 24; usiamo 26 per sicurezza
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        aidl = true // necessario per l'interfaccia AIDL di Shizuku
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            pickFirsts.add("plugin.properties")
        }
    }
}

dependencies {
    // --- Jetpack Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Coroutines (per operazioni Git/IO in background) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- JGit: implementazione Git pura Java, funziona su Android ---
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.jsch:6.7.0.202309050840-r")
    implementation("org.slf4j:slf4j-android:1.7.36") // logging richiesto da jgit

    // --- Shizuku API: comunicazione con il servizio Shizuku ---
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // --- DocumentFile per gestione cartelle SAF (fallback senza Shizuku) ---
    implementation("androidx.documentfile:documentfile:1.0.1")

    // --- Security: EncryptedSharedPreferences ---
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- Network: Jsoup per il crawler dei salvataggi Wii ---
    implementation("org.jsoup:jsoup:1.17.2")

    implementation("androidx.core:core-ktx:1.13.1")
}
