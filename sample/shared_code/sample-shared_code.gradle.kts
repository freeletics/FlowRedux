plugins {
    alias(libs.plugins.fgp.multiplatform)
}

freeletics {
    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")

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
