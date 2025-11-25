# on`<Action>`

How do we deal with external user input like clicks in FlowRedux?
This is what `Action` is for.
With the DSL of FlowRedux you can specify what should be done when a certain `Action` (triggered by the user) happened.

In our example we want to retry loading if we are in the `Error` state. In the `Error` state our UI shows an error text and a button the user can click to retry loading the list of items.
Clicking on that button dispatches a `RetryLoadingAction` to our state machine.
Let's extend our `ItemListStateMachineFactory` to react to such an action:

```kotlin
class ItemListStateMachineFactory(
    private val httpClient: HttpClient
) : FlowReduxStateMachineFactory<ListState, Action>() {

    init {
        initializeWith { Loading }

        spec {
            inState<Loading> {
                onEnter { state: State<Loading> ->
                    // We have discussed this block already in a previous section
                    try {
                        val items = httpClient.loadItems()
                        state.override { ShowContent(items) }
                    } catch (t: Throwable) {
                        state.override { Error("A network error occurred") }
                    }
                }
            }

            // let's add a new inState{...} with an on{...} block
            inState<Error> {
                on<RetryLoadingAction> { action: RetryLoadingAction ->
                    // This block triggers if we are in Error state and
                    // RetryLoadingAction has been dispatched to this state machine.
                    // In that case we transition to Loading state which then starts the HTTP
                    // request to load items again as the inState<Loading> + onEnter { ... } triggers

                    override { Loading }
                }
            }
        }
    }
}
```

The `on { ... }` block has `ChangeableState` as a receiver and gets the `action`, which is the actual instance of the `Action` that
triggered this block as a parameter.
`on { ... }` is actually pretty similar to `onEnter { ... }`, just with a different "trigger" (action vs. entering a state).
Furthermore, `on { ... }` has the same characteristics as `onEnter { ... }`:

- **`on { ... }` is running asynchronously in a coroutine**. That means whatever you do inside the `on` block is not
  blocking anything else. You can run suspending calls (like doing an HTTP request).
- **`on { ... }` expects a lambda (or function) with the following
  signature: `ChangeableState<T>.(action : Action) -> ChangedState<T>`**.
- **The execution of the `on { ... }` is canceled as soon as the state condition specified in the surrounding `inState` block
  doesn't hold anymore (i.e. the state has been changed by something else).**

So far, this is how our result looks:

![retry-action](../images/lce.gif)
