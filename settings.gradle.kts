pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven {setUrl("https://oss.sonatype.org/content/repositories/snapshots/")}
    }
}

plugins {
    id("com.freeletics.gradle.settings").version("js-repository-SNAPSHOT")
}

rootProject.name = "flowredux-library"

configure<com.freeletics.gradle.plugin.SettingsExtension> {
    snapshots()
}