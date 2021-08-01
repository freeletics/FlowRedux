package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.internal.Action
import com.freeletics.flowredux.dsl.internal.InStateOnEnterBlock
import com.freeletics.flowredux.dsl.internal.InStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.OnActionBlock
import com.freeletics.flowredux.dsl.internal.OnActionInStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.OnEnterInStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.Working_CollectInStateBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

// TODO @DslMarker
@FlowPreview
@ExperimentalCoroutinesApi
class InStateBuilderBlock<InputState : S, S : Any, A : Any>(
    /**
     * For private usage only
     */
    val _isInState: (S) -> Boolean
) : StoreWideBuilderBlock<S, A>() {

    val _inStateSideEffectBuilders = ArrayList<InStateSideEffectBuilder<InputState, S, A>>()

    inline fun <reified SubAction : A> on(
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.LATEST,
        noinline block: OnActionBlock<InputState, S, SubAction>
    ) {

        @Suppress("UNCHECKED_CAST")
        val builder = OnActionInStateSideEffectBuilder<InputState, S, A>(
            flatMapPolicy = flatMapPolicy,
            subActionClass = SubAction::class,
            isInState = _isInState,
            onActionBlock = block as OnActionBlock<InputState, S, A>
        )

        _inStateSideEffectBuilders.add(builder)
    }

    fun <T> collectWhileInState(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy = FlatMapPolicy.CONCAT,
        block: InStateObserverBlock<T, InputState, S>
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
        block: InStateOnEnterBlock<InputState, S>
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

typealias InStateObserverBlock<T, InputState, S> = suspend (value: T, state: InputState) -> ChangeState<S>
