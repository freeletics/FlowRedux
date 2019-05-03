package com.freeletics.rxredux

import com.freeletics.rxredux.businesslogic.github.GithubRepository
import com.freeletics.rxredux.businesslogic.github.GithubSearchResults
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HeldCertificate
import java.net.InetAddress

const val MOCK_WEB_SERVER_PORT = 56541

private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
private val githubSearchResultsAdapter = moshi.adapter(GithubSearchResults::class.java)

/*
val localhostCertificate = HeldCertificate.Builder()
    .addSubjectAlternativeName(InetAddress.getByName("localhost").canonicalHostName)
    .build()
*/

fun MockWebServer.enqueue200(items: List<GithubRepository>) {
    // TODO why is loading resources not working?
    // val body = MainActivityTest::class.java.getResource("response1.json").readText()

    enqueue(
        MockResponse()
            .setBody(githubSearchResultsAdapter.toJson(GithubSearchResults(items)))
    )
    Thread.sleep(200)
}

fun MockWebServer.setupForHttps(): MockWebServer {
    // TODO https://github.com/square/okhttp/issues/4183
    /*
    val serverCertificates = HandshakeCertificates.Builder()
        .heldCertificate(localhostCertificate)
        .build()

    useHttps(serverCertificates.sslSocketFactory(), false)
    */
    return this
}
