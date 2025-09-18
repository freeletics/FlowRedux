plugins {
    id("com.freeletics.gradle.multiplatform")
    id("com.freeletics.gradle.publish.oss")
}

freeletics {
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
