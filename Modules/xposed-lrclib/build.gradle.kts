plugins {
    id("com.android.application")
}

android {
    namespace = "com.lance.ytmusichyperlyric.xposed"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lance.ytmusichyperlyric.xposed"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.2.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    // LSPosed injects this API into the target process. It must not be packaged into the APK.
    compileOnly("io.github.libxposed:api:102.0.0")

    // Formal Lyricon provider protocol used by HyperLyric's SystemUI source.
    implementation("io.github.proify.lyricon:provider:0.1.70")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
}
