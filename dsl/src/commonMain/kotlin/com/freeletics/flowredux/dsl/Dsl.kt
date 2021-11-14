package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.FlowReduxLogger
import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.internal.Action
import com.freeletics.flowredux.dsl.internal.ExternalWrappedAction
import com.freeletics.flowredux.dsl.internal.InitialStateAction
import com.freeletics.flowredux.dsl.internal.reducer
import com.freeletics.flowredux.reduxStore
import kotlin.jvm.JvmSynthetic
import kotlin.reflect.KClass
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
@FlowPreview
@ExperimentalCoroutinesApi
public fun <S : Any, A : Any> Flow<A>.reduxStore(
    logger: FlowReduxLogger? = null,
    initialState: S,
    block: FlowReduxStoreBuilder<S, A>.() -> Unit
): Flow<S> =
    this.reduxStore(initialStateSupplier = { initialState }, logger = logger, block = block)

@FlowPreview
@ExperimentalCoroutinesApi
public class FlowReduxStoreBuilder<S : Any, A : Any> {

    private val builderBlocks: MutableList<StoreWideBuilderBlock<S, A>> = ArrayList()

    /**
     * Define what happens if the store is in a certain state.
     * "In a certain state" condition is true if state is instance of the type specified as generic function parameter.
     */
    public inline fun <reified SubState : S> inState(
        noinline block: InStateBuilderBlock<SubState, S, A>.() -> Unit
    ) {
        inState(SubState::class, block)
    }

    /**
     * Define what happens if the store is in a certain state.
     * "In a certain state" condition is true if state is instance of the type specified as generic function parameter.
     */
    @JvmSynthetic
    public fun <SubState : S> inState(
        subStateClass: KClass<SubState>,
        block: InStateBuilderBlock<SubState, S, A>.() -> Unit
    ) {

        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        //  or is this actaully a feature :)
        val builder = InStateBuilderBlock<SubState, S, A>(_isInState = { state ->
            subStateClass.isInstance(state)
        })
        block(builder)
        builderBlocks.add(builder as StoreWideBuilderBlock<S, A>)
    }

    /**
     * This variation allows you to specify is a mix between inferring the condition of the generic function type
     * and additionally can specify and ADDITIONAL condition that also must be true in addition to the check that
     * the type as specified as generic fun parameter is an instance of the current state.
     */
    public inline fun <reified SubState : S> inState(
        noinline additionalIsInState: (SubState) -> Boolean,
        noinline block: InStateBuilderBlock<SubState, S, A>.() -> Unit
    ) {
        inState(SubState::class, additionalIsInState, block)
    }

    /**
     * This variation allows you to specify is a mix between inferring the condition of the generic function type
     * and additionally can specify and ADDITIONAL condition that also must be true in addition to the check that
     * the type as specified as generic fun parameter is an instance of the current state.
     */
    public fun <SubState : S> inState(
        subStateClass: KClass<SubState>,
        additionalIsInState: (SubState) -> Boolean,
        block: InStateBuilderBlock<SubState, S, A>.() -> Unit
    ) {

        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        //  or is this actaully a feature :)
        val builder = InStateBuilderBlock<SubState, S, A>(_isInState = { state ->
            @Suppress("UNCHECKED_CAST")
            subStateClass.isInstance(state) && additionalIsInState (state as SubState)
        })
        block(builder)
        builderBlocks.add(builder as StoreWideBuilderBlock<S, A>)
    }

    /**
     * Define what happens if the store is in a certain state.
     * @param isInState The condition under which we identify that the state machine is in a given "state".
     */
    public fun inStateWithCondition(
        isInState: (S) -> Boolean,
        block: InStateBuilderBlock<S, S, A>.() -> Unit
    ) {
        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        val builder = InStateBuilderBlock<S, S, A>(_isInState = isInState)
        block(builder)
        builderBlocks.add(builder)
    }

    internal fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> =
        builderBlocks.flatMap { builder ->
            builder.generateSideEffects()
        }
}
