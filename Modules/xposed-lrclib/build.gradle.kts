plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "moe.lance.ytmusiclyric"
    compileSdk = 37

    defaultConfig {
        applicationId = "moe.lance.ytmusiclyric"
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
            // The repository does not contain a private release keystore yet.
            // Use the Android debug keystore so assembleRelease produces an
            // installable APK instead of an unsigned APK.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // LSPosed injects this API into the target process. It must not be packaged into the APK.
    compileOnly("io.github.libxposed:api:102.0.0")

    // Formal Lyricon provider protocol used by HyperLyric's SystemUI source.
    implementation("io.github.proify.lyricon:provider:0.1.70")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")

    // Miuix provides the Compose-native visual language used by the settings screens.
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.1")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.1")
    implementation("androidx.activity:activity-compose:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
