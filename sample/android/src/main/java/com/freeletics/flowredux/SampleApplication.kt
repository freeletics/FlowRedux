package com.freeletics.flowredux

import android.app.Application
import timber.log.Timber

open class SampleApplication : Application() {
    init {
        Timber.plant(Timber.DebugTree())
    }
}
