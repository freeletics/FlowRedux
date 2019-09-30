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
    val builderBlocks = ArrayList<StoreWideBlock<S, A>>()

    inline fun <reified SubState : S> inState(
        block: InStateBlock<S, SubState, A>.() -> Unit
    ) {
        // TODO check for duplicate inState { ... } blocks of the same SubType and throw Exception
        val builder = InStateBlock<S, SubState, A>(SubState::class)
        block(builder)
        builderBlocks.add(builder)
    }

    // TODO observe sideeffects that observe other flows

    internal fun generateSideEffects(): List<SideEffect<S, Action<S, A>>> =
        builderBlocks.flatMap { builder ->
            builder.generateSideEffect()
        }.also {
            println("Generated ${it.size} sideeffects")
        }
}

class InStateBlock<S : Any, SubState : S, A : Any>(
    internal val subStateClass: KClass<SubState>
) : StoreWideBlock<S, A>() {

    // TODO is there a better workaround to hide implementation details like this while keep inline fun()
    val _onActionSideEffectBuilders = ArrayList<OnActionSideEffectBuilder<S, A>>()

    inline fun <reified SubAction : A> on(
        flatMapPolicy: OnActionSideEffectBuilder.FlatMapPolicy =
            OnActionSideEffectBuilder.FlatMapPolicy.LATEST,
        noinline block: OnActionBlock<S, SubAction>
    ) {

        @Suppress("UNCHECKED_CAST")
        val builder = OnActionSideEffectBuilder<S, A>(
            flatMapPolicy = flatMapPolicy,
            subActionClass = SubAction::class,
            onActionBlock = block as OnActionBlock<S, A>
        )

        _onActionSideEffectBuilders.add(builder)
    }

    // TODO function to observe as long as we are in that state (i.e. observe a database as long
    //  as we are in that state.

    override fun generateSideEffect(): List<SideEffect<S, Action<S, A>>> {

        return _onActionSideEffectBuilders.map { onAction ->
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
                                subStateClass,
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
                                subStateClass,
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
                                subStateClass,
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
        actions
            .filter { action ->
                // TODO use .isInstance() instead of isSubclassOf() as it should work in kotlin native
                val conditionHolds = subStateClass.isSubclassOf(state()::class) &&
                    action is ExternalWrappedAction &&
                    onAction.subActionClass.isSubclassOf(action.action::class)

                println("filter $action $conditionHolds")

                conditionHolds

            }.map {
                when (it) {
                    is ExternalWrappedAction<*, *> -> onAction.subActionClass.cast(it.action) // TODO kotlin native supported?
                    is SelfReducableAction -> throw IllegalArgumentException("Internal bug. Please file an issue on Github")
                }
            }

    private fun onActionSideEffectFactory(
        action: A,
        stateAccessor: StateAccessor<S>,
        onAction: OnActionSideEffectBuilder<S, A>
    ): Flow<Action<S, A>> =
        flow {
            onAction.onActionBlock.invoke(
                stateAccessor,
                {
                    println("would like to set state because $action to ${it(stateAccessor())}")
                    emit(SelfReducableAction<S, A>(it))
                },
                action
            )
        }
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

/**
 * Observe for the lifetime of the whole redux store.
 * This observation of a flow is independent from any state the store is in nor from actions
 * that have been triggered.
 *
 * A typical usecase would be something like observing a database.
 */
class StoreWideObserveBuilder<S> {
    var setState: SetState<S>? = null
}

sealed class StoreWideBlock<S, A> {
    internal abstract fun generateSideEffect(): List<SideEffect<S, Action<S, A>>>
}


typealias OnActionBlock<S, A> = suspend (getState: StateAccessor<S>, setState: SetState<S>, action: A) -> Unit

typealias SetState<S> = suspend ((currentState: S) -> S) -> Unit