plugins {
    id("com.freeletics.gradle.multiplatform")
}

freeletics {
    multiplatform {
        addJvmTarget()
        addIosTargetsWithXcFramework("shared")
    }
}

dependencies {
    commonMainApi(projects.flowredux)
    commonMainApi(libs.kotlinx.collections.immutable)
    commonMainApi(libs.jetbrains.compose.runtime)
}
