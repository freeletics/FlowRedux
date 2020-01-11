package com.freeletics.flowredux

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.PaginationStateMachine
import com.freeletics.flowredux.sample.shared.PaginationState

    class PopularRepositoriesViewModel : ViewModel() {

    val liveData = MutableLiveData<PaginationState>()

    private val stateMachine = PaginationStateMachine(
        AndroidFlowReduxLogger,
        viewModelScope,
        this::onStateChanged
    )

    private fun onStateChanged(state: PaginationState) {
        liveData.value = state
    }

    fun dispatch(action: Action) {
        stateMachine.dispatch(action)
    }
}
