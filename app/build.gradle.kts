plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "com.akai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.akai"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only include ARM architectures — reduces APK size significantly
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
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