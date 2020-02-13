package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import kotlinx.coroutines.flow.Flow

// TODO @DslMarker
class InStateBuilderBlock<S : Any, A : Any>(
    /**
     * For private usage only
     */
    val _isInState: (S) -> Boolean
) : StoreWideBuilderBlock<S, A>() {

    val _inStateSideEffectBuilders = ArrayList<InStateSideEffectBuilder<S, A>>()

    inline fun <reified SubAction : A> on(
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.LATEST,
        noinline block: OnActionBlock<S, SubAction>
    ) {

        @Suppress("UNCHECKED_CAST")
        val builder = OnActionInStateSideEffectBuilder<S, A>(
            flatMapPolicy = flatMapPolicy,
            subActionClass = SubAction::class,
            isInState = _isInState,
            onActionBlock = block as OnActionBlock<S, A>
        )

        _inStateSideEffectBuilders.add(builder)
    }

    fun <T> collectWhileInState(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.CONCAT,
        block: InStateObserverBlock<T, S>
    ) {
        _inStateSideEffectBuilders.add(
            Working_CollectInStateBuilder(
                isInState = _isInState,
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
                isInState = _isInState,
                flatMapPolicy = flatMapPolicy,
                block = block
            )
        )
    }

    override fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> {
        return _inStateSideEffectBuilders.map { it.generateSideEffect() }
    }
}
