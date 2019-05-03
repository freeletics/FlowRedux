package com.freeletics.di

import com.freeletics.rxredux.ViewBindingInstantiatorMap
import com.freeletics.rxredux.di.ApplicationModule
import io.reactivex.Scheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


class TestApplicationModule(
    baseUrl: String,
    viewBindingInstantiatorMap: ViewBindingInstantiatorMap,
    androidScheduler: Scheduler
) : ApplicationModule(
    baseUrl = baseUrl,
    viewBindingInstantiatorMap = viewBindingInstantiatorMap,
    androidScheduler = androidScheduler
) {

    override fun provideOkHttp(): OkHttpClient =
        super.provideOkHttp().newBuilder()
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .also {
                // TODO https://github.com/square/okhttp/issues/4183
                /*
                val clientCertificates = HandshakeCertificates.Builder()
                    .addTrustedCertificate(localhostCertificate.certificate())
                    .build()

                it.sslSocketFactory(
                    clientCertificates.sslSocketFactory(),
                    clientCertificates.trustManager()
                )
                */
            }
            .build()


}
