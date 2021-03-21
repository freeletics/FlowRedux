package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.reduxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

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
    val builderBlocks: MutableList<StoreWideBuilderBlock<S, A>> = ArrayList<StoreWideBuilderBlock<S, A>>()

    /**
     * Define what happens if the store is in a certain state.
     */
    inline fun <reified SubState : S> inState(
        noinline block: InStateBuilderBlock<SubState, S, A>.() -> Unit
    ) {

        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        val builder = InStateBuilderBlock<SubState, S, A>(_isInState = { state ->
            SubState::class.isInstance(state)
        })
        block(builder)
        builderBlocks.add(builder as StoreWideBuilderBlock<S, A>)
    }

    /**
     * Define what happens if the store is in a certain state.
     */
    fun inState(
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


internal sealed class IsInState<S> {
    internal abstract fun isInState(state: S): Boolean

    internal class InferredFromGenericsFromDSL<Substate : S, S : Any>(
        internal val substateType: KClass<Substate>
    ) : IsInState<S>() {
        override fun isInState(state: S): Boolean =
            substateType.isInstance(state)

    }

    internal class Custom<S>(internal val condition: (S) -> Boolean) : IsInState<S>() {
        override fun isInState(state: S): Boolean = condition(state)
    }
}