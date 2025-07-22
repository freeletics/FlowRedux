import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.freeletics.gradle.multiplatform")
    id("com.freeletics.gradle.publish.oss")
}

freeletics {
    useCompose()

    multiplatform {
        // TODO stop limiting when it's possible to properly exclude targets from compose
        addCommonTargets(limitToComposeTargets = true)
    }
}

kotlin {
    sourceSets {
        val compose = create("composeMain") {
            dependsOn(get("commonMain"))
        }

        listOf(
            "jvmMain",
            "jsMain",
            "nativeMain",
            "wasmJsMain",
        ).forEach {
            if (it.endsWith("Main")) {
                get(it).dependsOn(compose)
            }
        }
    }
}

dependencies {
    commonMainApi(libs.coroutines.core)
    commonMainApi(libs.statemachine)
    "composeMainApi"(libs.jetbrains.compose.runtime)

    commonTestImplementation(libs.kotlin.test)
    commonTestImplementation(libs.kotlin.test.annotations)
    commonTestImplementation(libs.turbine)
    commonTestImplementation(libs.coroutines.test)
}
