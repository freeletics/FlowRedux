package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class FlowReduxStoreBuilder<S : Any, A : Any> internal constructor() {

    private val _sideEffectBuilders: MutableList<SideEffectBuilder<out S, S, A>> = ArrayList()
    internal val sideEffectBuilders: List<SideEffectBuilder<out S, S, A>> get() = _sideEffectBuilders

    /**
     * Define what happens if the store is in a certain state.
     * "In a certain state" condition is true if state is instance of the type specified as generic function parameter.
     */
    public inline fun <reified SubState : S> inState(
        noinline block: InStateBuilderBlock<SubState, S, A>.() -> Unit,
    ) {
        inState(SubState::class, block)
    }

    @PublishedApi
    internal fun <SubState : S> inState(
        subStateClass: KClass<SubState>,
        block: InStateBuilderBlock<SubState, S, A>.() -> Unit,
    ) {
        _sideEffectBuilders += InStateBuilderBlock<SubState, S, A>(
            isInState = { state -> subStateClass.isInstance(state) },
        ).apply(block).sideEffectBuilders
    }

    /**
     * This variation allows you to specify is a mix between inferring the condition of the generic function type
     * and additionally can specify and ADDITIONAL condition that also must be true in addition to the check that
     * the type as specified as generic fun parameter is an instance of the current state.
     */
    public inline fun <reified SubState : S> inState(
        noinline additionalIsInState: (SubState) -> Boolean,
        noinline block: InStateBuilderBlock<SubState, S, A>.() -> Unit,
    ) {
        inState(SubState::class, additionalIsInState, block)
    }

    @PublishedApi
    internal fun <SubState : S> inState(
        subStateClass: KClass<SubState>,
        additionalIsInState: (SubState) -> Boolean,
        block: InStateBuilderBlock<SubState, S, A>.() -> Unit,
    ) {
        @Suppress("UNCHECKED_CAST")
        _sideEffectBuilders += InStateBuilderBlock<SubState, S, A>(
            isInState = { state -> subStateClass.isInstance(state) && additionalIsInState(state as SubState) },
        ).apply(block).sideEffectBuilders
    }

    /**
     * Define what happens if the store is in a certain state.
     * @param isInState The condition under which we identify that the state machine is in a given "state".
     */
    public fun inStateWithCondition(
        isInState: (S) -> Boolean,
        block: InStateBuilderBlock<S, S, A>.() -> Unit,
    ) {
        _sideEffectBuilders += InStateBuilderBlock<S, S, A>(
            isInState = isInState,
        ).apply(block).sideEffectBuilders
    }
}
