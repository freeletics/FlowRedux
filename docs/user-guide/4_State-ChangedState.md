# ChangeableState`<T>` and ChangedState`<T>`
FlowRedux has the concept of a `ChangeableState<T>` (please note that `T` here is just a placeholder for generics).
It is used as a receiver for many DSL blocks like `onEnter {  }`, meaning that the `this` inside the block is of type
`ChangeableState<T>`.

With this `ChangeableState<T>` object you can get access to the actual state of your state machine with `ChangeableState.snapshot`.
Additionally `ChangeableState<T>` is providing functions to mutate the state or completely override it.
Here is a summary of the API of `ChangeableState<T>` (simplified version, we will dive deeper in a bit):

```kotlin
class ChangeableState<T> {
    // This holds the state value of your state machine at the time when the DSL block was entered.
    // It can be used to get something to for example do an API call with parameters based on the current state.
    val snapshot : T

    // completely replaces the current state with a new one
    fun override(newState : () -> T) : ChangedState<T>

     // mutates the current state value.
     // This is useful if you want to change just a few properties of your state
     // but not the whole state as .override() does.
    fun mutate(block: T.() -> T ) : ChangedState<T>

    // Special use case for the rare case where you really
    // don't want to change the state.
    fun noChange() : ChangedState<T>
}
```

Please note that `override()` and `mutate()` are just syntactic sugar of the same thing.
The reason why both exist is to clearly hint to other software engineers (i.e. pull request reviews) that you either want to move to an entirely new state or just change a few properties of the current state but overall want to stay in the same type of state.

 - use `override()` to explicitly want to **transition to an entirely new state**
 - use `mutate()` if you want to **change just some properties of the current state but stay in the same state class**.

Examples:
```kotlin
spec {

    // DO USE .override() to clearly say you want to move to another type of state
    inState<Loading>{
        onEnter{
            override { Error() }  // OK: move from Loading to Error state
        }
    }

    // DO NOT USE .mutate()
    inState<Loading>{
        onEnter{
            state.mutate { Error() }  // compiler error!
        }
    }
}
```

```kotlin
data class ScreenStatisticsState(
    val name : String,
    val visitCounter : Int
)

spec {
    // DO USE .mutate() to clearly indicate that you just want to
    // change a property but overall stay in same type of state
    inState<ScreenStatisticsState> {
        onEnter {
            mutate { this.copy(visitCounter= this.visitCounter + 1) } // OK: just update a property but stay in ScreenStatisticsState
        }
    }

    // DO NOT USE .override() as you don't want to move to another type of state
    inState<ScreenStatisticsState> {
        onEnter { state : State<ScreenStatisticsState> ->
            state.override {
                copy(visitCounter= this.visitCounter + 1) // compiles but hard to read
            }
        }
    }
}
```


As you see from a `ChangeableState<T>` you can produce a `ChangedState`.
`ChangedState` is something that simply tells FlowRedux internally how the reducer of the FlowReduxStateMachine should merge and compute the next state of your state machine.
`ChangedState` is not meant to be used or instantiated by you manually.
You may wonder "what about writing unit tests?".
We will cover testing and best practices in a [dedicated section](/testing).

We will dive deeper on `ChangeableState.override()` and `ChangeableState.mutate()` as we continue with our `ItemListStateMachineFactory` example.
