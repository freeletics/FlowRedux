# DSL

FlowRedux provides a convenient DSL to describe your state machine.
This page introduces you the DSL that you can use.

To do that we will stick with a simple example of loading a list of items from a web service.
As you read this section and more concepts of the DSL will be introduced we will extend this sample.

For now to get started, let's define the `States` our state machine has.
As said before we loads a list of items from a web service
and display that list.
While loading the list we show a loading indicator on the screen and
if an error occurs we show an error message on the screen with a retry button.

This gives us the following states:

```kotlin
sealed class State {

    // Shows a loading indicator on screen
    object LoadingState : State()

    // List of items loaded successfully, show it on screen
    data class ShowContentState(val items : List<Item>) : State()

    // Error while loading happened
    data class ErrorState(val cause : Throwable) : State()
}
```

If we reached `ErrorState` we display an error message but also a button a user can click to retry loading the items.
This gives us the following `Actions`:

```kotlin
sealed class Action {
    object RetryLoadingAction : Action()
}
```

## Initial State
Every `FlowReduxStateMachine` needs an initial state.
This is in which state the state machine starts.
In our example we start with the `LoadingState`.

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

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
Next, we already see that we need an `init {...}` block containing a `spec { ... }` block inside.
The `spec { ... }` block is actually where we write our DSL inside.

## inState`<State>`
The first concept we learn is `inState`

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                ...
            }
        }
    }
}
```

Please note that `inState` itself doesn't do anything.
All we did so far with `inState<LoadingState>` is set an entry point.
Next let's discuss what an `inState` can contain as triggers to actually "do something":

1. `onEnter`: Triggers whenever we enter that state
2. `on<Action>`: Triggers whenever we are in this state and the specified action is triggered from the outside by calling `FlowReduxStateMachine.dispatch(action)`.
3. `observeWhileInState( flow )`: You can subscribe to any arbitarry `Flow` while your state machine is in that state.

Let's try to go through them as we build our state machine:

### onEnter
What do we want to do when we enter the `LoadingState`?
We want to do the http request, right?
Let's do that by extending our example:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                onEnter { getState, setState ->
                    // we entered the LoadingState, so let's do the http request
                    try {
                        val items = httpClient.loadItems()
                        setState { ShowContentState(items) }
                    } catch (t : Throwable) {
                        setState { ErrorState(t) }
                    }
                }
            }
        }
    }
}
```

There are a some new things like  `onEnter`, `getState` and `setState`. We will cover [getState](#getstate) and [setState](#setstate) in dedicated sections.
All you have to know about `setState` for now is that this is the way to set the next state in your state machine.
Let's talk about `onEnter`:

- **`onEnter { ... }` is running asynchronously in a coroutine**.
That means whatever you do inside the `onEnter` block is not blocking anything else.
You can totally run here long running and expensive calls (like doing an http request).
- **`onEnter { ... }` doesn't get canceled** when the state machine transitioned to another state original state. Example:
 ```kotlin
 inState<LoadingState> {
    onEnter { getState, setState ->
        setState { ErrorState(Exception("Fake Exception") }
        doA()
        doSomethingLongRunning()
    }
 }
 ```
 `doA()` and `doSomethingLongRunning()` are still executed even if `setState { ... }` which got executed before causes our state machine to move to the next state.
 The takeaway is: the full `onEnter { ... }` block will be executed once a state has been entered (there is an exception, we will talk about that in [FlatMapPolicy](#flatmappolicy) section).

### on`<Action>`
How do we deal with external user input like clicks in FlowRedux? 
This is what Actions are for. 
In FlowRedux DSL you can react on Actions by using a `on<MyAction>{ ... }` block.

In our example we want to retry loading if we are in `ErrorState` and the user clicks on a retry button. 
Clicking on that button dispatches a `RetryLoadingAction` to our state machine.
Let's extend our FlowReduxStateMachine to react on such an action if the current state is `ErrorState`:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                onEnter { getState, setState ->
                    // we entered the LoadingState, so let's do the http request
                    try {
                        val items = httpClient.loadItems()
                        setState { ShowContentState(items) }
                    } catch (t : Throwable) {
                        setState { ErrorState(t) }
                    }
                }
            }
            
            // let's add a new inState{...} with an on{...} block 
            inState<ErrorState> {
               on<RetryLoadingAction> { action, getState, setState ->
                  // This block triggers if we are in ErrorState 
                  // RetryLoadingAction has been dispatched to this state machine.
                  // In that case we transition to LoadingState which then starts the http
                  // request to load items.
                  
                  setState { LoadingState }
               }
            }
        }
    }
}
```

A `on { ... }` block gets 3 parameters:  `action` which is the actual instance of the `Action` that
triggered this block and `setState` and `getState` which you have also seen in `onEnter { ... }` block.
`on { ... }` is actually pretty similar to `onEnter {...}` just with a different "trigger" (action vs. entering a state).
Furthermore, `on { ... }` has the same characteristics as `onEnter { ... }`:

- **`on<MyAction> { ... }` is running asynchronously in a coroutine**.
You can totally run here long running and expensive calls (like doing an http request).
- **`on<MyAction> { ... }` doesn't get canceled** when the state machine transitioned to another state original state.
See [onEnter](#onenter) section for more details.

### observeWhileInState()
This one is useful if you want to collect a `Flow` only while being exactly in that state.
To give a concrete example how this is useful let's extend our example from above.
Let's say whenever our state machine is in `ErrorState` we want to retry loading the items after
3 seconds in `ErrorState` or anytime before the 3 seconds have elapsed if the user clicks the retry button.
Furthermore the 3 seconds countdown timer should be displayed in our app:

To implement this let's first extend our `ErrorState`:

```kotlin
data class ErrorState(
    val cause : Throwable,
    val countdown : Int    // This value is decreased from 3 then 2 then 1 and represents the countdown value.
) : State()
```

Now let's add some countdown capabilities to our state machine by using `observeWhileInState()`:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                onEnter { getState, setState ->
                    // we entered the LoadingState, so let's do the http request
                    try {
                        val items = httpClient.loadItems()
                        setState { ShowContentState(items) }
                    } catch (t : Throwable) {
                        setState { ErrorState(cause = t, countdown = 3) } // Countdown starts with 3 seconds
                    }
                }
            }

            inState<ErrorState> {
               on<RetryLoadingAction> { action, getState, setState ->
                  setState { LoadingState }
               }

               val timer : Flow<Int> = timerThatEmitsEverySecond()
               observeWhileInState(timer) { value, getState, setState ->
                    // This block triggers every time the timer emits
                    // which happens every second
                    val state = getState()
                    if (state is ErrorState) {
                        val countdownTimeLeft = state.countdown
                        if (countdownTimeLeft > 0)
                            setState { state.copy(countdown = countdownTimeLeft - 1) } //  decrease the countdown by 1 second
                        else
                            setState { LoadingState } // transition to the LoadingState
                    }
               }
            }
        }
    }

    private fun timerThatEmitsEverySecond() : Flow<Int> {
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
Whenever we are in `LoadingState` and an error occurs while loading the items we go into
`ErrorState`. Nothing new.
What is new is that `ErrorState` contains an additional field  `countdown` which we set on
transitioning from `LoadingState` to `ErrorState(countdown = 3)` (means 3 seconds left).

We extend ` inState<ErrorState> { ... }` block and add a `observeWhileInState(timer)`.
`timer` is a `Flow<Int>` that emits a new (incremented) number every second.
`observeWhileInState(timer)` calls `.collect {...}` on the timer flow and executes the block with the
parameters `value`, `getState` and `setState` every time `timer` emits a new value.
In other words: instead of calling `timer.collect { ... }` you call `observeWhileInState(timer) { ... }` to collect the Flow's values as long as the state machine is in that state.

But here is the deal: it automatically cancels the timer once the state machine transitioned away from
`ErrorState` into another state.
This happens either when the user clicks on the retry button and causes `on<RetryLoadingAction>` to be dispatched
or when 3 seconds have elapsed.
To keep track how many seconds are left we decrease `ErrorState.countdown` field after every second until we reached zero.
On zero we call `setState { LoadingState }` to do the state transition.

`observeWhileInState(anyFlow) { value, getState, setState -> ... }` has 3 parameters:
`value` is the value emitted by the flow, [getState](#getstate) to get the current state and [setState](#setState) to do a state transition.

In contrast to `onEnter` and `on<Action>` block `observeWhileInState()` block stops the execution once the state machine is not in the original `inState<State>` anymore.

## Custom condition for inState
We already covered `inState<State>` that builds upon the recommended best practice that every State
in your state machine is expressed us it's own type in Kotlin.
Again, this is a best practice and the recommended way.

Sometimes, however, you need a bit more flexibility then just relaying on type.
For that use case you can use `inState(isInState: (State) -> Boolean)`.

Example: One could have also modeled the state for our example above as the following:

```kotlin
// TO MODEL YOUR STATE LIKE THIS IS NOT BEST PRACTICE! Use sealed class instead.
data class State (
    val loading : Boolean, // true means loading, false means not loading
    val items : List<Items>, // empty list if no items loaded yet
    val error : Throwable?, // if not null we are in error state
    val errorCountDown : Int? // the seconds for the error countdown
)
```

**AGAIN, the example shown above is not the recommended way.
We strongly recommend to use sealed classes instead to model state as shown at the beginning of this document.**

We just do this for demo purpose to demonstrate a way how to customize `inState`.
Given the state from above, what we can do now with our DSL is the following:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = State(loading = true, items = emptyList(), error = null, errorCountDown = null)) {

    init {
        spec {
            inState( isInState = {state -> state.loading == true} ) {
                onEnter { getState, setState ->
                    // we entered the LoadingState, so let's do the http request
                    try {
                        val items = httpClient.loadItems()
                        setState {
                            State(loading = false, items = items, error = null, errorCountdown = null)
                        }
                    } catch (t : Throwable) {
                        setState {
                            State(loading = false, items = emptyList(), error = t, errorCountdown = 3)
                        } // Countdown starts with 3 seconds
                    }
                }
            }

            inState( isInState = {state -> state.error != null } ) {
               on<RetryLoadingAction> { action, getState, setState ->
                  setState {
                     State(loading = true, items = emptyList(), error = null, errorCountdown = null)
                  }
               }

               val timer : Flow<Int> = timerThatEmitsEverySecond()
               observeWhileInState(timer) { value, getState, setState ->
                    // This block triggers every time the timer emits
                    // which happens every second
                    val state = getState()
                    val countdownTimeLeft = state.errorCountdown!!
                    if (countdownTimeLeft > 0)
                        setState { state.copy(errorCountdown = countdownTimeLeft - 1) } //  decrease the countdown by 1 second
                    else
                        setState {
                          State(loading = true, items = emptyList(), error = null, errorCountdown = null) // transition to the LoadingState
                        }
                    }
               }
            }
        }
    }
}
```

Instead of `inState<State> { ... }` we can use another version of `inState` name that instead of
generics take a lambda as parameter that looks like `(State) -> Boolean` so that.
If that lambda returns `true` it means we are in that state, otherwise not (returning false).
The rest still remains the same.
You can use `onEnter`, `on<Action>` and `observeWhileInState` the exact way as you already know.

## observe
If for whatever reason you want to trigger a state change out of  `inState<>`, `onEnter { ... }`, `on<Action>` or `observeWhileInState { ... }` by observing a `Flow` then `observe` is what you are looking for:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                onEnter { getState, setState ->
                  ...
                }
            }

            inState<ErrorState> {
               on<RetryLoadingAction> { action, getState, setState ->
                  ...
               }

               observeWhileInState(timer) { value, getState, setState ->
                  ...
               }
            }

            val aFlow : Flow<Int> = flowOf(1,2,3,4)
            observe( aFlow ) { value, getState, setState ->
                // Will trigger anytime flow emits a value
                ...
            }

            observe( anotherFlow ) { value, getState, setState ->
                // Will trigger anytime flow emits a value
               ...
            }
        }
    }
}
```

`observe()` is like `observeWhileInState()` just that it is not bound to the current state like `observeWhileInState()` is.
`observe()` will stop collecting the passed in Flow only if the CoroutineScope of the whole FlowReduxStateMachine gets canceled.


## SetState
As you probably have already noticed from the sections above `setState` is a way to make you state machine transition to another state.
You can think of `SetState` as a function that gets the current state as input parameter and returns
the new State:

```kotlin
fun setState (currentState : State) : State {
    val newState : State =  ... // compute new state somehow
    return newState
}
```

Due to some kotlin language restriction `SetState` is actually a `class` and not just a function but that doesn't have to bother you as it is an implementation detail of FlowRedux.
You can totally think of it as a function `(State) -> State`.

One important thing you have to know about FlowRedux in general is that FlowRedux is running async.
That means that there might be multiple `setState { ... }` in the queue trying to change the state (but only 1 SetState will be actually executed at the same time).

This means that it is possible that on `setState { ... }` execution the state is actually not in the same state anymore as you would have expected.
Per default FlowRedux will check if the state is still the expected one and if it isn't `setState {...}` wont run.

If you want to override this behavior you can do that by providing an additional parameter `runIf` and return true if FlowRedux should run setState, otherwise false.

```kotlin
setState(runIf = { currentState -> currentState is FooState }) { currentState ->
    // executed only if runIf returns true
    OtherState
}
```

For example if you want to force setState to run always you can do the following:

```kotlin
setState(runIf = { true }) { currentState ->
    OtherState
}
```

## GetState
If you need in any block the current state `GetState` is your friend.
It is actually just a function that you can invoke that returns you the current state of your state machine.

## FlatMapPolicy
Have you ever wondered what would happen if you would execute `Action` very fast 1 after another?
For example:

```kotlin
spec {
    inState<FooState> {
        on<BarAction> { _, _ , setState ->
            delay(5000) // wait for 5000 seconds
            setState { OtherState }
        }
    }
}
```

The example above shows a problem with async. state machines like FlowRedux:
If we our state machine is in `FooState` and a `BarAction` got triggered, we wait for 5 seconds and then set the state to another state.
What if while waiting 5 seconds (i.e. let's say after 3 seconds of waiting) another `BarAction` gets triggered.
That is possible right?
With `FlatMapPolicy` you can specify what should happen in that case.
There are three options to choose from:

- `LATEST`: This is the default one. It would cancel any previous execution and just run the latest one.
In the example above it would mean while waiting 5 seconds another `BarAction` gets triggered, the first execution of `on<BarAction>` block gets stopped and a new `on<BarAction>` block starts.
- `MERGE`: Choosing this causes all the blocks to continue running but there are no guarantees in which order. For example:
```kotlin
spec {
    inState<FooState> {
        on<BarAction>(flatMapPolicy = FlapMapPolicy.MERGE) { _, _ , setState ->
            delay( randomInt() ) // wait for some random time
            setState { OtherState }
        }
    }
}
```
Let's assume that  we trigger two times `BarAction`.
We use random amount of seconds for waiting.
Since we use `MERGE` `on<BarAction>` block gets executed 2 times without canceling the previous one (that is the difference to `LATEST`).
Moreover, `MERGE` doesn't make any promise on order of execution of the block (see `CONCAT` if you need promises on order).
So if `on<BarAction>` gets executed two times it will run in parallel and the the second execution could complete before the first execution (because using a random time of waiting).
- `CONCAT`: In contrast to `MERGE` and `LATEST` `CONCAT` will not run `on<BarAction>` in parallel and will not cancel any previous execution. Instead, `CONCAT` will preserve the order and execute one block after another.


All execution blocks can specify a `FlatMapPolicy`:

 - `on<Action>(flatMapPolicy = FlatMapPolicy.LATEST){... }`
 - `onEnter(flatMapPolicy = FlatMapPolicy.LATEST) { ... }`
 - `observeWhileInState(flatMapPolicy = FlatMapPolicy.LATEST) { ... }`

## Best Practice
One very important aspect of the DSL is to provide a readable and maintainable way to reason about your state machine.
Let' take a look at our example state machine:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                onEnter { getState, setState ->
                    // we entered the LoadingState, so let's do the http request
                    try {
                        val items = httpClient.loadItems()
                        setState { ShowContentState(items) }
                    } catch (t : Throwable) {
                        setState { ErrorState(cause = t, countdown = 3) } // Countdown starts with 3 seconds
                    }
                }
            }

            inState<ErrorState> {
               on<RetryLoadingAction> { action, getState, setState ->
                  setState { LoadingState }
               }

               val timer : Flow<Int> = timerThatEmitsEverySecond()
               observeWhileInState(timer) { value, getState, setState ->
                    // This block triggers every time the timer emits
                    // which happens every second
                    val state = getState()
                    if (state is ErrorState) {
                        val countdownTimeLeft = state.countdown
                        if (countdownTimeLeft > 0)
                            setState { state.copy(countdown = countdownTimeLeft - 1) } //  decrease the countdown by 1 second
                        else
                            setState { LoadingState } // transition to the LoadingState
                    }
               }
            }
        }
    }

    private fun timerThatEmitsEverySecond() : Flow<Int> {
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
What we are aiming for with the DSL is an overview about what the state machine is supposed to do on a high level that reads like as specification.
If you take a look at the example from above, however, you will notice that it isn't easy to read and get bloated with implementation details.

### The recommended way
We recommend to keep the DSL really short, expressive, readable and maintainable.
Therefore instead of having implementation details in your DSL we recommend to use function references instead.
Let's refactor the example above to reflect this idea:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    //
    // This is the specification of your state machine. Less implementation details, better readability.
    //
    init {
        spec {
            inState<LoadingState> {
                onEnter(::loadItemsAndMoveToContentOrErrorState)
            }

            inState<ErrorState> {
               on<RetryLoadingAction> { _, _, setState ->
                  // For a single line statement it's ok to keep the block instead of moving to a function reference
                  setState { LoadingState }
               }

               observeWhileInState(
                    timerThatEmitsEverySecond(),
                    ::onSecondElapsedMoveToLoadingStateOrMoveToDecrementCountdown
               )
            }
        }
    }


    //
    // All the implementation details are in the functions below.
    //

    private fun loadItemsAndMoveToContentOrErrorState(getState : StateAccessor<State>, setState : SetState<State>){
        try {
            val items = httpClient.loadItems()
            setState { ShowContentState(items) }
        } catch (t : Throwable) {
            setState { ErrorState(cause = t, countdown = 3) } // Countdown starts with 3 seconds
        }
    }

    private fun onSecondElapsedMoveToLoadingStateOrMoveToDecrementCountdown(
        value : Int,
        getState : StateAccessor<State>,
        setState : SetState<State>
    ){
        val state = getState()
        if (state is ErrorState) {
            val countdownTimeLeft = state.countdown
            if (countdownTimeLeft > 0)
                setState { state.copy(countdown = countdownTimeLeft - 1) } //  decrease the countdown by 1 second
            else
                setState { LoadingState } // transition to the LoadingState
        }
    }

    private fun timerThatEmitsEverySecond() : Flow<Int> {
        var timeElapsed = 0
        while (isActive) {  // is Flow still active?
            delay(1000)     // wait 1 second
            timeElapsed++
            emit(timeElapsed) // Flow Emits value
        }
    }
}
```

By using function references you can read the DSL better and can zoom in into implementation
details anytime you want to by looking into a function body.