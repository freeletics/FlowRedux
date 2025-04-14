package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@FlowReduxDsl
public class FlowReduxStoreBuilder2<S : Any, A : Any> internal constructor() {
    private val sideEffectBuilderList: MutableList<SideEffectBuilder<out S, S, A>> = ArrayList()
    internal val sideEffectBuilders: List<SideEffectBuilder<out S, S, A>> get() = sideEffectBuilderList

    /**
     * Define what happens if the store is in a certain state.
     * "In a certain state" condition is true if state is instance of the type specified as generic function parameter.
     */
    public inline fun <reified SubState : S> inState(
        noinline block: InStateBuilderBlock2<SubState, S, A>.() -> Unit,
    ) {
        inState(SubState::class, block)
    }

    @PublishedApi
    internal fun <SubState : S> inState(
        subStateClass: KClass<SubState>,
        block: InStateBuilderBlock2<SubState, S, A>.() -> Unit,
    ) {
        sideEffectBuilderList += InStateBuilderBlock2<SubState, S, A>(
            isInState = { state -> subStateClass.isInstance(state) },
        ).apply(block).sideEffectBuilders
    }
}
