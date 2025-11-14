plugins {
    alias(libs.plugins.fgp.android.app).apply(false)
    alias(libs.plugins.fgp.multiplatform).apply(false)
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.android.app).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.dependency.analysis).apply(false)
    alias(libs.plugins.burst).apply(false)
    alias(libs.plugins.dokka).apply(false)
    alias(libs.plugins.publish).apply(false)

    alias(libs.plugins.fgp.root)
}
