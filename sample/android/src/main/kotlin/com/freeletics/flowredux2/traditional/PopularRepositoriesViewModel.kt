package com.freeletics.flowredux2.traditional

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeletics.flowredux2.sample.shared.Action
import com.freeletics.flowredux2.sample.shared.GithubApi
import com.freeletics.flowredux2.sample.shared.PaginationState
import com.freeletics.flowredux2.sample.shared.PaginationStateMachine

internal class PopularRepositoriesViewModel : ViewModel() {
    private val liveData = MutableLiveData<PaginationState>()

    private val stateMachine = PaginationStateMachine(
        githubApi = GithubApi(),
        scope = viewModelScope,
    ).apply { start(liveData::setValue) }

    internal val stateLiveData: LiveData<PaginationState> = liveData

    internal fun dispatch(action: Action) = stateMachine.dispatch(action)
}
