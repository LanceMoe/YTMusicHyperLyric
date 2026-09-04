import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "YTMusicHyperLyric"
include(":plugins:api")
include(":plugins:lrclib")
include(":modules:xposed-lrclib")

project(":plugins").projectDir = file("Plugins")
project(":plugins:api").projectDir = file("Plugins/api")
project(":plugins:lrclib").projectDir = file("Plugins/modules/lrclib")
project(":modules:xposed-lrclib").projectDir = file("Modules/xposed-lrclib")
