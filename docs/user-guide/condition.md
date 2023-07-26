# Custom condition inside inState

We already covered `inState<State>` that builds upon the recommended best practice that every State of your state machine is expressed us it's own type in Kotlin.

Sometimes, however, you need a bit more flexibility than just relaying on types to model state.
For that use case you can add  `condition( isConditionMet: (State) -> Boolean)` blocks inside your `inState<State>` blocks. 

Example: One could have also modeled the state for our `ItemListStateMachine` as the following:

```kotlin
// TO MODEL YOUR STATE LIKE THIS IS NOT BEST PRACTICE!
// In a real world example we recommend using sealed class instead.
data class ListState(
    val loading: Boolean, // true means loading, false means not loading
    val items: List<Items>, // empty list if no items loaded yet
    val error: Throwable?, // if not null we are in error state
    val errorCountDown: Int? // the seconds for the error countdown
)
```

**AGAIN, the example shown above is not the recommended way.
We strongly recommend to use sealed classes instead to model state as shown at the beginning of this document.**

We just do this for demo purpose to demonstrate a way how to customize `inState`.
Given the state from above, what we  can do now with our DSL is the following:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<ListState, Action>(
    initialState = State(
        loading = true,
        items = emptyList(),
        error = null,
        errorCountDown = null
    )
) {

    init {
        spec {
            inState<ListState> {
                condition({ state -> state.loading == true }) {
                    onEnter { state: State<ListState> ->
                        // we entered the Loading, so let's do the http request
                        try {
                            val items = httpClient.loadItems()
                            state.mutate {
                                this.copy(loading = false, items = items, error = null, errorCountdown = null)
                            }
                        } catch (t: Throwable) {
                            state.mutate {
                                this.copy(loading = false, items = emptyList(), error = t, errorCountdown = 3)
                            }
                        }
                    }
                }

                condition({ state -> state.error != null }) {
                    on<RetryLoadingAction> { action : RetryLoadingAction, state : State<ListState> ->
                        state.mutate {
                            this.copy(loading = true, items = emptyList(), error = null, errorCountdown = null)
                        }
                    }

                    val timer : Flow<Int> = timerThatEmitsEverySecond()
                    collectWhileInState(timer) { value : Int, state : State<ListState> ->
                        state.mutate {
                            if (errorCountdown!! > 0)
                                //  decrease the countdown by 1 second
                                this.copy(errorCountdown = this.errorCountdown!! - 1)
                            else
                                // transition to the Loading
                                this.copy(
                                    loading = true,
                                    items = emptyList(),
                                    error = null,
                                    errorCountdown = null
                                )
                        }
                    }
                }
            }
        }
    }
}
```

`condition` takes a lambda as parameter with the following signature: `(State) -> Boolean`.
If that lambda returns `true` it means the condition is met, otherwise not (returning false).
You can use `onEnter`, `on<Action>` and `collectWhileInState` the exact way as you already know, just wrapped around a `condition` block.
