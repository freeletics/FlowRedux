# FlowReduxStateMachine vs .reduxStore()

The DSL provided by FlowRedux can be use int two ways:

1. Extending from `FlowReduxStateMachine`
2. Use `.reduxStore()` which is a custom operator on Kotlin's `Flow` type.

## FlowReduxStateMachine
This is probably the easiest way to get started writing a State Machine with FlowRedux DSL.

```kotlin
class MyStateMachine : FlowReduxStateMachine<State, Action>(InitialState){

    init {
        spec {
            // Your DSL goes inside this spec block.
            // Example:
            inState<State1> {
                onAction<Action1> { ... }
            }
        }
    }

}
```

See [DSL section](dsl.md) for more information how to use the DSL.

The advantage of extending from `FlowReduxStateMachine` is that you get a ready a base template
where you just have to fill in the DSL part inside the `spec { ... }` block.
By extending from `FlowReduxStateMachine` your StateMachine inherits a `dispatch(action : Action)`
method to dispatch Actions to your state machine and a `val state: Flow<State>` to observe your
state machine's state.
Whenever the state changes, the latest State will be emitted to this `Flow`.

## .reduxStore()

If you want to work with Flow type directly FlowRedux provides you a custom operator called
`.reduxStore()`.
It is a custom operator like any other Flow operator like `Flow.map { ... }`.
The idea is that the upstream Flow brings the actions to your FlowReduxStore like this:

```kotlin
sealed class Action{
    object Action1 : MyAction()
    object Action2 : MyAction()
}
```

```kotlin
val actionFlow : Flow<Action> = flowOf {
    emit(Action1)
    delay(2000)
    emit(Action2)
}

val stateFlow : Flow<State> = actionFlow // actionFlow is the input actions to the state machine
    .reduxStore<Action, State>(IntialState) {
        // Your DSL goes inside this spec block.
        // Example:
        inState<State1> {
            onAction<Action1> { ... }
        }
    }

stateFlow.collect { state ->
    updateUi(state)
}
```



