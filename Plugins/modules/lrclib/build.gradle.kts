import org.gradle.api.tasks.bundling.Zip

plugins {
    id("com.android.application")
}

android {
    namespace = "com.lance.ytmusichyperlyric.plugin.lrclib"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lance.ytmusichyperlyric.plugin.lrclib.build"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // HyperLyric Runtime 在父 ClassLoader 提供 API，禁止复制进插件 DEX。
    compileOnly(project(":plugins:api"))
    testImplementation(project(":plugins:api"))

    // 插件拥有独立的 DEX/ClassLoader 边界，随包携带 Kotlin 运行库。
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")

    testImplementation("junit:junit:4.13.2")
}

val debugApk = layout.buildDirectory.file("outputs/apk/debug/${project.name}-debug.apk")
val releaseApk = layout.buildDirectory.file(
    "outputs/apk/release/${project.name}-release-unsigned.apk",
)

val packagePlugin by tasks.registering(Zip::class) {
    dependsOn("assembleRelease")
    archiveFileName.set("hyperlyric-lrclib-plugin.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/plugin"))
    from(zipTree(releaseApk)) {
        include("classes*.dex")
    }
    from("src/main/plugin") {
        include("manifest.json")
    }
}

val packageDebugPlugin by tasks.registering(Zip::class) {
    dependsOn("assembleDebug")
    archiveFileName.set("hyperlyric-lrclib-plugin-debug.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/plugin"))
    from(zipTree(debugApk)) {
        include("classes*.dex")
    }
    from("src/main/plugin") {
        include("manifest.json")
    }
}
