# Effects

If you don't want to change the state but do some work without changing the state i.e. logging,
triggering google analytics or trigger navigation then Effects are what you are looking for.

The following counterparts to `on<Action>`, `onEnter` and `collectWhileInState` exists:

- `onActionEffect<Action>`: Like `on<Action>` this triggers whenever the described Action is dispatched.
- `onEnterEffect`: Like `onEnter` this triggers whenever you enter the state.
- `collectWhileInStateEffect`: Like `collectWhileInState` this is used to collect a `Flow`.


Effects behave the same way as their counterparts.
For example cancelation etc. works just the same way as described in the section of `on<Action>`, `onEnter` and `collectWhileInState`.Effects

Usage:
```kotlin
class ItemListStateMachine : FlowReduxStateMachine<ListState, Action>(initialState = Loading) {

    init {
        spec {
            inState<Error> {
               onEnterEffect { stateSnapshot : Error ->
                   logMessage("Did enter $stateSnapshot") // note there is no state change
               }

               onActionEffect<RetryLoadingAction> { action : RetryLoadingAction, stateSnapshot : Error ->
                    analyticsTracker.track(ButtonClickedEvent()) // note there is no state change
               }

                val someFolow : Flow<String> = ...
                collectWhileInStateEffect(someFlow) {value : String , stateSnapshot : Error ->
                    logMessage("Collected $value from flow while in state $stateSnapshot") // note there is no state change
                }
            }

        }
    }
}
```
