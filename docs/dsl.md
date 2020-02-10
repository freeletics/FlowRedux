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

## inState
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


## FlatMapPolicy
