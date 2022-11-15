package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.dsl.internal.Action
import com.freeletics.flowredux.dsl.internal.CollectInStateBasedOnStateBuilder
import com.freeletics.flowredux.dsl.internal.CollectInStateBuilder
import com.freeletics.flowredux.dsl.internal.InStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.OnActionInStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.OnEnterInStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.StartStateMachineOnActionInStateSideEffectBuilder
import com.freeletics.flowredux.dsl.internal.StartStatemachineOnEnterSideEffectBuilder
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@FlowPreview
@ExperimentalCoroutinesApi
@FlowReduxDsl
public class InStateBuilderBlock<InputState : S, S : Any, A : Any>(
    private val _isInState: (S) -> Boolean,
) {

    private val _inStateSideEffectBuilders = ArrayList<InStateSideEffectBuilder<InputState, S, A>>()

    internal fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> {
        return _inStateSideEffectBuilders.map { it.generateSideEffect() }
    }

    /**
     * Triggers every time an action of type [SubAction] is dispatched while the state machine is
     * in this state.
     *
     * An ongoing [handler] is cancelled when leaving this state. [executionPolicy] is used to
     * determine the behavior when a new [SubAction] is dispatched while the previous [handler]
     * execution is still ongoing. By default an ongoing [handler] is cancelled when leaving this
     * state or when a new [SubAction] is dispatched.
     */
    public inline fun <reified SubAction : A> on(
        executionPolicy: ExecutionPolicy = ExecutionPolicy.CANCEL_PREVIOUS,
        noinline handler: suspend (action: SubAction, state: State<InputState>) -> ChangedState<S>,
    ) {
        on(SubAction::class, executionPolicy, handler)
    }

    /**
     * Triggers every time an action of type [SubAction] is dispatched while the state machine is
     * in this state.
     *
     * An ongoing [handler] is cancelled when leaving this state. [executionPolicy] is used to
     * determine the behavior when a new [SubAction] is dispatched while the previous [handler]
     * execution is still ongoing.
     */
    public fun <SubAction : A> on(
        actionClass: KClass<SubAction>,
        executionPolicy: ExecutionPolicy,
        handler: suspend (action: SubAction, state: State<InputState>) -> ChangedState<S>,
    ) {
        val builder = OnActionInStateSideEffectBuilder<InputState, S, A>(
            executionPolicy = executionPolicy,
            subActionClass = actionClass,
            isInState = _isInState,
            handler = { action, state ->
                @Suppress("UNCHECKED_CAST")
                handler(action as SubAction, state)
            }
        )

        _inStateSideEffectBuilders.add(builder)
    }

    /**
     *  An effect is a way to do some work without changing the state.
     *  A typical use case would be trigger navigation as some sort of side effect or
     *  triggering analytics.
     *  This is the "effect counterpart" to handling actions that you would do with [on].
     *
     * An ongoing [handler] is cancelled when leaving this state. [executionPolicy] is used to
     * determine the behavior when a new [SubAction] is dispatched while the previous [handler]
     * execution is still ongoing. By default an ongoing [handler] is cancelled when leaving this
     * state or when a new [SubAction] is dispatched.
     */
    public inline fun <reified SubAction : A> onActionEffect(
        executionPolicy: ExecutionPolicy = ExecutionPolicy.CANCEL_PREVIOUS,
        noinline handler: suspend (action: SubAction, stateSnapshot: InputState) -> Unit,
    ) {
        onActionEffect(SubAction::class, executionPolicy, handler)
    }

    /**
     *  An effect is a way to do some work without changing the state.
     *  A typical use case would be trigger navigation as some sort of side effect or
     *  triggering analytics.
     *  This is the "effect counterpart" to handling actions that you would do with [on].
     */
    public fun <SubAction : A> onActionEffect(
        actionClass: KClass<SubAction>,
        executionPolicy: ExecutionPolicy,
        handler: suspend (action: SubAction, stateSnapshot: InputState) -> Unit,
    ) {
        on(
            actionClass = actionClass,
            executionPolicy = executionPolicy,
            handler = { action: SubAction, state: State<InputState> ->
                handler(action, state.snapshot)
                NoStateChange
            }
        )
    }

    /**
     * Triggers every time the state machine enters this state.
     * It only triggers again if the surrounding `in<State>` condition is met and will only
     * re-trigger if `in<State>` condition returned false and then true again.
     *
     * An ongoing [handler] is cancelled when leaving this state.
     */
    public fun onEnter(
        handler: suspend (state: State<InputState>) -> ChangedState<S>,
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
    public fun onEnterEffect(
        handler: suspend (stateSnapshot: InputState) -> Unit,
    ) {
        onEnter { state ->
            handler(state.snapshot)
            NoStateChange
        }
    }

    /**
     * Triggers every time the state machine enters this state. The passed [flow] will be collected
     * and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [executionPolicy] is used to determine the behavior when a new emission from [flow] arrives
     * before the previous [handler] invocation completed. By default [ExecutionPolicy.ORDERED]
     * is applied.
     */
    public fun <T> collectWhileInState(
        flow: Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.ORDERED,
        handler: suspend (item: T, state: State<InputState>) -> ChangedState<S>,
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
     * [executionPolicy] is used to determine the behavior when a new emission from [flowBuilder]'s
     * `Flow` arrives before the previous [handler] invocation completed. By default
     * [ExecutionPolicy.ORDERED] is applied.
     */
    public fun <T> collectWhileInState(
        flowBuilder: (state: Flow<InputState>) -> Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.ORDERED,
        handler: suspend (item: T, state: State<InputState>) -> ChangedState<S>,
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
     */
    public fun <T> collectWhileInStateEffect(
        flow: Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.ORDERED,
        handler: suspend (item: T, state: InputState) -> Unit,
    ) {
        collectWhileInState(
            flow = flow,
            executionPolicy = executionPolicy,
            handler = { value: T, state: State<InputState> ->
                handler(value, state.snapshot)
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
     */
    public fun <T> collectWhileInStateEffect(
        flowBuilder: (state: Flow<InputState>) -> Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.ORDERED,
        handler: suspend (item: T, state: InputState) -> Unit,
    ) {
        collectWhileInState(flowBuilder = flowBuilder,
            executionPolicy = executionPolicy,
            handler = { value: T, state: State<InputState> ->
                handler(value, state.snapshot)
                NoStateChange
            })
    }

    public fun <SubStateMachineState : Any> onEnterStartStateMachine(
        stateMachine: FlowReduxStateMachine<SubStateMachineState, A>,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S> = { _, subState ->
            @Suppress("UNCHECKED_CAST")
            OverrideState(subState as S)
        }
    ) {
        onEnterStartStateMachine(
            stateMachineFactory = { stateMachine },
            actionMapper = { it },
            stateMapper = stateMapper,
        )
    }

    public fun <SubStateMachineState : Any> onEnterStartStateMachine(
        stateMachineFactory: (InputState) -> FlowReduxStateMachine<SubStateMachineState, A>,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S> = { _, subState ->
            @Suppress("UNCHECKED_CAST")
            OverrideState(subState as S)
        }
    ) {
        onEnterStartStateMachine(
            stateMachineFactory = stateMachineFactory,
            actionMapper = { it },
            stateMapper = stateMapper,
        )
    }

    public fun <SubStateMachineState : Any, SubStateMachineAction : Any> onEnterStartStateMachine(
        stateMachine: FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction?,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S> = { _, subState ->
            @Suppress("UNCHECKED_CAST")
            OverrideState(subState as S)
        }
    ) {
        onEnterStartStateMachine(
            stateMachineFactory = { stateMachine },
            actionMapper = actionMapper,
            stateMapper = stateMapper,
        )
    }

    public fun <SubStateMachineState : Any, SubStateMachineAction : Any> onEnterStartStateMachine(
        stateMachineFactory: (InputState) -> FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction?,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S> = { _, subState ->
            @Suppress("UNCHECKED_CAST")
            OverrideState(subState as S)
        }
    ) {
        _inStateSideEffectBuilders.add(
            StartStatemachineOnEnterSideEffectBuilder(
                subStateMachineFactory = stateMachineFactory,
                actionMapper = actionMapper,
                stateMapper = stateMapper,
                isInState = _isInState
            )
        )
    }

    public inline fun <reified SubAction : A, SubStateMachineState : Any> onActionStartStateMachine(
        stateMachine: FlowReduxStateMachine<SubStateMachineState, A>,
        noinline stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    ) {
        onActionStartStateMachine(
            stateMachineFactory = { _: SubAction, _: InputState -> stateMachine },
            actionMapper = { it },
            stateMapper = stateMapper
        )
    }

    public inline fun <reified SubAction : A, SubStateMachineState : Any> onActionStartStateMachine(
        noinline stateMachineFactory: (SubAction, InputState) -> FlowReduxStateMachine<SubStateMachineState, A>,
        noinline stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    ) {
        onActionStartStateMachine(
            stateMachineFactory = stateMachineFactory,
            actionMapper = { it },
            stateMapper = stateMapper
        )
    }

    public inline fun <reified SubAction : A, SubStateMachineState : Any, SubStateMachineAction : Any> onActionStartStateMachine(
        noinline stateMachineFactory: (SubAction, InputState) -> FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
        noinline actionMapper: (A) -> SubStateMachineAction?,
        noinline stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    ) {
        onActionStartStateMachine(
            actionClass = SubAction::class,
            stateMachineFactory = stateMachineFactory,
            actionMapper = actionMapper,
            stateMapper = stateMapper
        )
    }

    public fun <SubAction : A, SubStateMachineState : Any, SubStateMachineAction : Any> onActionStartStateMachine(
        actionClass: KClass<out SubAction>,
        stateMachineFactory: (SubAction, InputState) -> FlowReduxStateMachine<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction?,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    ) {
        val builder = StartStateMachineOnActionInStateSideEffectBuilder<SubStateMachineState, SubStateMachineAction, InputState, SubAction, S, A>(
            subStateMachineFactory = stateMachineFactory,
            actionMapper = actionMapper,
            stateMapper = stateMapper,
            isInState = _isInState,
            subActionClass = actionClass,
        )

        _inStateSideEffectBuilders.add(builder)
    }
}
