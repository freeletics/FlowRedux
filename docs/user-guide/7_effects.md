# Effects

If you don't want to change the state but do some work without changing the state (i.e., logging,
triggering Google Analytics, or triggering navigation), then effects are what you are looking for.

The following counterparts to `on<Action>`, `onEnter` and `collectWhileInState` exist:

- `onActionEffect<Action>`: Like `on<Action>`, this triggers whenever the described action is dispatched.
- `onEnterEffect`: Like `onEnter` this triggers whenever you enter the state.
- `collectWhileInStateEffect`: Like `collectWhileInState`, this is used to collect a `Flow`.


Effects behave the same way as their counterparts; the main difference is that the receiver is `State` instead of `ChangeableState`.
`State` only provides access to the current state through `snapshot`, but has no mutating functions like `override()` or `mutate()`.
Because of that effect blocks also don't have to return a `ChangedState`, but can just return `Unit`.

Other behaviors like cancellation, etc., work just the same way as described in the sections on `on<Action>`, `onEnter`, and `collectWhileInState`.

Usage:
```kotlin
class ItemListStateMachineFactory : FlowReduxStateMachineFactory<ListState, Action>() {

    init {
        initializeWith { Loading }

        spec {
            inState<Error> {
               onEnterEffect {
                   logMessage("Did enter $snapshot") // Note: there is no state change
               }

               onActionEffect<RetryLoadingAction> { action : RetryLoadingAction ->
                    // current state can be accessed through snapshot
                    analyticsTracker.track(ButtonClickedEvent()) // Note: there is no state change
               }

                val someFlow : Flow<String> = TODO()
                collectWhileInStateEffect(someFlow) { value : String ->
                    logMessage("Collected $value from flow while in state $snapshot") // Note: there is no state change
                }
            }

        }
    }
}
```
