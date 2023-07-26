# inState`<State>`

The first concept of the DSL we learn is `inState`:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<ListState, Action>(initialState = Loading) {

    init {
        spec {
            inState<Loading> {
                ...
            }
        }
    }
}
```

`inState<Loading>` is just an "entry point".
Next let's discuss what an `inState` block can contain as triggers to actually "do something":

1. `onEnter`: Triggers whenever we enter that state
2. `on<Action>`: Triggers whenever we are in this state and the specified action is triggered from the outside by
   calling `FlowReduxStateMachine.dispatch(action)`.
3. `collectWhileInState( flow )`: You can subscribe to any arbitrary `Flow` while your state machine is in that state.

Additionally `onEnterStartStateMachine()` and `onActionStartStateMachine()` can be placed inside an `inState{ ... }` block, but we will talk about these advanced concepts that are useful for composing business logic later.

Let's take a more closer look at 3 basic elements `onEnter`, `on<Action>` and `collectWhileInState`  as we build our state machine.