package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.dsl.internal.Action
import com.freeletics.flowredux.dsl.internal.ExternalWrappedAction
import com.freeletics.flowredux.dsl.internal.InitialStateAction
import com.freeletics.flowredux.dsl.internal.reducer
import com.freeletics.flowredux.reduxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

/**
 * Provides a fluent DSL to specify a ReduxStore
 */
@FlowPreview
@ExperimentalCoroutinesApi
public fun <S : Any, A : Any> Flow<A>.reduxStore(
    initialStateSupplier: () -> S,
    block: FlowReduxStoreBuilder<S, A>.() -> Unit
): Flow<S> {
    val builder = FlowReduxStoreBuilder<S, A>()
    block(builder)

    return this.map<A, Action<S, A>> { ExternalWrappedAction(it) }
        .onStart {
            emit(InitialStateAction())
        }
        .reduxStore(
            initialStateSupplier = initialStateSupplier,
            reducer = ::reducer,
            sideEffects = builder.generateSideEffects()
        )
        .distinctUntilChanged { old, new -> old === new } // distinct until not the same object reference.
}

/**
 * Provides a fluent DSL to specify a ReduxStore
 */
@FlowPreview
@ExperimentalCoroutinesApi
public fun <S : Any, A : Any> Flow<A>.reduxStore(
    initialState: S,
    block: FlowReduxStoreBuilder<S, A>.() -> Unit
): Flow<S> =
    this.reduxStore(initialStateSupplier = { initialState }, block = block)

