plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it.orNull?.isNotBlank() == true }

android {
    namespace = "moe.lance.ytmusiclrc"
    compileSdk = 37

    defaultConfig {
        applicationId = "moe.lance.ytmusiclrc"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.3.1"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("ytmusiclrc-${variant.name}.apk")
        }
    }
}

dependencies {
    // LSPosed injects this API into the target process. It must not be packaged into the APK.
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")

    // Formal Lyricon provider protocol used by HyperLyric's SystemUI source.
    implementation("io.github.proify.lyricon:provider:0.1.70")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")

    // Miuix provides the Compose-native visual language used by the settings screens.
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.1")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.1")
    implementation("androidx.activity:activity-compose:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("org.json:json:20240303")
}

// Debug builds and unit tests need no signing credentials. Release packaging fails closed.
val requireReleaseSigning = tasks.register("requireReleaseSigning") {
    doLast {
        check(releaseSigningReady) {
            "Release signing requires RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD. See README.md."
        }
        check(file(releaseStoreFile.get()).isFile) { "Release keystore does not exist" }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(requireReleaseSigning)
}
