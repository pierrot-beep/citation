plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.example.citationdujour"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.example.citationdujour"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
