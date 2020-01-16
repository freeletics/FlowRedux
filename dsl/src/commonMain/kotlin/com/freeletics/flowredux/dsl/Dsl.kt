package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.reduxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Provides a fluent DSL to specify a ReduxStore
 */
fun <S : Any, A : Any> Flow<A>.reduxStore(
    logger: FlowReduxLogger? = null,
    initialStateSupplier: () -> S,
    block: FlowReduxStoreBuilder<S, A>.() -> Unit
): Flow<S> {
    val builder = FlowReduxStoreBuilder<S, A>()
    block(builder)

    return this.map { ExternalWrappedAction<S, A>(it) as Action<S, A> }
        .onStart {
            emit(InitialStateAction<S, A>())
        }
        .reduxStore<Action<S, A>, S>(
            logger = logger,
            initialStateSupplier = initialStateSupplier,
            reducer = ::reducer,
            sideEffects = builder.generateSideEffects()
        )
        .distinctUntilChanged { old, new -> old === new } // distinct until not the same object reference.
}

/**
 * Provides a fluent DSL to specify a ReduxStore
 */
fun <S : Any, A : Any> Flow<A>.reduxStore(
    logger: FlowReduxLogger? = null,
    initialState: S,
    block: FlowReduxStoreBuilder<S, A>.() -> Unit
): Flow<S> =
    this.reduxStore(initialStateSupplier = { initialState }, logger = logger, block = block)

class FlowReduxStoreBuilder<S : Any, A : Any> {

    // TODO is there a better workaround to hide implementation details like this while keep inline fun()
    val builderBlocks = ArrayList<StoreWideBuilderBlock<S, A>>()

    /**
     * Define what happens if the store is in a certain state.
     */
    inline fun <reified SubState : S> inState(
        block: InStateBuilderBlock<S, SubState, A>.() -> Unit
    ) {
        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        val builder = InStateBuilderBlock<S, SubState, A>(SubState::class)
        block(builder)
        builderBlocks.add(builder)
    }

    /**
     * Define some global observer to be able to set the state directly from a flow that you observeWhileInState.
     * A common use case would be to observeWhileInState a database
     */
    // TODO not sure if we actually need an observeWhileInState or can have some kind of `setState` accessible
    //  in the block directly and folks can collect a particular flow directly
    // TODO rename observe to collect
    fun <T> observe(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.CONCAT,
        block: StoreWideObserverBlock<T, S>
    ) {
        val builder = StoreWideObserveBuilderBlock<T, S, A>(
            flow = flow,
            flatMapPolicy = flatMapPolicy,
            block = block
        )
        builderBlocks.add(builder)
    }

    internal fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> =
        builderBlocks.flatMap { builder ->
            builder.generateSideEffects()
        }
}
