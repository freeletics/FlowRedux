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


dependencyAnalysis {
    issues {
        all {
            onUnusedDependencies {
                // auto-added by the Kotlin JS toolchain via the multiplatform plugin
                exclude("org.jetbrains.kotlin:kotlin-dom-api-compat")
            }
        }
    }

    structure {
        bundle("compose-ui") {
            primary("org.jetbrains.compose.ui:ui")
            includeGroup("androidx.compose.ui")
            includeGroup("org.jetbrains.compose.ui")
        }
        bundle("compose-foundation") {
            primary("org.jetbrains.compose.foundation:foundation")
            includeGroup("androidx.compose.animation")
            includeGroup("androidx.compose.foundation")
            includeGroup("org.jetbrains.compose.animation")
            includeGroup("org.jetbrains.compose.foundation")
        }
        bundle("lifecycle-common") {
            primary("org.jetbrains.androidx.lifecycle:lifecycle-common")
            includeDependency("androidx.lifecycle:lifecycle-common")
            includeDependency("androidx.lifecycle:lifecycle-common-android")
            includeDependency("androidx.lifecycle:lifecycle-common-desktop")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-common")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-common-android")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-common-desktop")
        }
        bundle("lifecycle-runtime") {
            primary("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose")
            includeDependency("androidx.lifecycle:lifecycle-runtime")
            includeDependency("androidx.lifecycle:lifecycle-runtime-android")
            includeDependency("androidx.lifecycle:lifecycle-runtime-desktop")
            includeDependency("androidx.lifecycle:lifecycle-runtime-compose")
            includeDependency("androidx.lifecycle:lifecycle-runtime-compose-android")
            includeDependency("androidx.lifecycle:lifecycle-runtime-compose-desktop")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-runtime")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-runtime-android")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-runtime-desktop")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-android")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-desktop")
        }
        bundle("lifecycle-viewmodel") {
            primary("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate")
            includeDependency("androidx.lifecycle:lifecycle-viewmodel")
            includeDependency("androidx.lifecycle:lifecycle-viewmodel-android")
            includeDependency("androidx.lifecycle:lifecycle-viewmodel-desktop")
            includeDependency("androidx.lifecycle:lifecycle-viewmodel-savedstate")
            includeDependency("androidx.lifecycle:lifecycle-viewmodel-savedstate-android")
            includeDependency("androidx.lifecycle:lifecycle-viewmodel-savedstate-desktop")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-android")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-desktop")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate-android")
            includeDependency("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate-desktop")
        }
        bundle("savedstate") {
            primary("androidx.savedstate:savedstate")
            includeGroup("androidx.savedstate")
        }
    }
}
