plugins {
    alias(libs.plugins.fgp.android.app)
    alias(libs.plugins.android.app)
}

freeletics {
    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")

    android {
        enableCompose()
        enableViewBinding()
    }
}

android {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + buildComposeMetricsParameters()
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle)
    implementation(libs.androidx.viewmodel)
    implementation(libs.androidx.livedata)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.adapterdelegates)
    implementation(libs.adapterdelegates.dsl)
    implementation(libs.coroutines.core)
    implementation(libs.material)
    implementation(libs.timber)
    implementation(libs.kotlinx.collections.immutable)

    implementation(projects.sample.sharedCode)
    implementation(projects.flowredux)
    implementation(projects.compose)
}

fun Project.buildComposeMetricsParameters(): List<String> = buildList(4) {
    val enableMetricsProvider = project.providers.gradleProperty("enableComposeCompilerMetrics")
    val enableMetrics = enableMetricsProvider.orNull == "true"
    if (enableMetrics) {
        val metricsFolderAbsolutePath = project.layout.buildDirectory
            .file("compose-metrics")
            .map { it.asFile.absolutePath }
            .get()

        add("-P")
        add(
            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$metricsFolderAbsolutePath"
        )
    }

    val enableReportsProvider = project.providers.gradleProperty("enableComposeCompilerReports")
    val enableReports = enableReportsProvider.orNull == "true"
    if (enableReports) {
        val reportsFolderAbsolutePath = project.layout.buildDirectory
            .file("compose-reports")
            .map { it.asFile.absolutePath }
            .get()

        add("-P")
        add(
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$reportsFolderAbsolutePath",
        )
    }
}
