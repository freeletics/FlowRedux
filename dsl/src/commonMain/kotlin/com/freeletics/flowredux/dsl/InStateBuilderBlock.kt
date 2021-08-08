package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.internal.Action
import com.freeletics.flowredux.dsl.internal.CollectInStateBasedOnStateBuilder
import com.freeletics.flowredux.dsl.internal.CollectInStateBuilder
import com.freeletics.flowredux.dsl.internal.InStateSideEffectBuilder
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
            cancelWhenStateChanges = true,
            handler = handler as OnActionHandler<InputState, S, A>
        )

        _inStateSideEffectBuilders.add(builder)
    }

    /**
     * Triggers every time an action of type [SubAction] is dispatched while the state machine is
     * in this state.
     *
     * An ongoing [handler] is NOT cancelled when leaving this state or when a new [SubAction] is
     * dispatched.
     */
    inline fun <reified SubAction : A> onActionEffect(
        noinline handler: OnActionEffectHandler<InputState, SubAction>
    ) {
        onActionEffect(FlatMapPolicy.CONCAT, handler)
    }

    /**
     * Triggers every time an action of type [SubAction] is dispatched while the state machine is
     * in this state.
     *
     * An ongoing [handler] is NOT cancelled when leaving this state. [flatMapPolicy] is used to
     * determine the behavior when a new [SubAction] is dispatched while the previous [handler]
     * execution is still ongoing.
     */
    inline fun <reified SubAction : A> onActionEffect(
        flatMapPolicy: FlatMapPolicy,
        noinline handler: OnActionEffectHandler<InputState, SubAction>
    ) {
        val builder = OnActionInStateSideEffectBuilder<InputState, S, A>(
            isInState = _isInState,
            cancelWhenStateChanges = false,
            subActionClass = SubAction::class,
            flatMapPolicy = flatMapPolicy,
            handler = { action, stateSnapshot ->
                handler(action as SubAction, stateSnapshot)
                NoStateChange
            }
        )

        _inStateSideEffectBuilders.add(builder)
    }

    /**
     * Triggers every time the state machine enters this state.
     *
     * An ongoing [handler] is cancelled when leaving this state.
     */
    fun onEnter(
        handler: OnEnterHandler<InputState, S>
    ) {
        _inStateSideEffectBuilders.add(
            OnEnterInStateSideEffectBuilder(
                isInState = _isInState,
                cancelWhenStateChanges = true,
                handler = handler
            )
        )
    }

    /**
     * Triggers every time the state machine enters this state.
     *
     * An ongoing [handler] is NOT cancelled when leaving this state.
     */
    fun onEnterEffect(
        handler: OnEnterEffectHandler<InputState>
    ) {
        _inStateSideEffectBuilders.add(
            OnEnterInStateSideEffectBuilder(
                isInState = _isInState,
                cancelWhenStateChanges = false,
                handler = {
                    handler(it)
                    NoStateChange
                }
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
        handler: CollectWhileInStateHandler<T, InputState, S>
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
        handler: CollectWhileInStateHandler<T, InputState, S>
    ) {
        _inStateSideEffectBuilders.add(
            CollectInStateBuilder(
                isInState = _isInState,
                cancelWhenStateChanges = true,
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
        handler: CollectWhileInStateHandler<T, InputState, S>
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
        handler: CollectWhileInStateHandler<T, InputState, S>
    ) {
        _inStateSideEffectBuilders.add(
            CollectInStateBasedOnStateBuilder(
                isInState = _isInState,
                cancelWhenStateChanges = true,
                flowBuilder = flowBuilder,
                flatMapPolicy = flatMapPolicy,
                handler = handler
            )
        )
    }

    /**
     * Triggers every time the state machine enters this state. The passed [flow] will be collected
     * and any emission will be passed to [handler].
     *
     * The collection is cancelled when leaving this state, while an any ongoing [handler] is NOT
     * cancelled.
     *
     * [handler] will only be called for a new emission from [flow] after a previous [handler]
     * invocation completed.
     */
    fun <T> collectWhileInStateEffect(
        flow: Flow<T>,
        handler: CollectWhileInStateEffectHandler<T, InputState>
    ) {
        collectWhileInStateEffect(flow, FlatMapPolicy.CONCAT, handler)
    }

    /**
     * Triggers every time the state machine enters this state. The passed [flow] will be collected
     * and any emission will be passed to [handler].
     *
     * The collection is cancelled when leaving this state, while an any ongoing [handler] is NOT
     * cancelled.
     *
     * [flatMapPolicy] is used to determine the behavior when a new emission from [flow] arrives
     * before the previous [handler] invocation completed.
     */
    fun <T> collectWhileInStateEffect(
        flow: Flow<T>,
        flatMapPolicy: FlatMapPolicy,
        handler: CollectWhileInStateEffectHandler<T, InputState>
    ) {
        _inStateSideEffectBuilders.add(
            CollectInStateBuilder(
                isInState = _isInState,
                cancelWhenStateChanges = false,
                flow = flow,
                flatMapPolicy = flatMapPolicy,
                handler = { value, state ->
                    handler(value, state)
                    NoStateChange
                }
            )
        )
    }

    /**
     * Triggers every time the state machine enters this state. [flowBuilder] will get a
     * [Flow] that emits the current [InputState] and any change to it. The transformed `Flow` that
     * [flowBuilder] returns will be collected and any emission will be passed to [handler].
     *
     * The collection is cancelled when leaving this state, while an any ongoing [handler] is NOT
     * cancelled.
     *
     * [handler] will only be called for a new emission from [flowBuilder]'s `Flow` after a
     * previous [handler] invocation completed.
     */
    fun <T> collectWhileInStateEffect(
        flowBuilder: (Flow<InputState>) -> Flow<T>,
        handler: CollectWhileInStateEffectHandler<T, InputState>
    ) {
        collectWhileInStateEffect(flowBuilder, FlatMapPolicy.CONCAT, handler)
    }

    /**
     * Triggers every time the state machine enters this state. [flowBuilder] will get a
     * [Flow] that emits the current [InputState] and any change to it. The transformed `Flow` that
     * [flowBuilder] returns will be collected and any emission will be passed to [handler].
     *
     * The collection is cancelled when leaving this state, while an any ongoing [handler] is NOT
     * cancelled.
     *
     * [flatMapPolicy] is used to determine the behavior when a new emission from [flowBuilder]'s
     * `Flow` arrives before the previous [handler] invocation completed.
     */
    fun <T> collectWhileInStateEffect(
        flowBuilder: (Flow<InputState>) -> Flow<T>,
        flatMapPolicy: FlatMapPolicy,
        handler: CollectWhileInStateEffectHandler<T, InputState>
    ) {
        _inStateSideEffectBuilders.add(
            CollectInStateBasedOnStateBuilder(
                isInState = _isInState,
                cancelWhenStateChanges = false,
                flowBuilder = flowBuilder,
                flatMapPolicy = flatMapPolicy,
                handler = { value, state ->
                    handler(value, state)
                    NoStateChange
                }
            )
        )
    }

    override fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> {
        return _inStateSideEffectBuilders.map { it.generateSideEffect() }
    }
}

typealias OnActionHandler<InputState, S, A> = suspend (action: A, state: InputState) -> ChangeState<S>

typealias OnActionEffectHandler<InputState, A> = suspend (action: A, state: InputState) -> Unit

typealias OnEnterHandler<InputState, S> = suspend (state: InputState) -> ChangeState<S>

typealias OnEnterEffectHandler<InputState> = suspend (state: InputState) -> Unit

typealias CollectWhileInStateHandler<T, InputState, S> = suspend (value: T, state: InputState) -> ChangeState<S>

typealias CollectWhileInStateEffectHandler<T, InputState> = suspend (value: T, state: InputState) -> Unit
