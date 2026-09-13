plugins {
    id("com.android.application")
}

android {
    namespace = "com.livecopilot.micprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.livecopilot.micprobe"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-live-replies"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
