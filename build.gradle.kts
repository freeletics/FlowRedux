plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.android.app).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.jetbrains.compose).apply(false)
    alias(libs.plugins.dependency.analysis).apply(false)
    alias(libs.plugins.dokka).apply(false)
    alias(libs.plugins.publish).apply(false)

    alias(libs.plugins.fgp.root)
    alias(libs.plugins.binarycompatibility)
}

apiValidation {
    ignoredProjects += listOf(
        "android",
        "shared_code",
    )

    nonPublicMarkers += listOf(
        "androidx.annotation.VisibleForTesting",
    )
}
