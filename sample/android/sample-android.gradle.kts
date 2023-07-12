plugins {
    alias(libs.plugins.fgp.android.app)
}

freeletics {
    enableCompose()
    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
}

dependencies {
    implementation(libs.androidx.core)
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
    implementation(libs.androidx.compose.material)
    implementation(libs.adapterdelegates)
    implementation(libs.adapterdelegates.dsl)
    implementation(libs.coroutines.core)
    implementation(libs.material)
    implementation(libs.timber)

    implementation(projects.sample.sharedCode)
    implementation(projects.flowredux)
    implementation(projects.compose)
}