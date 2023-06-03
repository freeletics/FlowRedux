package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.sideeffects.CollectWhile
import com.freeletics.flowredux.sideeffects.CollectWhileBasedOnState
import com.freeletics.flowredux.sideeffects.OnAction
import com.freeletics.flowredux.sideeffects.OnActionStartStateMachine
import com.freeletics.flowredux.sideeffects.OnEnter
import com.freeletics.flowredux.sideeffects.OnEnterStartStateMachine
import com.freeletics.flowredux.sideeffects.SideEffect
import com.freeletics.flowredux.sideeffects.SideEffectBuilder
import com.freeletics.mad.statemachine.StateMachine
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
@FlowReduxDsl
public abstract class BaseBuilderBlock<InputState : S, S : Any, A : Any> internal constructor() {

    internal abstract val isInState: SideEffectBuilder.IsInState<S>
    internal open fun sideEffectIsInState() = SideEffect.IsInState<S> {
        isInState.check(it)
    }

    internal val sideEffectBuilders = ArrayList<SideEffectBuilder<InputState, S, A>>()

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

    @PublishedApi
    internal fun <SubAction : A> on(
        actionClass: KClass<SubAction>,
        executionPolicy: ExecutionPolicy,
        handler: suspend (action: SubAction, state: State<InputState>) -> ChangedState<S>,
    ) {
        sideEffectBuilders += SideEffectBuilder(isInState) {
            OnAction(
                isInState = sideEffectIsInState(),
                subActionClass = actionClass,
                executionPolicy = executionPolicy,
                handler = handler,
            )
        }
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

    @PublishedApi
    internal fun <SubAction : A> onActionEffect(
        actionClass: KClass<SubAction>,
        executionPolicy: ExecutionPolicy,
        handler: suspend (action: SubAction, stateSnapshot: InputState) -> Unit,
    ) {
        on(
            actionClass = actionClass,
            executionPolicy = executionPolicy,
            handler = { action, state ->
                handler(action, state.snapshot)
                NoStateChange
            },
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
        sideEffectBuilders += SideEffectBuilder(isInState) { initialState ->
            OnEnter(
                isInState = sideEffectIsInState(),
                initialState = initialState,
                handler = handler,
            )
        }
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
        sideEffectBuilders += SideEffectBuilder(isInState) {
            CollectWhile(
                isInState = sideEffectIsInState(),
                flow = flow,
                executionPolicy = executionPolicy,
                handler = handler,
            )
        }
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
        sideEffectBuilders += SideEffectBuilder(isInState) {
            CollectWhileBasedOnState(
                isInState = sideEffectIsInState(),
                flowBuilder = flowBuilder,
                executionPolicy = executionPolicy,
                handler = handler,
            )
        }
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
            },
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
        collectWhileInState(
            flowBuilder = flowBuilder,
            executionPolicy = executionPolicy,
            handler = { value: T, state: State<InputState> ->
                handler(value, state.snapshot)
                NoStateChange
            },
        )
    }

    public fun <SubStateMachineState : Any> onEnterStartStateMachine(
        stateMachine: StateMachine<SubStateMachineState, A>,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S> = { _, subState ->
            @Suppress("UNCHECKED_CAST")
            OverrideState(subState as S)
        },
    ) {
        onEnterStartStateMachine(
            stateMachineFactory = { stateMachine },
            actionMapper = { it },
            stateMapper = stateMapper,
        )
    }

    public fun <SubStateMachineState : Any> onEnterStartStateMachine(
        stateMachineFactory: (InputState) -> StateMachine<SubStateMachineState, A>,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S> = { _, subState ->
            @Suppress("UNCHECKED_CAST")
            OverrideState(subState as S)
        },
    ) {
        onEnterStartStateMachine(
            stateMachineFactory = stateMachineFactory,
            actionMapper = { it },
            stateMapper = stateMapper,
        )
    }

    public fun <SubStateMachineState : Any, SubStateMachineAction : Any> onEnterStartStateMachine(
        stateMachine: StateMachine<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction?,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S> = { _, subState ->
            @Suppress("UNCHECKED_CAST")
            OverrideState(subState as S)
        },
    ) {
        onEnterStartStateMachine(
            stateMachineFactory = { stateMachine },
            actionMapper = actionMapper,
            stateMapper = stateMapper,
        )
    }

    public fun <SubStateMachineState : Any, SubStateMachineAction : Any> onEnterStartStateMachine(
        stateMachineFactory: (InputState) -> StateMachine<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction?,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S> = { _, subState ->
            @Suppress("UNCHECKED_CAST")
            OverrideState(subState as S)
        },
    ) {
        sideEffectBuilders += SideEffectBuilder(isInState) { initialState ->
            OnEnterStartStateMachine(
                isInState = sideEffectIsInState(),
                subStateMachine = stateMachineFactory(initialState),
                actionMapper = actionMapper,
                stateMapper = stateMapper,
            )
        }
    }

    public inline fun <reified SubAction : A, SubStateMachineState : Any> onActionStartStateMachine(
        stateMachine: StateMachine<SubStateMachineState, A>,
        noinline stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    ) {
        onActionStartStateMachine(
            stateMachineFactory = { _: SubAction, _: InputState -> stateMachine },
            actionMapper = { it },
            stateMapper = stateMapper,
        )
    }

    public inline fun <reified SubAction : A, SubStateMachineState : Any> onActionStartStateMachine(
        noinline stateMachineFactory: (SubAction, InputState) -> StateMachine<SubStateMachineState, A>,
        noinline stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    ) {
        onActionStartStateMachine(
            stateMachineFactory = stateMachineFactory,
            actionMapper = { it },
            stateMapper = stateMapper,
        )
    }

    public inline fun <reified SubAction : A, SubStateMachineState : Any, SubStateMachineAction : Any> onActionStartStateMachine(
        noinline stateMachineFactory: (SubAction, InputState) -> StateMachine<SubStateMachineState, SubStateMachineAction>,
        noinline actionMapper: (A) -> SubStateMachineAction?,
        noinline stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    ) {
        onActionStartStateMachine(
            actionClass = SubAction::class,
            stateMachineFactory = stateMachineFactory,
            actionMapper = actionMapper,
            stateMapper = stateMapper,
        )
    }

    @PublishedApi
    internal fun <SubAction : A, SubStateMachineState : Any, SubStateMachineAction : Any> onActionStartStateMachine(
        actionClass: KClass<out SubAction>,
        stateMachineFactory: (SubAction, InputState) -> StateMachine<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction?,
        stateMapper: (State<InputState>, SubStateMachineState) -> ChangedState<S>,
    ) {
        sideEffectBuilders += SideEffectBuilder(isInState) {
            OnActionStartStateMachine(
                isInState = sideEffectIsInState(),
                subStateMachineFactory = stateMachineFactory,
                subActionClass = actionClass,
                actionMapper = actionMapper,
                stateMapper = stateMapper,
            )
        }
    }
}
