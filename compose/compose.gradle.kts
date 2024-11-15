import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.freeletics.gradle.multiplatform")
    id("com.freeletics.gradle.publish.oss")
}

freeletics {
    useCompose()

    // TODO https://youtrack.jetbrains.com/issue/CMP-3344
    // multiplatform {
    //     addCommonTargets()
    // }
}

kotlin {
    jvm()

    js(IR) {
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    linuxX64()
    linuxArm64()

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
    commonMainApi(libs.jetbrains.compose.runtime)
    commonMainImplementation(libs.coroutines.core)
}
