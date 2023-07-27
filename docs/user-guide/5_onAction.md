# on`<Action>`

How do we deal with external user input like clicks in FlowRedux?
This is what `Action` is for.
With the DSL of FlowRedux you can specify what should be done when a certain `Action` (triggered by the user) happened.

In our example we want to retry loading if we are in `Error` state. In the `Error` state our UI shows a error text and a button the user can click on to retry loading the list of items.
Clicking on that button dispatches a `RetryLoadingAction` to our state machine.
Let's extend our `ItemListStateMachine` to react on such an action:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<ListState, Action>(initialState = Loading) {

    init {
        spec {
            inState<Loading> {
                onEnter { state: State<Loading> ->
                    // We have discussed this block already in a previous section
                    try {
                        val items = httpClient.loadItems()
                        state.override { ShowContent(items) }
                    } catch (t: Throwable) {
                        state.override { Error(t) }
                    }
                }
            }

            // let's add a new inState{...} with an on{...} block
            inState<Error> {
                on<RetryLoadingAction> { action: RetryLoadingAction, state: State<Error> ->
                    // This block triggers if we are in Error state and
                    // RetryLoadingAction has been dispatched to this state machine.
                    // In that case we transition to Loading state which then starts the http
                    // request to load items again as the inState<Loading> + onEnter { ... } triggers

                    state.override { Loading }
                }
            }
        }
    }
}
```

An `on { ... }` block gets 2 parameters:  `action` which is the actual instance of the `Action` that triggered this block
and `state : State<T>` which gives us access to the current state and let us to state transitions with `.override()`.
`on { ... }` is actually pretty similar to `onEnter {...}` just with a different "trigger" (action vs. entering a state)
. Furthermore, `on { ... }` has the same characteristics as `onEnter { ... }`:

- **`on { ... }` is running asynchronously in a coroutine**. That means whatever you do inside the `on` block is not
  blocking anything else. You can totally run here long-running and expensive calls (like doing a http request).
- **`on { ... }` expects a lambda (or function) with the following
  signature: `(action : Action , state : State<T>) -> ChangedState<T>`**.
- **The execution of the `on { ... }` is canceled as soon as state condition specified in the surrounding `inState` block
  doesn't hold anymore (i.e. state has been changes by something else).**

So far this is how our result look like:

![retry-action](../images/lce.gif)
