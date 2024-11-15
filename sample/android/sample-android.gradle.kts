plugins {
    id("com.freeletics.gradle.android.app")
}

freeletics {
    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
    useCompose()

    android {
        enableViewBinding()
    }
}

dependencies {
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
