plugins {
    alias(libs.plugins.fgp.multiplatform)
}

freeletics {
    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")

    addJvmTarget()
    addIosTargets("shared", true)
}

dependencies {
    commonMainApi(projects.flowredux)
}
