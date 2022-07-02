# Getting started

FlowRedux provides a convenient DSL to describe your state machine.
This page introduces you the DSL that you can use. 
Furthermore, this document is meant to be read from top to bottom.

To do that we will stick with a simple example of loading a list of items from a web service. 
As you read this section and more concepts of the DSL will be introduced we will extend this example with more features.

For now to get started, let's define the `States` our state machine has. As said before we load a list of items from a
web service and display that list. While loading the list we show a loading indicator on the screen and if an error
occurs we show an error message on the screen with a retry button.

This gives us the following states:

```kotlin
sealed interface ListState {

    // Shows a loading indicator on screen
    object Loading : ListState

    // List of items loaded successfully, show it on screen
    data class ShowContent(val items: List<Item>) : ListState

    // Error while loading happened
    data class Error(val cause: Throwable) : ListState
}
```

If the state machine reaches `Error`  state then we display an error message in our UI but also a button a user of our app can click to retry loading the items.

This gives us the following `Actions`:

```kotlin
sealed interface Action {
    object RetryLoadingAction : Action
}
```

## Initial State

Every `FlowReduxStateMachine` needs an initial state. 
This specifies in which state the state machine starts. 
In our example we start with the `Loading`.

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : ListState, Action>(initialState = Loading) {

    init {
        spec {
            // will be filled in next section
            ...
        }
    }
}
```

Please note the constructor parameter of `FlowReduxStateMachine(initialState = ...)`. 
This is how you define the initial state of your state machine. 
Next, we already see that we need an `init {...}` block containing a `spec { ... }` block
inside. 
The `spec { ... }` block is actually where we write our state machine specification by using our DSL.


## inState`<State>`

The first concept we learn is `inState`

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : ListState, Action>(initialState = Loading) {

    init {
        spec {
            inState<Loading> {
                ...
            }
        }
    }
}
```

Please note that `inState` itself doesn't do anything. 
All we did so far with `inState<Loading>` is set an "entry point".
Next let's discuss what an `inState` block can contain as triggers to actually "do something":

1. `onEnter`: Triggers whenever we enter that state
2. `on<Action>`: Triggers whenever we are in this state and the specified action is triggered from the outside by
   calling `FlowReduxStateMachine.dispatch(action)`.
3. `collectWhileInState( flow )`: You can subscribe to any arbitrary `Flow` while your state machine is in that state.

Additionally `onEnterStartStateMachine()` and `onActionStartStateMachine()` can be placed inside an `inState{ ... }` block, but we will talk about this advanced concepts that are useful for composing business logic later.

Let's take a more closer look at 3 basic elements `onEnter`, `on<Action>` and `collectWhileInState`  as we build our state machine. 

## onEnter

What do we want to do when we enter the `Loading`? We want to make the http request to load the items from our server, right? 
Let's specify that with the DSL in our state machine:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : ListState, Action>(initialState = Loading) {

    init {
        spec {
            inState<Loading> {
                onEnter { state: State<Loading> ->
                    // we entered the Loading state, 
                    // so let's do the http request
                    try {
                        val items = httpClient.loadItems()  // loadItems() is a suspend function
                        state.override { ShowContent(items) }  // return ShowContent from onEnter block
                    } catch (t: Throwable) {
                        state.override { Error(t) }   // return Error state from onEnter block
                    }
                }
            }
        }
    }
}
```

There are a some new things like  `onEnter` and `State<T>`. 
We will covered `State<T>` in the next section. 

Let's talk about `onEnter`:

- **`onEnter { ... }` is running asynchronously in a coroutine**. That means whatever you do inside the `onEnter` block
  is not blocking anything else. You can totally run here long-running and expensive calls (like doing a http request).
- **`onEnter { ... }` expects a lambda (or function) with the following
  signature: `onEnter( (State<T>) -> ChangedState<T> )`**. We will cover that in detail in the next section.
- **`onEnter { ... }` is executed exactly once when the surrounding `inState<T>` condition is met**. 
  It will only executed the next time when the state machine transitions out of the current state and back to it again.
- **The execution of the `onEnter { ... }` is canceled as soon as state condition specified in the surrounding `inState`
doesn't hold anymore i.e. state has been changes by some other block of the DSL else.
Recall that FlowRedux is a multi-threaded asynchronous state machine. We will talk about that later.

The key takeaway here is that with `onEnter { ... }` you can do some work and then move on to another state by calling `State.override()` or `State.mutate()`

To be able to fully understand the code snipped from above, let's take a look at `State<T>` and `ChangedState<T>`.

## State`<T>` and ChangedState`<T>`
FlowRedux has the concept of a `State<T>` (please note that `T` here is just a placeholder for generics).
It is used as a parameter for many DSL blocks like `onEnter { state : State<MyState> }` etc.
With this `State<T>` object you can get access to the actual state of your statemachine with `State.snapshot`.
Additionally `State<T>` is providing functions to mutate the state or completely override it.
Here is a summary of the API of `State<T>` (simplified version, we will dive deeper in a bit):

```kotlin
class State<T> {
    // This holds the state value of your state machine
    val snapshot : T  

    // completely replaces the current state with a new one
    fun override(newState : T) : ChangedState<T>  

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
 - use `mutate()` if you want to change just some properties of the current state but stay in the same state class.

Examples:
```kotlin
spec {

    // DO USE .override() to clearly say you want to move to another type of state
    inState<Loading>{
        onEnter{ state : State<Loading>  ->
            state.override { Error() } 
        }
    }

    // DO NOT USE .mutate() 
    inState<Loading>{
        onEnter{ state : State<Loading>  ->
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
        onEnter { state : State<ScreenStatisticsState> -> 
            state.mutate { this.copy(visitCounter= this.visitCounter + 1) }
        }
    }

    // DO NOT USE .override() as you don't want to move to another type of state
    inState<ScreenStatisticsState> {
        onEnter { state : State<ScreenStatisticsState> -> 
            state.override { 
                this.copy(visitCounter= this.visitCounter + 1) // compiles but hard to read
            } 
        }
    }
}
```


As you see from a `State<T>` you can produce a `ChangedState`. 
`ChangedState` is something that simply tells FlowRedux internally how the reducer of the FlowReduxStateMachine should merge and compute the next state of your statemachine.
`ChangeState` is not meant to be used or instantiated by you manually. 
You may wonder "what about writing unit tests?". 
We will cover testing and best practices in a [dedicated section](/testing).

We will dive deeper on `State.override()` and `State.mutate()` as we continue with our `ItemListStateMachine` example. 

### on`<Action>`

How do we deal with external user input like clicks in FlowRedux? 
This is what `Action` is for. 
With the DSL of FlowRedux you can specify what should be done when a certain `Action` (triggered by the user) happened. 

In our example we want to retry loading if we are in `Error` state. In the `Error´ state our UI shows a error text and a button the user can click on to retry loading the list of items. 
Clicking on that button dispatches a `RetryLoadingAction` to our state machine. 
Let's extend our `ItemListStateMachine` to react on such an action:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : ListState, Action>(initialState = Loading) {

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

A `on { ... }` block gets 2 parameters:  `action` which is the actual instance of the `Action` that triggered this block
and `state : State<T>` which gives us access to the current state and let us to state transitions with `.override()`.
`on { ... }` is actually pretty similar to `onEnter {...}` just with a different "trigger" (action vs. entering a state)
. Furthermore, `on { ... }` has the same characteristics as `onEnter { ... }`:

- **`on { ... }` is running asynchronously in a coroutine**. That means whatever you do inside the `on` block is not
  blocking anything else. You can totally run here long-running and expensive calls (like doing a http request).
- **`on { ... }` expects a lambda (or function) with the following
  signature: `(action : Action , state : State<T>) -> ChangeState<T>`**.
- **The execution of the `on { ... }` is canceled as soon as state condition specified in the surrounding `inState` block
  doesn't hold anymore (i.e. state has been changes by something else).**

### collectWhileInState()

This one is useful if you want to collect a `Flow` (from Kotlin Coroutines) only while being exactly in that state. 
To give a concrete example how this is useful let's extend our `ItemListStateMachine` example. 
Let's say whenever our state machine is in `Error` state we want
to retry loading the items after 3 seconds in `Error` state or anytime before the 3 seconds have elapsed if the user clicks the retry button. 
Furthermore the 3 seconds countdown timer should be displayed in our UI as well:

To implement this let's first extend our `Error` state:

```kotlin
data class Error(
    val cause: Throwable,
    val countdown: Int    // This value is decreased from 3 then 2 then 1 and represents the countdown value.
) : State()
```

Now let's add some countdown capabilities to our state machine by using `collectWhileInState()`:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : ListState, Action>(initialState = Loading) {

    init {
        spec {
            inState<Loading> {
                onEnter { state: State<Loading> ->
                    // We have discussed this block already in a previous section
                    try {
                        val items = httpClient.loadItems()
                        state.override { ShowContent(items) }  
                    } catch (t: Throwable) {
                        state.override { Error(t, countdown = 3) }   // countdown is new
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

Let's look at the source code above step by step. 
What is new is that `Error` state contains now an additional
field  `countdown : Int` which we set on transitioning from `Loading` to `Error(countdown = 3)` (means 3 seconds left on the countdown clock).

We extend ` inState<Error> { ... }` block and add `collectWhileInState(timer)` block.
`timer` is a `Flow<Int>` that emits a new (incremented) number every second.
`collectWhileInState(timer)` internally calls `.collect {...}` on the flow passed as first parameter (in our case the `timer`). 
The second parameter is the a  block with the parameters `timerValue : Int` and `State<Error>`. 

In other words: instead of calling `timer.collect { ... }` directly you
call `collectWhileInState(timer) { ... }` to collect the Flow. 
FlowRedux then takes care of canceling the flow once the surrounding `inState{ ... }` condition doesn't hold anymore. In our case, the timer is automatically canceled once the state machine transitions from
`Error` state into another state. 
This happens either when the user clicks on the retry button and which
triggers `on<RetryLoadingAction>` which causes a state transition to `Loading` or when 3 seconds have elapsed (inside `collectWhileInState(timer)`). 

## Effects
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
class ItemListStateMachine : ListState, Action>(initialState = Loading) {

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

## Custom condition for inState

We already covered `inState<State>` that builds upon the recommended best practice that every State of your state machine is expressed us it's own type in Kotlin. 

Sometimes, however, you need a bit more flexibility than just relaying on types to model state. 
For that use case you can use `inStateWithCondition(isInState: (State) -> Boolean)`.

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
) : ListState, Action>(
    initialState = State(
        loading = true,
        items = emptyList(),
        error = null,
        errorCountDown = null
    )
) {

    init {
        spec {
            inStateWithCondition(isInState = { state -> state.loading == true }) {
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

            inStateWithCondition(isInState = { state -> state.error != null }) {
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
```

Instead of `inState<State> { ... }` we can use `inStateWithCondition` instead. 
It takes a lambda as parameter that looks like `(State) -> Boolean` so that. 
If that lambda returns `true` it means we are in that state, otherwise not (returning false). 
The rest still remains the same. 
You can use `onEnter`, `on<Action>` and `collectWhileInState` the exact way as you already know. 
However, since `inStateWithCondition` has no generics, FlowRedux cannot infer types in `onEnter`, `on`, etc.

## Acting across multiple states

If for whatever reason you want to trigger a state change for all states you can achieve that by
using `inState<>` on a base class.

```kotlin
// DSL specs
spec {
    inState<ListState> {
        // on, onEnter, collectWhileInState for all states because
        // ListState is the base class these would never get cancelled
    }

    inState<Loading> {
        // on, onEnter, collectWhileInState specific to Loading state
    }

    inState<ShowContent> {
        // on, onEnter, collectWhileInState specific to ShowContent state
    }
}
```

In case you want to trigger state changes from a subset of states you could introduce another
level to your state class hierarchy. For example the following would allow you to have a
`inState<PostLoading>` block to share actions between `ShowContent` and `Error`:

```kotlin
sealed interface ListState {

    // Shows a loading indicator on screen
    object Loading : ListState

    sealed interface PostLoading : ListState

    // List of items loaded successfully, show it on screen
    data class ShowContent(val items: List<Item>) : PostLoading

    // Error while loading happened
    data class Error(val cause: Throwable) : PostLoading
}
```


## ExecutionPolicy

Have you ever wondered what would happen if you would execute `Action` very fast 1 after another? 
For example:

```kotlin
spec {
    inState<FooState> {
        on<BarAction> { action, state : State<FooState> ->
            delay(5000) // wait for 5 seconds
            state.override { OtherState() }
        }
    }
}
```

The example above shows a problem with async. state machines like FlowRedux:
If our state machine is in `FooState` and a `BarAction` got triggered, we wait for 5 seconds and then set the state to another state. 
What if while waiting 5 seconds (i.e. let's say after 3 seconds of waiting) another `BarAction` gets
triggered. 
That is possible, right? 
With `ExecutionPolicy` you can specify what should happen in that case. 
There are three options to choose from:

- `CANCEL_PREVIOUS`: **This is the default one automatically applied unless specified otherwise.** It would cancel any previous execution and just run the latest one. In the example mentioned it means the previous still running `BarAction` handler gets canceled and a new one with the laster `BarAction` starts.
- `UNORDERED`: Choosing this causes all the blocks to continue running but there are no guarantees in which order. For example:

```kotlin
spec {
    inState<FooState> {
        on<BarAction>(executionPolicy = FlapMapPolicy.UNORDERED) { _, state : State<FooState> ->
            delay(randomInt()) // wait for some random time
            state.override { OtherState }
        }
    }
}
```

Let's assume that we trigger two times `BarAction`. 
We use random amount of seconds for waiting. 
Since we use `UNORDERED` as policy `on<BarAction>` the handler block gets executed 2 times without canceling the previous one (that is the difference  to `CANCEL_PREVIOUS`). 
Moreover, `UNORDERED` doesn't make any promise on order of execution of the block (see `ORDERED` if you need promises on order). 
If `on<BarAction>` gets executed two times it will run in parallel and the the second execution
could complete before the first execution (because using a random time of waiting).

- `ORDERED`: In contrast to `UNORDERED` and `CANCEL_PREVIOUS`, `ORDERED` will not run `on<BarAction>` in parallel and will not cancel any previous execution. Instead, `ORDERED` will preserve the order.

`on<Action>` and `collectWhileInstate()` can specify an `ExecutionPolicy`:

- `on<Action>(executionPolicy = ExecutionPolicy.CANCEL_PREVIOUS) { ... }`
- `collectWhileInState(executionPolicy = ExecutionPolicy.CANCEL_PREVIOUS) { ... }`

Please note that `onEnter` doesn't have the option to specify `ExecutionPolicy`.

## Best practice: make your DSL spec readable

One very important aspect of the DSL is to provide a readable and maintainable way to reason about your state machine.
Let' take a look at our example state machine:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : ListState, Action>(initialState = Loading) {

    init {
        spec {
            inState<Loading> {
                onEnter { state: State<Loading> ->
                    // We have discussed this block already in a previous section
                    try {
                        val items = httpClient.loadItems()
                        state.override { ShowContent(items) }  
                    } catch (t: Throwable) {
                        state.override { Error(t, countdown = 3) }   // countdown is new
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
With more blocks we add the state machine itself gets harder to read, understand and maintain.
What we are aiming for with FlowRedux and it's DSL is to get a readable overview about what the state machine is supposed to do on a high level. 
If you take a look at the example from above, however, you will notice that it isn't easy
to read and get bloated with implementation details.

### Extract logic to functions

We recommend keeping the DSL really short, expressive, readable and maintainable. 
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
                    // For a single line statement it's ok to keep the block instead of moving to a function reference
                    state.override { Loading }
                }

                collectWhileInState(timerThatEmitsEverySecond()) { value, state  ->
                    onSecondElapsedMoveToLoadingOrDecrementCountdown(value, state)
                }                
            }
        }
    }


    //
    // All the implementation details are in the functions below.
    //
    private fun loadItemsAndMoveToContentOrError(state: State<Loading>): ChangedState<State> {
        return try {
            val items = httpClient.loadItems()
            state.override { ShowContent(items) } 
        } catch (t: Throwable) {
            state.override { Error(cause = t, countdown = 3) } 
        }
    }

    private fun onSecondElapsedMoveToLoadingOrDecrementCountdown(
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
        while (isActive) {  // is Flow still active?
            delay(1000)     // wait 1 second
            timeElapsed++
            emit(timeElapsed) // Flow Emits value
        }
    }
}
```

