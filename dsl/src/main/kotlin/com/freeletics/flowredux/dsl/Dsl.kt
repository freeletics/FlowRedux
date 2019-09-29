package com.freeletics.flowredux.dsl

import com.freeletics.flowredux.SideEffect
import com.freeletics.flowredux.StateAccessor
import com.freeletics.flowredux.reduxStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.startWith
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.full.cast
import kotlin.reflect.full.isSubclassOf

fun <S : Any, A : Any> Flow<A>.reduxStoreDsl(
    initialState: S, block: FlowReduxStoreBuilder<S, A>.() -> Unit
): Flow<S> {
    val builder = FlowReduxStoreBuilder<S, A>()
    block(builder)

    return this.map { ExternalWrappedAction<S, A>(it) }
        .reduxStore<Action<S, A>, S>(
            initialStateSupplier = { initialState },
            reducer = ::reducer,
            sideEffects = builder.generateSideEffects()
        )

    // TODO we may neeed a loop back to propagate internally state changes.
    //  See TODO in SelfReducableAction.kt
}

class FlowReduxStoreBuilder<S : Any, A : Any> {

    // TODO is there a better workaround to hide implementation details like this while keep inline fun()
    val _inStateBuilders = ArrayList<InStateBlock<S, out S, A>>()

    inline fun <reified SubState : S> inState(
        block: InStateBlock<S, SubState, A>.() -> Unit
    ) {
        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        val builder = InStateBlock<S, SubState, A>(SubState::class)
        block(builder)
        _inStateBuilders.add(builder)
    }

    // TODO observe sideeffects that observe other flows

    internal fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> =
        _inStateBuilders.flatMap { builder ->
            builder._onActionSideEffectBuilders.map { onAction ->
                object : SideEffect<S, Action<S, A>> {
                    override fun invoke(
                        actions: Flow<Action<S, A>>,
                        state: StateAccessor<S>
                    ): Flow<Action<S, A>> {
                        return when (onAction.flatMapPolicy) {
                            OnActionSideEffectBuilder.FlatMapPolicy.LATEST ->
                                actionsFilterFactory(
                                    actions,
                                    state,
                                    builder.subStateClass,
                                    onAction
                                )
                                    .flatMapLatest { action ->
                                        onActionSideEffectFactory(
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
                                        onActionSideEffectFactory(
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
                                        onActionSideEffectFactory(
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
        actions: Flow<Action<S, out A>>,
        state: StateAccessor<S>,
        subStateClass: KClass<out S>,
        onAction: OnActionSideEffectBuilder<S, out A>
    ): Flow<A> =
        actions.filter { action ->
            println("filter $action")
            subStateClass.isSubclassOf(state()::class) &&
                action is ExternalWrappedAction &&
                onAction.subActionClass.isInstance(action.action::class)
        }.map {
            when (it) {
                is ExternalWrappedAction<*, *> -> onAction.subActionClass.cast(it.action)
                is SelfReducableAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
            }
        }

    private fun onActionSideEffectFactory(
        action: A,
        stateAccessor: StateAccessor<S>,
        onAction: OnActionSideEffectBuilder<S, A>
    ): Flow<Action<S, A>> =
        flow {

            val setStateInterceptor = object : SetState<S> {
                override fun invoke(p1: (currentState: S) -> S) {
                    println("would like to set state because $action")
                    // emit(SelfReducableAction<S, A>(p1))
                    // TODO make this suspend somehow
                }
            }

            onAction.onActionBlock.invoke(
                stateAccessor,
                setStateInterceptor,
                action
            )
        }
}

class InStateBlock<S : Any, SubState : S, A : Any>(
    internal val subStateClass: KClass<SubState>
) {

    // TODO is there a better workaround to hide implementation details like this while keep inline fun()
    val _onActionSideEffectBuilders = ArrayList<OnActionSideEffectBuilder<S, A>>()

    inline fun <reified SubAction : A> on(
        flatMapPolicy: OnActionSideEffectBuilder.FlatMapPolicy =
            OnActionSideEffectBuilder.FlatMapPolicy.LATEST,
        noinline block: OnActionBlock<S, SubAction>
    ) {

        val builder = OnActionSideEffectBuilder<S, A>(
            flatMapPolicy = flatMapPolicy,
            subActionClass = SubAction::class,
            onActionBlock = block as OnActionBlock<S, A>
        )

        _onActionSideEffectBuilders.add(builder)
    }

    // TODO function to observe as long as we are in that state (i.e. observe a database as long
    //  as we are in that state.
}

class OnActionSideEffectBuilder<S : Any, A : Any>(
    internal val subActionClass: KClass<out A>,
    internal val flatMapPolicy: FlatMapPolicy,
    internal val onActionBlock: OnActionBlock<S, A>
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


typealias OnActionBlock<S, A> = suspend (getState: StateAccessor<S>, setState: SetState<S>, action: A) -> Unit

typealias SetState<S> = ((currentState: S) -> S) -> Unit