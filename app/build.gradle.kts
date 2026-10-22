plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.relay"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.relay"
        minSdk = 26
        targetSdk = 34
        versionCode = 115
        versionName = "1.1.5"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
