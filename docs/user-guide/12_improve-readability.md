# Improve readability of your DSL spec

One very important aspect of the DSL is to provide a readable and maintainable way to reason about your state machine.
Let's take a look at our example state machine:

```kotlin
class ItemListStateMachineFactory(
    private val httpClient: HttpClient
) : FlowReduxStateMachineFactory<ListState, Action>() {

    init {
        initializeWith {
            Loading
        }

        spec {
            inState<Loading> {
                onEnter {
                    // We have discussed this block already in a previous section
                    try {
                        val items = httpClient.loadItems()
                        override { ShowContent(items) }
                    } catch (t: Throwable) {
                        override { Error("A network error occurred", countdown = 3) }   // countdown is new
                    }
                }
            }

            inState<Error> {
                on<RetryLoadingAction> { action: RetryLoadingAction ->
                    // We have discussed this block already in a previous section
                    state.override { Loading }
                }

                val timer : Flow<Int> = timerThatEmitsEverySecond()
                collectWhileInState(timer) { timerValue: Int ->
                    // This block triggers every time the timer emits
                    // which happens every second
                    state.override { // we use .override() because we could move to another type of state
                        // inside this block, this references Error state
                        if (this.countdown > 0) {
                            this.copy(countdown = this.countdown - 1) // decrease countdown by 1 second
                        } else {
                            Loading // transition to the Loading state
                        }
                    }
                }
            }
        }
    }

    private fun timerThatEmitsEverySecond(): Flow<Int> = flow {
        var timeElapsed = 0
        while (isActive) {  // Is Flow still active?
            delay(1000)     // Wait 1 second
            timeElapsed++
            emit(timeElapsed) // Flow emits value
        }
    }
}
```

Do you notice something?
With more blocks we add, the state machine itself gets harder to read, understand, and maintain.
What we are aiming for with FlowRedux and its DSL is to get a readable overview of what the state machine is supposed to do on a high level.
If you take a look at the example above, however, you will notice that it isn't easy
to read and gets bloated with implementation details.

## Extract logic to functions

We recommend keeping the DSL `spec { ... }` block really short, expressive, readable and maintainable.
Therefore, instead of having implementation details in your DSL, we recommend extracting that to functions instead.
Let's refactor the example above to reflect this idea:

```kotlin
class ItemListStateMachineFactory(
    private val httpClient: HttpClient
) : FlowReduxStateMachineFactory<ListState, Action>() {

    // This is the specification of your state machine.
    // Less implementation details, better readability.
    init {
        initializeWith {
            Loading // if creating the initial state would require multiple lines it could be moved to a function as well
        }

        spec {
            inState<Loading> {
                onEnter { loadItemsAndMoveToContentOrError() }
            }

            inState<Error> {
                on<RetryLoadingAction> { action ->
                    // For a single-line statement it's OK to keep logic inside the block instead
                    // of extracting it to a function (but it also depends on your testing strategy)
                    state.override { Loading }
                }

                collectWhileInState(timerThatEmitsEverySecond()) { value  ->
                    decrementCountdownAndMoveToLoading(value)
                }
            }
        }
    }


    //
    // All the implementation details are in the functions below.
    //
    private suspend fun ChangeableState<Loading>.loadItemsAndMoveToContentOrError(): ChangedState<State> {
        return try {
            val items = httpClient.loadItems()
            override { ShowContent(items) }
        } catch (t: Throwable) {
            override { Error(cause = t, countdown = 3) }
        }
    }

    private fun ChangeableState<Error>.decrementCountdownAndMoveToLoading(
        value: Int,
    ): ChangedState<State> {
        return override {
            if (this.countdownTimeLeft > 0)
                this.copy(countdown = countdownTimeLeft - 1)
            else {
                Loading
            }
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

Moreover, have you noticed that the extracted functions now all get a similar method signature:

```kotlin
suspend fun ChangeableState<T>.doSomething() : ChangedState<T>
```

We are now getting closer to [pure functions](https://en.wikipedia.org/wiki/Pure_function).
This makes writing unit tests easier because, for the same input (`ChangeableState`), pure functions return the same output (`ChangedState`).
We will talk about that more in detail in the dedicated section about testing best practices.
