import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

// Load release signing config from keystore.properties (git-ignored, never committed).
// If the file is missing (e.g. a fresh clone without the keystore), release signing is
// skipped gracefully so debug builds still work.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

android {
    namespace = "com.akai"
    compileSdk = 36

    // Play Asset Delivery: link the models asset pack (see models_pack module).
    // Ships AkAI's ~857MB of ML models outside the base app, install-time delivery.
    assetPacks += listOf(":models_pack")

    defaultConfig {
        applicationId = "com.akai"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.7.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only include ARM architectures — reduces APK size significantly
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            // Only configure if keystore.properties was found — keeps fresh clones building.
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Sign release builds with our upload keystore (only if it's configured).
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Required for TFLite select-tf-ops (LSTM support)
    aaptOptions {
        noCompress += "tflite"
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // MediaPipe
    implementation(libs.mediapipe.tasks.vision)

    // TensorFlow Lite + LSTM support
    implementation(libs.tflite)
    implementation(libs.tflite.select.tf.ops)

    // Lifecycle ViewModel + LiveData
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Coroutines
    implementation(libs.coroutines.android)

    // Vosk offline STT
    implementation(libs.vosk)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    // Nearby Connections — offline device-to-device conversation sync
    implementation(libs.play.services.nearby)


    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}