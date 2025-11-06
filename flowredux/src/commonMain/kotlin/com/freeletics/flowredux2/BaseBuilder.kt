package com.freeletics.flowredux2

import com.freeletics.flowredux2.sideeffects.CollectWhile
import com.freeletics.flowredux2.sideeffects.OnAction
import com.freeletics.flowredux2.sideeffects.OnActionStartStateMachine
import com.freeletics.flowredux2.sideeffects.OnEnter
import com.freeletics.flowredux2.sideeffects.OnEnterStartStateMachine
import com.freeletics.flowredux2.sideeffects.SideEffect
import com.freeletics.flowredux2.sideeffects.SideEffectBuilder
import com.freeletics.flowredux2.util.FlowReduxDsl
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
@FlowReduxDsl
public abstract class BaseBuilder<InputState : S, S : Any, A : Any> internal constructor() {
    internal abstract val isInState: SideEffectBuilder.IsInState<S>
    internal abstract val logger: TaggedLogger?

    internal open fun sideEffectIsInState(initialState: InputState) = SideEffect.IsInState<S> {
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
        executionPolicy: ExecutionPolicy = ExecutionPolicy.CancelPrevious,
        noinline handler: suspend ChangeableState<InputState>.(action: SubAction) -> ChangedState<S>,
    ) {
        on(SubAction::class, executionPolicy, handler)
    }

    @PublishedApi
    internal fun <SubAction : A> on(
        actionClass: KClass<SubAction>,
        executionPolicy: ExecutionPolicy,
        handler: suspend ChangeableState<InputState>.(action: SubAction) -> ChangedState<S>,
    ) {
        val logger = logger?.wrap("on<${actionClass.simpleName}>")
        sideEffectBuilders += SideEffectBuilder(isInState, logger) {
            OnAction(
                isInState = sideEffectIsInState(it),
                subActionClass = actionClass,
                executionPolicy = executionPolicy,
                handler = handler,
                logger = logger,
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
        executionPolicy: ExecutionPolicy = ExecutionPolicy.CancelPrevious,
        noinline handler: suspend State<InputState>.(action: SubAction) -> Unit,
    ) {
        onActionEffect(SubAction::class, executionPolicy, handler)
    }

    @PublishedApi
    internal fun <SubAction : A> onActionEffect(
        actionClass: KClass<SubAction>,
        executionPolicy: ExecutionPolicy,
        handler: suspend State<InputState>.(action: SubAction) -> Unit,
    ) {
        val logger = logger?.wrap("onActionEffect<${actionClass.simpleName}>")
        sideEffectBuilders += SideEffectBuilder(isInState, logger) {
            OnAction(
                isInState = sideEffectIsInState(it),
                subActionClass = actionClass,
                executionPolicy = executionPolicy,
                handler = { action ->
                    handler(action)
                    NoStateChangeSkipEmission
                },
                logger = logger,
            )
        }
    }

    /**
     * Triggers every time the state machine enters this state.
     * It only triggers again if the surrounding `in<State>` condition is met and will only
     * re-trigger if `in<State>` condition returned false and then true again.
     *
     * An ongoing [handler] is cancelled when leaving this state.
     */
    public fun onEnter(
        handler: suspend ChangeableState<InputState>.() -> ChangedState<S>,
    ) {
        sideEffectBuilders += SideEffectBuilder(isInState, logger) { initialState ->
            OnEnter(
                isInState = sideEffectIsInState(initialState),
                initialState = initialState,
                handler = handler,
                logger = logger,
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
        handler: suspend State<InputState>.() -> Unit,
    ) {
        sideEffectBuilders += SideEffectBuilder(isInState, logger) { initialState ->
            OnEnter(
                isInState = sideEffectIsInState(initialState),
                initialState = initialState,
                handler = {
                    handler()
                    NoStateChangeSkipEmission
                },
                logger = logger,
            )
        }
    }

    /**
     * Triggers every time the state machine enters this state. The passed [flow] will be collected
     * and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [executionPolicy] is used to determine the behavior when a new emission from [flow] arrives
     * before the previous [handler] invocation completed. By default [ExecutionPolicy.Ordered]
     * is applied.
     */
    public fun <T> collectWhileInState(
        flow: Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.Ordered,
        name: String? = null,
        handler: suspend ChangeableState<InputState>.(item: T) -> ChangedState<S>,
    ) {
        val logger = logger?.wrap("collectWhileInState<${name ?: "?"}>")
        sideEffectBuilders += SideEffectBuilder(isInState, logger) {
            CollectWhile(
                isInState = sideEffectIsInState(it),
                flow = flow,
                executionPolicy = executionPolicy,
                handler = handler,
                logger = logger,
            )
        }
    }

    /**
     * Triggers every time the state machine enters this state. The passed [Flow] created by
     * [flowBuilder] will be collected and any emission will be passed to [handler].
     *
     * The collection as well as any ongoing [handler] is cancelled when leaving this state.
     *
     * [executionPolicy] is used to determine the behavior when a new emission from `Flow` arrives
     * before the previous [handler] invocation completed. By default [ExecutionPolicy.Ordered]
     * is applied.
     */
    public fun <T> collectWhileInState(
        flowBuilder: (InputState) -> Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.Ordered,
        name: String? = null,
        handler: suspend ChangeableState<InputState>.(item: T) -> ChangedState<S>,
    ) {
        val logger = logger?.wrap("collectWhileInState<${name ?: "?"}>")
        sideEffectBuilders += SideEffectBuilder(isInState, logger) { initialState ->
            CollectWhile(
                isInState = sideEffectIsInState(initialState),
                flow = flowBuilder(initialState),
                executionPolicy = executionPolicy,
                handler = handler,
                logger = logger,
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
        executionPolicy: ExecutionPolicy = ExecutionPolicy.Ordered,
        name: String? = null,
        handler: suspend State<InputState>.(item: T) -> Unit,
    ) {
        val logger = logger?.wrap("collectWhileInStateEffect<${name ?: "?"}>")
        sideEffectBuilders += SideEffectBuilder(isInState, logger) {
            CollectWhile(
                isInState = sideEffectIsInState(it),
                flow = flow,
                executionPolicy = executionPolicy,
                handler = { value: T ->
                    handler(value)
                    NoStateChangeSkipEmission
                },
                logger = logger,
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
        flowBuilder: (InputState) -> Flow<T>,
        executionPolicy: ExecutionPolicy = ExecutionPolicy.Ordered,
        name: String? = null,
        handler: suspend State<InputState>.(item: T) -> Unit,
    ) {
        val logger = logger?.wrap("collectWhileInStateEffect<${name ?: "?"}>")
        sideEffectBuilders += SideEffectBuilder(isInState, logger) { initialState ->
            CollectWhile(
                isInState = sideEffectIsInState(initialState),
                flow = flowBuilder(initialState),
                executionPolicy = executionPolicy,
                handler = { value: T ->
                    handler(value)
                    NoStateChangeSkipEmission
                },
                logger = logger,
            )
        }
    }

    public fun <SubStateMachineState : Any> onEnterStartStateMachine(
        stateMachineFactoryBuilder: State<InputState>.() -> FlowReduxStateMachineFactory<SubStateMachineState, A>,
        cancelOnState: (SubStateMachineState) -> Boolean = { false },
        name: String? = null,
        handler: suspend ChangeableState<InputState>.(SubStateMachineState) -> ChangedState<S>,
    ) {
        onEnterStartStateMachine(
            stateMachineFactoryBuilder = stateMachineFactoryBuilder,
            actionMapper = { it },
            cancelOnState = cancelOnState,
            name = name,
            handler = handler,
        )
    }

    public fun <SubStateMachineState : Any, SubStateMachineAction : Any> onEnterStartStateMachine(
        stateMachineFactoryBuilder: State<InputState>.() -> FlowReduxStateMachineFactory<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction?,
        cancelOnState: (SubStateMachineState) -> Boolean = { false },
        name: String? = null,
        handler: suspend ChangeableState<InputState>.(SubStateMachineState) -> ChangedState<S>,
    ) {
        val logger = logger?.wrap("onEnterStartStateMachine<${name ?: "?"}>")
        sideEffectBuilders += SideEffectBuilder(isInState, logger) { initialState ->
            OnEnterStartStateMachine(
                isInState = sideEffectIsInState(initialState),
                subStateMachineFactory = ChangeableState(initialState).stateMachineFactoryBuilder(),
                actionMapper = actionMapper,
                cancelOnState = cancelOnState,
                handler = handler,
                logger = logger,
            )
        }
    }

    public inline fun <reified SubAction : A, SubStateMachineState : Any> onActionStartStateMachine(
        noinline stateMachineFactoryBuilder: State<InputState>.(SubAction) -> FlowReduxStateMachineFactory<SubStateMachineState, A>,
        noinline cancelOnState: (SubStateMachineState) -> Boolean = { false },
        name: String? = null,
        noinline handler: suspend ChangeableState<InputState>.(SubStateMachineState) -> ChangedState<S>,
    ) {
        onActionStartStateMachine(
            actionClass = SubAction::class,
            stateMachineFactoryBuilder = stateMachineFactoryBuilder,
            actionMapper = { it },
            cancelOnState = cancelOnState,
            name = name,
            handler = handler,
        )
    }

    public inline fun <reified SubAction : A, SubStateMachineState : Any, SubStateMachineAction : Any> onActionStartStateMachine(
        noinline stateMachineFactoryBuilder: State<InputState>.(SubAction) -> FlowReduxStateMachineFactory<SubStateMachineState, SubStateMachineAction>,
        noinline actionMapper: (A) -> SubStateMachineAction?,
        noinline cancelOnState: (SubStateMachineState) -> Boolean = { false },
        name: String? = null,
        noinline handler: suspend ChangeableState<InputState>.(SubStateMachineState) -> ChangedState<S>,
    ) {
        onActionStartStateMachine(
            actionClass = SubAction::class,
            stateMachineFactoryBuilder = stateMachineFactoryBuilder,
            actionMapper = actionMapper,
            cancelOnState = cancelOnState,
            name = name,
            handler = handler,
        )
    }

    @PublishedApi
    internal fun <SubAction : A, SubStateMachineState : Any, SubStateMachineAction : Any> onActionStartStateMachine(
        actionClass: KClass<out SubAction>,
        stateMachineFactoryBuilder: State<InputState>.(SubAction) -> FlowReduxStateMachineFactory<SubStateMachineState, SubStateMachineAction>,
        actionMapper: (A) -> SubStateMachineAction?,
        cancelOnState: (SubStateMachineState) -> Boolean = { false },
        name: String? = null,
        handler: suspend ChangeableState<InputState>.(SubStateMachineState) -> ChangedState<S>,
    ) {
        val logger = logger?.wrap("onActionStartStateMachine<${name ?: "?"}>")
        sideEffectBuilders += SideEffectBuilder(isInState, logger) {
            OnActionStartStateMachine(
                isInState = sideEffectIsInState(it),
                stateMachineFactoryBuilder = stateMachineFactoryBuilder,
                subActionClass = actionClass,
                actionMapper = actionMapper,
                cancelOnState = cancelOnState,
                handler = handler,
                logger = logger,
            )
        }
    }
}
