plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.quicktimer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quicktimer.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 4
        versionName = "1.1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
