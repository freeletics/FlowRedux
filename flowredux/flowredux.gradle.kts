plugins {
    alias(libs.plugins.fgp.multiplatform)
    alias(libs.plugins.fgp.publish)
}

freeletics {
    explicitApi()

    addCommonTargets()
}

dependencies {
    commonMainApi(libs.coroutines.core)
    commonMainApi(libs.mad.statemachine)

    commonTestImplementation(libs.kotlin.test)
    commonTestImplementation(libs.kotlin.test.annotations)
    commonTestImplementation(libs.turbine)
    commonTestImplementation(libs.coroutines.test)
}