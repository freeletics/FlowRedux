package com.freeletics.flowredux

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.GithubApi
import com.freeletics.flowredux.sample.shared.GithubRepository
import com.freeletics.flowredux.sample.shared.PaginationStateMachine
import com.freeletics.flowredux.sample.shared.PaginationState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.defaultSerializer
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import kotlinx.serialization.json.Json
import okhttp3.logging.HttpLoggingInterceptor

class PopularRepositoriesViewModel : ViewModel() {

    val liveData = MutableLiveData<PaginationState>()
    private val httpClient = HttpClient(OkHttp) {

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }

        engine {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            addInterceptor(loggingInterceptor)
        }

        install(JsonFeature) {
            serializer = KotlinxSerializer(Json.nonstrict)
        }
    }

    private val stateMachine = PaginationStateMachine(
        logger = AndroidFlowReduxLogger,
        githubApi = GithubApi(httpClient = httpClient),
        scope = viewModelScope,
        stateChangeListener = ::onStateChanged
    )

    private fun onStateChanged(state: PaginationState) {
        liveData.value = state
    }

    fun dispatch(action: Action) {
        stateMachine.dispatch(action)
    }
}
