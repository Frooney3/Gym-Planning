plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes the run number so every build installs as an update over the last one
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "app.spotter.trainer"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.spotter.trainer"
        minSdk = 28
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    // The permanent signing key comes from GitHub secrets (never committed). Without it the
    // build falls back to a throwaway debug key, and each build then needs a fresh install.
    val keystorePath = System.getenv("SPOTTER_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SPOTTER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SPOTTER_KEY_ALIAS") ?: "spotter"
                keyPassword = System.getenv("SPOTTER_KEY_PASSWORD") ?: System.getenv("SPOTTER_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
