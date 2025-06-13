package com.freeletics.flowredux2

import android.app.Application
import timber.log.Timber

open class SampleApplication : Application() {
    init {
        Timber.plant(Timber.DebugTree())
    }
}
