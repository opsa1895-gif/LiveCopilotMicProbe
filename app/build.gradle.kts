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
        versionCode = 13
        versionName = "0.13.0-vad-hysteresis"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
