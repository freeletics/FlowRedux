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

    private val stateMachine = PaginationStateMachine(
        logger = AndroidFlowReduxLogger,
        githubApi = GithubApi(),
        scope = viewModelScope
    ).also {
        it.start(::onStateChanged)
    }

    private fun onStateChanged(state: PaginationState) {
        liveData.value = state
    }

    fun dispatch(action: Action) {
        stateMachine.dispatch(action)
    }
}
