# Improve readability of your DSL spec

One very important aspect of the DSL is to provide a readable and maintainable way to reason about your state machine.
Let' take a look at our example state machine:

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
                        state.override { Error("A network error occurred", countdown = 3) }   // countdown is new
                    }
                }
            }

            inState<Error> {
                on<RetryLoadingAction> { action: RetryLoadingAction, state: State<Error> ->
                    // We have discussed this block already in a previous section
                    state.override { Loading }
                }

                val timer : Flow<Int> = timerThatEmitsEverySecond()
                collectWhileInState(timer) { timerValue: Int, state: State<Error> ->
                    // This block triggers every time the timer emits
                    // which happens every second
                    state.override { // we use .override() because we could move to another type of state
                        // inside this block, this references Error state
                        if (this.countdown > 0)
                            this.copy(countdown = this.countdown - 1) // decrease countdown by 1 second
                        else
                            Loading // transition to the Loading state
                    }
                }
            }
        }
    }

    private fun timerThatEmitsEverySecond(): Flow<Int> = flow {
        var timeElapsed = 0
        while (isActive) {  // is Flow still active?
            delay(1000)     // wait 1 second
            timeElapsed++
            emit(timeElapsed) // Flow Emits value
        }
    }
}
```

Do you notice something?
With more blocks we add the state machine itself gets harder to read, to understand and to maintain.
What we are aiming for with FlowRedux and it's DSL is to get a readable overview about what the state machine is supposed to do on a high level.
If you take a look at the example from above, however, you will notice that it isn't easy
to read and get bloated with implementation details.

## Extract logic to functions

We recommend keeping the DSL `spec { ... }` block really short, expressive, readable and maintainable.
Therefore, instead of having implementation details in your DSL we recommend to extract that to functions instead.
Let's refactor the example above to reflect this idea:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<ListState, Action>(initialState = Loading) {

    // This is the specification of your state machine.
    // Less implementation details, better readability.
    init {
        spec {
            inState<Loading> {
                onEnter { loadItemsAndMoveToContentOrError(it) }
            }

            inState<Error> {
                on<RetryLoadingAction> { action, state ->
                    // For a single line statement it's ok to keep logic inside the block instead
                    // of extracting it a function (but it also depends on your testing strategy)
                    state.override { Loading }
                }

                collectWhileInState(timerThatEmitsEverySecond()) { value, state  ->
                    decrementCountdownAndMoveToLoading(value, state)
                }
            }
        }
    }


    //
    // All the implementation details are in the functions below.
    //
    private suspend fun loadItemsAndMoveToContentOrError(state: State<Loading>): ChangedState<State> {
        return try {
            val items = httpClient.loadItems()
            state.override { ShowContent(items) }
        } catch (t: Throwable) {
            state.override { Error(cause = t, countdown = 3) }
        }
    }

    private fun decrementCountdownAndMoveToLoading(
        value: Int,
        state: State<Error>
    ): ChangedState<State> {
        return state.override {
            if (this.countdownTimeLeft > 0)
                this.copy(countdown = countdownTimeLeft - 1)
            else
                Loading
        }
    }

    private fun timerThatEmitsEverySecond(): Flow<Int> = flow {
        var timeElapsed = 0
        while (isActive) {
            delay(1000)
            timeElapsed++
            emit(timeElapsed)
        }
    }
}
```

Moreover, have you notice that the extracted function now all get a similar method signature:

```kotlin
suspend fun doSomething(state : State<T>) : ChangedState<T>
```

We are now getting closer to [pure functions](https://en.wikipedia.org/wiki/Pure_function).
This makes writing unit test easier because for the same input (`State`) pure functions return the same output (`ChangedState`).
We will talk about that more in detail in the dedicated section about testing best practices.
