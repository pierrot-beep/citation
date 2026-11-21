plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.example.radaralerte"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.example.radaralerte"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
    // Carte OpenStreetMap, sans clé d'API
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
