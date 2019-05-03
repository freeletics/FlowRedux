package com.freeletics.rxredux

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.freeletics.rxredux.businesslogic.pagination.Action
import com.freeletics.rxredux.businesslogic.pagination.PaginationStateMachine
import com.freeletics.rxredux.di.AndroidScheduler
import com.jakewharton.rxrelay2.PublishRelay
import com.jakewharton.rxrelay2.Relay
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import javax.inject.Inject

class PopularRepositoriesViewModel @Inject constructor(
    paginationStateMachine: PaginationStateMachine,
    @AndroidScheduler androidScheduler : Scheduler
) : ViewModel() {

    private val inputRelay: Relay<Action> = PublishRelay.create()
    private val mutableState = MutableLiveData<PaginationStateMachine.State>()
    private val disposables = CompositeDisposable()

    val input: Consumer<Action> = inputRelay
    val state: LiveData<PaginationStateMachine.State> = mutableState

    init {
        disposables.add(inputRelay.subscribe(paginationStateMachine.input))
        disposables.add(
            paginationStateMachine.state
                .observeOn(androidScheduler)
                .subscribe { state -> mutableState.value = state }
        )
    }

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}
