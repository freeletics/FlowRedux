package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.reduxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Provides a fluent DSL to specify a ReduxStore
 */
@ExperimentalCoroutinesApi
fun <S : Any, A : Any> Flow<A>.reduxStore(
    logger: FlowReduxLogger? = null,
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
@ExperimentalCoroutinesApi
fun <S : Any, A : Any> Flow<A>.reduxStore(
    logger: FlowReduxLogger? = null,
    initialState: S,
    block: FlowReduxStoreBuilder<S, A>.() -> Unit
): Flow<S> =
    this.reduxStore(initialStateSupplier = { initialState }, logger = logger, block = block)

class FlowReduxStoreBuilder<S : Any, A : Any> {

    // TODO is there a better workaround to hide implementation details like this while keep inline fun()
    val builderBlocks: MutableList<StoreWideBuilderBlock<S, A>> = ArrayList<StoreWideBuilderBlock<S, A>>()

    /**
     * Define what happens if the store is in a certain state.
     * "In a certain state" condition is true if state is instance of the type specified as generic function parameter.
     */
    inline fun <reified SubState : S> inState(
        noinline block: InStateBuilderBlock<SubState, S, A>.() -> Unit
    ) {

        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        //  or is this actaully a feature :)
        val builder = InStateBuilderBlock<SubState, S, A>(_isInState = { state ->
            SubState::class.isInstance(state)
        })
        block(builder)
        builderBlocks.add(builder as StoreWideBuilderBlock<S, A>)
    }

    /**
     * This variation allows you to specify is a mix between inferring the condition of the generic function type
     * and additionally can specify and ADDITIONAL condition that also must be true in addition to the check that
     * the type as specified as generic fun parameter is an instance of the current state.
     */
    inline fun <reified SubState : S> inState(
        noinline additionalIsInState: (SubState) -> Boolean,
        noinline block: InStateBuilderBlock<SubState, S, A>.() -> Unit
    ) {

        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        //  or is this actaully a feature :)
        val builder = InStateBuilderBlock<SubState, S, A>(_isInState = { state ->
            SubState::class.isInstance(state) && additionalIsInState (state as SubState)
        })
        block(builder)
        builderBlocks.add(builder as StoreWideBuilderBlock<S, A>)
    }

    /**
     * Define what happens if the store is in a certain state.
     * @param isInState The condition under which we identify that the state machine is in a given "state".
     */
    fun inStateWithCondition(
        isInState: (S) -> Boolean,
        block: InStateBuilderBlock<S, S, A>.() -> Unit
    ) {
        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        val builder = InStateBuilderBlock<S, S, A>(_isInState = isInState)
        block(builder)
        builderBlocks.add(builder)
    }

    /**
     * Define some global observer to be able to set the state directly from a flow that you observe.
     * A common use case would be to observe a database
     */
    // TODO not sure if we actually need an observe or can have some kind of `setState` accessible
    //  in the block directly and folks can collect a particular flow directly
    fun <T> collectWhileInAnyState(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.CONCAT, // TODO should be latest?
        block: StoreWideCollectorBlock<T, S>
    ) {
        val builder = StoreWideCollectBuilderBlock<T, S, A>(
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
