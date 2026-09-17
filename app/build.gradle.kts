plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.esp32file"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.esp32file"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
