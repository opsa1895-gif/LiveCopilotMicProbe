plugins {
    id("com.android.application")
}

android {
    namespace = "com.livecopilot.micprobe"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.livecopilot.micprobe"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-probe"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
