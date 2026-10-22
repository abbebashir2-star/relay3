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
        versionCode = 400
        versionName = "4.0.0"
    }
    buildTypes { getByName("debug") { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { }
