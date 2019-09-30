package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import com.freeletics.flowredux.reduxStore
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.full.cast
import kotlin.reflect.full.isSubclassOf

/**
 * Provides a fluent DSL to specify a ReduxStore
 */
fun <S : Any, A : Any> Flow<A>.reduxStoreDsl(
    initialState: S, block: FlowReduxStoreBuilder<S, A>.() -> Unit
): Flow<S> {
    val builder = FlowReduxStoreBuilder<S, A>()
    block(builder)

    return this.map { ExternalWrappedAction<S, A>(it) }
        .reduxStore<Action<S, A>, S>(
            initialStateSupplier = { initialState },
            reducer = ::reducer,
            sideEffects = builder.generateSideEffects()
        )

    // TODO we may neeed a loop back to propagate internally state changes.
    //  See TODO in Action.kt
}

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
     * Define some global observer to be able to set the state directly from a flow that you observe.
     * A common use case would be to observe a database
     */
    // TODO not sure if we actually need an observe or can have some kind of `setState` accessible
    //  in the block directly and folks can collect a particular flow directly
    fun <T> observe(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.LATEST,
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
        }.also {
            println("Generated ${it.size} sideeffects")
        }
}
