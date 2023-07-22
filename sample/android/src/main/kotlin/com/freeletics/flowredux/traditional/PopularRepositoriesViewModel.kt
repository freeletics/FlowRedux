package com.freeletics.flowredux.traditional

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.GithubApi
import com.freeletics.flowredux.sample.shared.PaginationState
import com.freeletics.flowredux.sample.shared.PaginationStateMachine

class PopularRepositoriesViewModel : ViewModel() {

    val liveData = MutableLiveData<PaginationState>()

    private val stateMachine = PaginationStateMachine(
        githubApi = GithubApi(),
        scope = viewModelScope,
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
