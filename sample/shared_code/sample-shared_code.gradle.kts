plugins {
    alias(libs.plugins.fgp.multiplatform)
}

freeletics {
    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")

    multiplatform {
        addJvmTarget()
        addIosTargets("shared", true)
    }
}

dependencies {
    commonMainApi(projects.flowredux)
    commonMainApi(libs.kotlinx.collections.immutable)
}
