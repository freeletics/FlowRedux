plugins {
    alias(libs.plugins.fgp.multiplatform)
}

freeletics {
    multiplatform {
        addCommonTargets()
    }
}
