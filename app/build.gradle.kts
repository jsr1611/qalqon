import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uz.jumanazar.qalqon"
    compileSdk = 36

    defaultConfig {
        applicationId = "uz.jumanazar.qalqon"
        minSdk = 29
        targetSdk = 36
        // Driven by CI (GitHub run number) so every build bumps; falls back for local builds.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"

        // Secret for developer challenge-response recovery. Injected in CI from the
        // RECOVERY_SECRET GitHub secret; a harmless dev default is used for local builds
        // (never ship a public APK built locally for real recovery).
        val recoverySecret = System.getenv("RECOVERY_SECRET") ?: "qalqon-dev-insecure"
        buildConfigField("String", "RECOVERY_SECRET", "\"$recoverySecret\"")
    }

    signingConfigs {
        create("release") {
            // Credentials are read from environment variables in CI.
            // Falls back silently to an unsigned build when they are absent (local dev).
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.biometric:biometric:1.1.0")
    // biometric 1.1.0 pulls an old fragment; force a modern one for ActivityResult APIs.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
