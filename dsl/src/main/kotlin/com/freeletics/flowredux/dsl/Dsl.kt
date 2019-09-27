package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import com.freeletics.flowredux.reduxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.full.cast
import kotlin.reflect.full.isSubclassOf

fun <S : Any, A : Any> Flow<A>.reduxStoreDsl(
    initialState: S, block: FlowReduxStoreBuilder<S, A>.() -> Unit
): Flow<S> {
    val builder = FlowReduxStoreBuilder<S, A>()
    block(builder)

    return this.map { ExternalWrappedAction(it) }
        .reduxStore<Action<A>, S>(
            initialStateSupplier = { initialState },
            reducer = ::reducer,
            sideEffects = builder.generateSideEffects()
        )

    // TODO we may neeed a loop back to propagate internally state changes.
    //  See TODO in SelfReducableAction.kt
}

class FlowReduxStoreBuilder<S : Any, A : Any> {

    // TODO is there a better workaround to hide implementation details like this while keep inline fun()
    val _inStateBuilders = ArrayList<InStateSideEffectBuilder<S, out S, A>>()

    inline fun <reified SubState : S> inState(
        block: InStateSideEffectBuilder<S, SubState, A>.() -> Unit
    ) {
        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        val builder = InStateSideEffectBuilder<S, SubState, A>(SubState::class)
        block(builder)
        _inStateBuilders.add(builder)
    }

    // TODO observe sideeffects that observe other flows
    internal fun generateSideEffects(): List<SideEffect<S, Action<A>>> =
        _inStateBuilders.flatMap { builder ->
            builder._onActionSideEffectBuilders.map { onAction ->
                object : SideEffect<S, Action<A>> {
                    override fun invoke(
                        actions: Flow<Action<A>>,
                        state: StateAccessor<S>
                    ): Flow<Action<A>> {
                        return when (onAction.flatMapPolicy) {
                            OnActionSideEffectBuilder.FlatMapPolicy.LATEST ->
                                actionsFilterFactory(
                                    actions,
                                    state,
                                    builder.subStateClass,
                                    onAction
                                )
                                    .flatMapLatest { action ->
                                        setStateSideEffectFactory(
                                            action = action,
                                            stateAccessor = state,
                                            onAction = onAction
                                        )
                                    }

                            OnActionSideEffectBuilder.FlatMapPolicy.MERGE ->
                                actionsFilterFactory(
                                    actions,
                                    state,
                                    builder.subStateClass,
                                    onAction
                                )
                                    .flatMapMerge { action ->
                                        setStateSideEffectFactory(
                                            action = action,
                                            stateAccessor = state,
                                            onAction = onAction
                                        )
                                    }

                            OnActionSideEffectBuilder.FlatMapPolicy.CONCAT ->
                                actionsFilterFactory(
                                    actions,
                                    state,
                                    builder.subStateClass,
                                    onAction
                                )
                                    .flatMapConcat { action ->
                                        setStateSideEffectFactory(
                                            action = action,
                                            stateAccessor = state,
                                            onAction = onAction
                                        )
                                    }
                        }
                    }
                }
            }
        }

    /**
     * Creates a Flow that filters for a given (sub)state and (sub)action and returns a
     * Flow of (sub)action
     */
    private fun actionsFilterFactory(
        actions: Flow<Action<out A>>,
        state: StateAccessor<S>,
        subStateClass: KClass<out S>,
        onAction: OnActionSideEffectBuilder<S, out A>
    ): Flow<A> =
        actions.filter { action ->
            subStateClass.isSubclassOf(state()::class) &&
                action is ExternalWrappedAction &&
                onAction.subActionClass.isInstance(action.action::class)
        }.map {
            when (it) {
                is ExternalWrappedAction<*> -> onAction.subActionClass.cast(it.action)
                is SelfReducableAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
            }
        }

    private fun <SubAction : A> setStateSideEffectFactory(
        action: SubAction,
        stateAccessor: StateAccessor<S>,
        onAction: OnActionSideEffectBuilder<S, A>
    ): Flow<Action<A>> =
        flow {

            val setStateInterceptor = object : SetState<S> {
                override fun invoke(p1: (currentState: S) -> S) {
                }
            }

            onAction.setStateSideEffect.invoke(
                stateAccessor,
                setStateInterceptor,
                action
            )
        }
}

class InStateSideEffectBuilder<S : Any, SubState : S, A : Any>(
    internal val subStateClass: KClass<SubState>
) {

    // TODO is there a better workaround to hide implementation details like this while keep inline fun()
    val _onActionSideEffectBuilders = ArrayList<OnActionSideEffectBuilder<S, A>>()

    inline fun <reified SubAction : A> on(
        flatMapPolicy: OnActionSideEffectBuilder.FlatMapPolicy =
            OnActionSideEffectBuilder.FlatMapPolicy.LATEST,
        noinline sideEffect: SetStateSideEffect<S, SubAction>
    ) {

        val builder = OnActionSideEffectBuilder(
            flatMapPolicy = flatMapPolicy,
            subActionClass = SubAction::class,
            setStateSideEffect = sideEffect
        )

        _onActionSideEffectBuilders.add(builder)
    }

    // TODO function to observe as long as we are in that state (i.e. observe a database as long
    //  as we are in that state.
}

class OnActionSideEffectBuilder<S : Any, A : Any>(
    internal val subActionClass: KClass<A>,
    internal val flatMapPolicy: FlatMapPolicy,
    internal val setStateSideEffect: SetStateSideEffect<S, A>
) {

    // TODO find better name
    enum class FlatMapPolicy {
        /**
         * uses flatMapLatest
         */
        LATEST,
        /**
         * Uses flatMapMerge
         */
        MERGE,
        /**
         * Uses flatMapConcat
         */
        CONCAT
    }
}


typealias SetStateSideEffect<S, A> = suspend (getState: StateAccessor<S>, setState: SetState<S>, action: A) -> Unit

typealias SetState<S> = ((currentState: S) -> S) -> Unit