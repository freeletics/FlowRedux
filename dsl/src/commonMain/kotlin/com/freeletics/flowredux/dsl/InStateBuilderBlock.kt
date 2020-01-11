package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

// TODO @DslMarker

class InStateBuilderBlock<S : Any, SubState : S, A : Any>(
    val _subStateClass: KClass<SubState>
) : StoreWideBuilderBlock<S, A>() {

    val _inStateSideEffectBuilders = ArrayList<InStateSideEffectBuilder<S, A>>()

    inline fun <reified SubAction : A> on(
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.LATEST,
        noinline block: OnActionBlock<S, SubAction>
    ) {

        @Suppress("UNCHECKED_CAST")
        val builder = OnActionInStateSideEffectBuilder<S, A, SubState>(
            flatMapPolicy = flatMapPolicy,
            subActionClass = SubAction::class,
            subStateClass = _subStateClass,
            onActionBlock = block as OnActionBlock<S, A>
        )

        _inStateSideEffectBuilders.add(builder)
    }

    fun <T> observeWhileInState(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.CONCAT,
        block: InStateObserverBlock<T, S>
    ) {
        _inStateSideEffectBuilders.add(
            ObserveInStateSideEffectBuilder(
                subStateClass = _subStateClass,
                flow = flow,
                flatMapPolicy = flatMapPolicy,
                block = block
            )
        )
    }

    /**
     * Triggers every time the state machine enters this state.
     *
     * This does not cancel any ongoing block when the state changes.
     *
     * TODO add a sample
     */
    fun onEnter(
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.LATEST,
        block: InStateOnEnterBlock<S>
    ) {
        _inStateSideEffectBuilders.add(
            OnEnterInStateSideEffectBuilder(
                subStateClass = _subStateClass,
                flatMapPolicy = flatMapPolicy,
                block = block
            )
        )
    }

    override fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> {
        return _inStateSideEffectBuilders.map { it.generateSideEffect() }
    }
}
