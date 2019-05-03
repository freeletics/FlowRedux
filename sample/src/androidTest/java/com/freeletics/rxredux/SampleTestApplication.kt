package com.freeletics.rxredux

import android.view.ViewGroup
import com.freeletics.di.TestApplicationModule
import com.freeletics.rxredux.di.DaggerApplicationComponent
import io.reactivex.android.schedulers.AndroidSchedulers

class SampleTestApplication : SampleApplication() {

    override fun componentBuilder(builder: DaggerApplicationComponent.Builder) =
        builder.applicationModule(
            TestApplicationModule(
                baseUrl = "http://127.0.0.1:$MOCK_WEB_SERVER_PORT",
                androidScheduler = AndroidSchedulers.mainThread(),
                viewBindingInstantiatorMap = mapOf<Class<*>, ViewBindingInstantiator>(
                    PopularRepositoriesActivity::class.java to { rootView: ViewGroup ->
                        RecordingPopularRepositoriesViewBinding(
                            rootView
                        )
                    }
                )
            )
        )

}
