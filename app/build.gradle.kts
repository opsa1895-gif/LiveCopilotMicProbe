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
        versionCode = 61
        versionName = "0.60.0-final-turn-replies"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}
