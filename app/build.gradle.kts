plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Semnarea release: daca workflow-ul GitHub primeste un keystore (secrets),
// APK-ul se semneaza cu el. Altfel se foloseste cheia de debug - APK-ul
// ramane instalabil, dar nu se poate actualiza peste o versiune semnata altfel.
val keystorePath: String? = System.getenv("VICPROJ_KEYSTORE_PATH")
val hasReleaseKeystore = keystorePath != null && file(keystorePath).exists()

android {
    namespace = "md.vicproj.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "md.vicproj.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "1.4.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("VICPROJ_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VICPROJ_KEY_ALIAS")
                keyPassword = System.getenv("VICPROJ_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 oprit intentionat: aplicatia n-a putut fi testata pe dispozitiv inainte de livrare,
            // iar un APK putin mai mare e mai sigur decat unul "optimizat" care poate crapa la rulare.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
    lint {
        // un avertisment de lint nu trebuie sa opreasca build-ul din GitHub Actions
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Scanare QR: CameraX + ML Kit (model INCLUS in aplicatie, functioneaza offline)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Generare QR
    implementation("com.google.zxing:core:3.5.3")

    // Retea + stocare criptata a sesiunii
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
