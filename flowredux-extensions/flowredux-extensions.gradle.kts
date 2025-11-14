import kotlin.jvm.java
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("com.freeletics.gradle.multiplatform")
    id("app.cash.burst")
}

freeletics {
    enableOssPublishing()

    multiplatform {
        // TODO stop limiting when it's possible to properly exclude targets from compose
        addCommonTargets(limitToComposeTargets = true)
    }
}

dependencies {
    commonMainApi(libs.coroutines.core)
    commonMainApi(projects.flowredux)

    commonTestImplementation(libs.kotlin.test)
    commonTestImplementation(libs.kotlin.test.annotations)
    commonTestImplementation(libs.turbine)
    commonTestImplementation(libs.coroutines.test)
}

// see the @Suppress("INVISIBLE_REFERENCE")
tasks.withType(KotlinCompilationTask::class.java).configureEach {
    this.compilerOptions {
        freeCompilerArgs.add("-Xdont-warn-on-error-suppression")
    }
}
