# collectWhileInState()

This one is useful if you want to collect a `Flow` (from Kotlin Coroutines) only while being exactly in that state.
To give a concrete example how this is useful let's extend our `ItemListStateMachineFactory` example.
Let's say whenever our state machine is in `Error` state we want
to retry loading the items after 3 seconds in `Error` state or anytime before the 3 seconds have elapsed if the user clicks the retry button.
Furthermore the 3 seconds countdown timer should be displayed in our UI as well.
This is how the UI should look like:

![Automatically Retry](.../images/error-countdown.gif)

To implement this let's first extend our `Error` state:

```kotlin
data class Error(
    val message: String,
    val countdown: Int // This value is decreased from 3 then 2 then 1 and represents the countdown value.
) : ListState
```

Now let's add some countdown capabilities to our state machine by using `collectWhileInState()`:

```kotlin
class ItemListStateMachineFactory(
    private val httpClient: HttpClient
) : FlowReduxStateMachineFactory<ListState, Action>() {

    init {
        intializeWith { Loading }

        spec {
            inState<Loading> {
                onEnter {
                    // We have discussed this block already in a previous section
                    try {
                        val items = httpClient.loadItems()
                        override { ShowContent(items) }
                    } catch (t: Throwable) {
                        override { Error(
                            message = "A network error occurred",
                            countdown = 3 // countdown is new
                        ) }
                    }
                }
            }

            inState<Error> {
                on<RetryLoadingAction> { action: RetryLoadingAction ->
                    // We have discussed this block already in a previous section
                    override { Loading }
                }

                val timer : Flow<Int> = timerThatEmitsEverySecond()
                collectWhileInState(timer) { timerValue: Int ->
                    // This block triggers every time the timer emits
                    // which happens every second
                    override { // we use .override() because we could move to another type of state
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
        while (isActive) {  // is Flow still active?
            delay(1_000)     // wait 1 second
            timeElapsed++
            emit(timeElapsed) // Flow Emits value
        }
    }
}
```

Let's look at the source code above step by step.
What is new is that `Error` state contains now an additional
field  `countdown : Int` which we set on transitioning from `Loading` to `Error(countdown = 3)` (means 3 seconds left on the countdown clock).

We extend ` inState<Error> { ... }` block and add `collectWhileInState(timer)` block.
`timer` is a `Flow<Int>` that emits a new (incremented) number every second.
`collectWhileInState(timer)` internally calls `.collect {...}` on the flow passed as first parameter (in our case the `timer`).
The second parameter is the a  block with the parameters `timerValue : Int` and `State<Error>`.

In other words: instead of calling `timer.collect { ... }` directly you
call `collectWhileInState(timer) { ... }` to collect the Flow.
FlowRedux then takes care of canceling the flow once the surrounding `inState { ... }` condition doesn't hold anymore. In our case, the timer is automatically canceled once the state machine transitions from
`Error` state into another state.
This happens either when the user clicks on the retry button and which
triggers `on<RetryLoadingAction>` which causes a state transition to `Loading` or when 3 seconds have elapsed (inside `collectWhileInState(timer)`).
