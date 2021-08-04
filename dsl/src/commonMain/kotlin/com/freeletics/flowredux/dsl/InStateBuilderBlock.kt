package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.internal.Action
import com.freeletics.flowredux.dsl.internal.CollectInStateBasedOnStateBuilder
import com.freeletics.flowredux.dsl.internal.CollectInStateBuilder
import com.freeletics.flowredux.dsl.internal.CollectStateInStateBuilder
import com.freeletics.flowredux.dsl.internal.InStateOnEnterHandler
import com.freeletics.flowredux.dsl.internal.InStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.OnActionHandler
import com.freeletics.flowredux.dsl.internal.OnActionInStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.OnEnterInStateSideEffectBuilder
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

    /**
     * Triggers every time an action of type [SubAction] is dispatched while the state machine is
     * in this state.
     *
     * An ongoing [handler] is cancelled when leaving this state or when a new [SubAction] is
     * dispatched.
     */
    inline fun <reified SubAction : A> on(
        noinline handler: OnActionHandler<InputState, S, SubAction>
    ) {
        on(FlatMapPolicy.LATEST, handler)
    }

    /**
     * Triggers every time an action of type [SubAction] is dispatched while the state machine is
     * in this state.
     *
     * An ongoing [handler] is cancelled when leaving this state. [flatMapPolicy] is used to
     * determine the behavior when a new [SubAction] is dispatched while the previous [handler]
     * execution is still ongoing.
     */
    inline fun <reified SubAction : A> on(
        flatMapPolicy: FlatMapPolicy,
        noinline handler: OnActionHandler<InputState, S, SubAction>
    ) {
        @Suppress("UNCHECKED_CAST")
        val builder = OnActionInStateSideEffectBuilder<InputState, S, A>(
            flatMapPolicy = flatMapPolicy,
            subActionClass = SubAction::class,
            isInState = _isInState,
            handler = handler as OnActionHandler<InputState, S, A>
        )

        _inStateSideEffectBuilders.add(builder)
    }

    /**
     * Triggers every time the state machine enters this state.
     *
     * An ongoing [handler] is cancelled when leaving this state.
     */
    fun onEnter(
        handler: InStateOnEnterHandler<InputState, S>
    ) {
        _inStateSideEffectBuilders.add(
            OnEnterInStateSideEffectBuilder(
                isInState = _isInState,
                handler = handler
            )
        )
    }

    /**
     * Triggers every time the state machine enters this state. The passed [flow] will be collected
     * and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [handler] will only be called for a new emission from [flow] after a previous [handler]
     * invocation completed.
     */
    fun <T> collectWhileInState(
        flow: Flow<T>,
        handler: InStateObserverHandler<T, InputState, S>
    ) {
        collectWhileInState(flow, FlatMapPolicy.CONCAT, handler)
    }

    /**
     * Triggers every time the state machine enters this state. The passed [flow] will be collected
     * and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [flatMapPolicy] is used to determine the behavior when a new emission from [flow] arrives
     * before the previous [handler] invocation completed.
     */
    fun <T> collectWhileInState(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy,
        handler: InStateObserverHandler<T, InputState, S>
    ) {
        _inStateSideEffectBuilders.add(
            CollectInStateBuilder(
                isInState = _isInState,
                flow = flow,
                flatMapPolicy = flatMapPolicy,
                handler = handler
            )
        )
    }

    /**
     * Triggers every time the state machine enters this state. [flowBuilder] will get a
     * [Flow] that emits the current [InputState] and any change to it. The transformed `Flow` that
     * [flowBuilder] returns will be collected and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [handler] will only be called for a new emission from [flowBuilder]'s `Flow` after a
     * previous [handler] invocation completed.
     */
    fun <T> collectWhileInState(
        flowBuilder: (Flow<InputState>) -> Flow<T>,
        handler: InStateObserverHandler<T, InputState, S>
    ) {
        collectWhileInState(flowBuilder, FlatMapPolicy.CONCAT, handler)
    }

    /**
     * Triggers every time the state machine enters this state. [flowBuilder] will get a
     * [Flow] that emits the current [InputState] and any change to it. The transformed `Flow` that
     * [flowBuilder] returns will be collected and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [flatMapPolicy] is used to determine the behavior when a new emission from [flowBuilder]'s
     * `Flow` arrives before the previous [handler] invocation completed.
     */
    fun <T> collectWhileInState(
        flowBuilder: (Flow<InputState>) -> Flow<T>,
        flatMapPolicy: FlatMapPolicy,
        handler: InStateObserverHandler<T, InputState, S>
    ) {
        _inStateSideEffectBuilders.add(
            CollectInStateBasedOnStateBuilder(
                isInState = _isInState,
                flowBuilder = flowBuilder,
                flatMapPolicy = flatMapPolicy,
                handler = handler
            )
        )
    }

    override fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> {
        return _inStateSideEffectBuilders.map { it.generateSideEffect() }
    }
}

typealias InStateObserverHandler<T, InputState, S> = suspend (value: T, state: InputState) -> ChangeState<S>
