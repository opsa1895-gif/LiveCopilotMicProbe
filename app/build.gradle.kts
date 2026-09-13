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
        versionCode = 10
        versionName = "0.10.0-reply-freshness"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
