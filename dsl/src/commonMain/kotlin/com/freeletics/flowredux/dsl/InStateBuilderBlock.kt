package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.internal.Action
import com.freeletics.flowredux.dsl.internal.CollectInStateBasedOnStateBuilder
import com.freeletics.flowredux.dsl.internal.CollectInStateBuilder
import com.freeletics.flowredux.dsl.internal.InStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.OnActionInStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.OnEnterInStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.SubStateMachineSideEffectBuilder
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
     * in this state (as specified in the surrounding `in<State>` condition).
     *
     * An ongoing [handler] is cancelled when leaving this state or when a new [SubAction] is
     * dispatched.
     */
    inline fun <reified SubAction : A> on(
        handler: OnActionHandler<InputState, S, SubAction>
    ) {
        on(ExecutionPolicy.CANCEL_PREVIOUS, handler)
    }

    /**
     * Triggers every time an action of type [SubAction] is dispatched while the state machine is
     * in this state.
     *
     * An ongoing [handler] is cancelled when leaving this state. [executionPolicy] is used to
     * determine the behavior when a new [SubAction] is dispatched while the previous [handler]
     * execution is still ongoing.
     */
    inline fun <reified SubAction : A> on(
        executionPolicy: ExecutionPolicy,
        handler: OnActionHandler<InputState, S, SubAction>
    ) {
        @Suppress("UNCHECKED_CAST")
        val builder = OnActionInStateSideEffectBuilder(
            executionPolicy = executionPolicy,
            subActionClass = SubAction::class,
            isInState = _isInState,
            handler = handler as OnActionHandler<InputState, S, A>
        )

        _inStateSideEffectBuilders.add(builder)
    }

    /**
     *  An effect is a way to do some work without changing the state.
     *  A typical use case would be trigger navigation as some sort of side effect or
     *  triggering analytics.
     *  This is the "effect counterpart" to handling actions that you would do with [on].
     */
    inline fun <reified SubAction : A> onActionEffect(
        executionPolicy: ExecutionPolicy,
        handler: OnActionEffectHandler<InputState, SubAction>
    ) {
        on(executionPolicy = executionPolicy,
            handler = { action: SubAction, state: InputState ->
                handler.handle(action, state)
                NoStateChange
            }
        )
    }

    /**
     *  An effect is a way to do some work without changing the state.
     *  A typical use case is to trigger navigation as some sort of side effect or
     *  triggering analytics or do logging.
     *  This is the "effect counterpart" to handling actions that you would do with [on].
     *  Thus, cancellation and so on works the same way as [on].
     *
     *  Per default it uses [ExecutionPolicy.CANCEL_PREVIOUS].
     */
    inline fun <reified SubAction : A> onActionEffect(
        handler: OnActionEffectHandler<InputState, SubAction>
    ) {
        onActionEffect(
            executionPolicy = ExecutionPolicy.CANCEL_PREVIOUS,
            handler = handler
        )
    }

    /**
     * Triggers every time the state machine enters this state.
     * It only triggers again if the surrounding `in<State>` condition is met and will only
     * re-trigger if `in<State>` condition returned false and then true again.
     *
     * An ongoing [handler] is cancelled when leaving this state.
     */
    fun onEnter(
        handler: OnEnterHandler<InputState, S>
    ) {
        _inStateSideEffectBuilders.add(
            OnEnterInStateSideEffectBuilder(
                isInState = _isInState,
                handler = handler
            )
        )
    }

    /**
     * An effect is a way to do some work without changing the state.
     * A typical use case is to trigger navigation as some sort of side effect or
     * triggering analytics or do logging.
     *
     * This is the "effect counterpart" of [onEnter] and follows the same logic when it triggers
     * and when it gets canceled.
     */
    fun onEnterEffect(handler: OnEnterStateEffectHandler<InputState>) {
        onEnter { state ->
            handler.handle(state)
            NoStateChange
        }
    }

    /**
     * Triggers every time the state machine enters this state. The passed [flow] will be collected
     * and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [handler] will only be called for a new emission from [flow] after a previous [handler]
     * invocation completed.
     *
     * Per default [ExecutionPolicy.ORDERED] is applied.
     */
    fun <T> collectWhileInState(
        flow: Flow<T>,
        handler: CollectFlowHandler<T, InputState, S>
    ) {
        collectWhileInState(flow, ExecutionPolicy.ORDERED, handler)
    }

    /**
     * Triggers every time the state machine enters this state. The passed [flow] will be collected
     * and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [executionPolicy] is used to determine the behavior when a new emission from [flow] arrives
     * before the previous [handler] invocation completed.
     */
    fun <T> collectWhileInState(
        flow: Flow<T>,
        executionPolicy: ExecutionPolicy,
        handler: CollectFlowHandler<T, InputState, S>
    ) {
        _inStateSideEffectBuilders.add(
            CollectInStateBuilder(
                isInState = _isInState,
                flow = flow,
                executionPolicy = executionPolicy,
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
        flowBuilder: FlowBuilder<InputState, T>,
        handler: CollectFlowHandler<T, InputState, S>
    ) {
        collectWhileInState(flowBuilder, ExecutionPolicy.ORDERED, handler)
    }


    /**
     * Triggers every time the state machine enters this state. [flowBuilder] will get a
     * [Flow] that emits the current [InputState] and any change to it. The transformed `Flow` that
     * [flowBuilder] returns will be collected and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [executionPolicy] is used to determine the behavior when a new emission from [flowBuilder]'s
     * `Flow` arrives before the previous [handler] invocation completed.
     */
    fun <T> collectWhileInState(
        flowBuilder: FlowBuilder<InputState, T>,
        executionPolicy: ExecutionPolicy,
        handler: CollectFlowHandler<T, InputState, S>
    ) {
        _inStateSideEffectBuilders.add(
            CollectInStateBasedOnStateBuilder(
                isInState = _isInState,
                flowBuilder = flowBuilder,
                executionPolicy = executionPolicy,
                handler = handler
            )
        )
    }

    /**
     * An effect is a way to do some work without changing the state.
     * A typical use case is to trigger navigation as some sort of side effect or
     * triggering analytics or do logging.
     *
     * This is the "effect counterpart" of [collectWhileInState] and follows the same logic
     * when it triggers and when it gets canceled.
     *
     * Per default [ExecutionPolicy.ORDERED] is applied.
     */
    fun <T> collectWhileInStateEffect(
        flow: Flow<T>,
        handler: CollectFlowEffectHandler<T, InputState>
    ) {
        collectWhileInStateEffect(
            flow = flow,
            executionPolicy = ExecutionPolicy.ORDERED,
            handler = handler
        )
    }

    /**
     * An effect is a way to do some work without changing the state.
     * A typical use case is to trigger navigation as some sort of side effect or
     * triggering analytics or do logging.
     *
     * This is the "effect counterpart" of [collectWhileInState] and follows the same logic
     * when it triggers and when it gets canceled.
     */
    fun <T> collectWhileInStateEffect(
        flow: Flow<T>,
        executionPolicy: ExecutionPolicy,
        handler: CollectFlowEffectHandler<T, InputState>
    ) {
        collectWhileInState(
            flow = flow,
            executionPolicy = executionPolicy,
            handler = { value: T, state: InputState ->
                handler.handle(value, state)
                NoStateChange
            }
        )
    }

    /**
     * An effect is a way to do some work without changing the state.
     * A typical use case is to trigger navigation as some sort of side effect or
     * triggering analytics or do logging.
     *
     * This is the "effect counterpart" of [collectWhileInState] and follows the same logic
     * when it triggers and when it gets canceled.
     *
     * Per default [ExecutionPolicy.ORDERED] is applied.
     */
    fun <T> collectWhileInStateEffect(
        flowBuilder: FlowBuilder<InputState, T>,
        handler: CollectFlowEffectHandler<T, InputState>
    ) {
        collectWhileInStateEffect(
            flowBuilder = flowBuilder,
            executionPolicy = ExecutionPolicy.ORDERED,
            handler = handler
        )
    }

    /**
     * An effect is a way to do some work without changing the state.
     * A typical use case is to trigger navigation as some sort of side effect or
     * triggering analytics or do logging.
     *
     * This is the "effect counterpart" of [collectWhileInState] and follows the same logic
     * when it triggers and when it gets canceled.
     */
    fun <T> collectWhileInStateEffect(
        flowBuilder: FlowBuilder<InputState, T>,
        executionPolicy: ExecutionPolicy,
        handler: CollectFlowEffectHandler<T, InputState>
    ) {
        collectWhileInState(flowBuilder = flowBuilder,
            executionPolicy = executionPolicy,
            handler = { value: T, state: InputState ->
                handler.handle(value, state)
                NoStateChange
            })
    }

    fun <SubStateMachineState : S> stateMachine(
        stateMachine: FlowReduxStateMachine<SubStateMachineState, A>,
        stateMapper: (InputState, SubStateMachineState) -> ChangeState<S> = { _, substateMachineState ->
            OverrideState(
                substateMachineState
            )
        }
    ) {
        stateMachine(
            stateMachine = stateMachine,
            actionMapper = { it },
            stateMapper = stateMapper
        )
    }

    fun <SubStateMachineState : Any, SubStateMachineAction : Any> stateMachine(
        stateMachine: FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction,
        stateMapper: (InputState, SubStateMachineState) -> ChangeState<S>
    ) {
        stateMachine(
            stateMachineFactory = { stateMachine },
            actionMapper = actionMapper,
            stateMapper = stateMapper
        )
    }

    fun <SubStateMachineState : S> stateMachine(
        stateMachineFactory: (InputState) -> FlowReduxStateMachine<SubStateMachineState, A>,
    ) {

        stateMachine(
            stateMachineFactory = stateMachineFactory,
            actionMapper = { it },
            stateMapper = { _, substateMachineState ->
                OverrideState(
                    substateMachineState
                )
            }
        )
    }

    fun <SubStateMachineState : Any, SubStateMachineAction : Any> stateMachine(
        stateMachineFactory: (InputState) -> FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction,
        stateMapper: (InputState, SubStateMachineState) -> ChangeState<S>
    ) {
        _inStateSideEffectBuilders.add(
            SubStateMachineSideEffectBuilder(
                subStateMachineFactory = stateMachineFactory,
                actionMapper = actionMapper,
                stateMapper = stateMapper,
                isInState = _isInState
            )
        )
    }

    override fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> {
        return _inStateSideEffectBuilders.map { it.generateSideEffect() }
    }
}
