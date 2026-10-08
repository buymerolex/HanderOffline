plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.hander.novelreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.hander.novelreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
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
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.compose.material:material-icons-extended")

    // PDF (offline reading - unchanged from before)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // networking for online sources
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // cover images
    implementation("io.coil-kt:coil-compose:2.6.0")

    // media session (notification controls, headset button, lock screen)
    implementation("androidx.media:media:1.7.0")

    // coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
