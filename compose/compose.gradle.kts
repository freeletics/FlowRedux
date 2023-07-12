plugins {
    alias(libs.plugins.fgp.multiplatform)
    alias(libs.plugins.fgp.publish)
    alias(libs.plugins.compose.multiplatform)
}

freeletics {
    explicitApi()

    // TODO https://github.com/JetBrains/compose-multiplatform/issues/3344
    // addCommonTargets(androidNativeTargets = false)
}

compose {
    kotlinCompilerPlugin.set(libs.versions.compose.multiplatform.compiler)
}


kotlin {
    jvm()

    js(IR) {
        nodejs()
    }

    linuxX64()
    // TODO linuxArm64()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    macosArm64()
    macosX64()

    mingwX64()

    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()

    watchosArm32()
    watchosArm64()
    // TODO watchosDeviceArm64()
    watchosX64()
    watchosSimulatorArm64()
}

dependencies {
    commonMainApi(projects.flowredux)
    commonMainApi(libs.compose.multiplatform.runtime)
    commonMainImplementation(libs.coroutines.core)
}
