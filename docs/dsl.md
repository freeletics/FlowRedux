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
                    state.override {  currentState : Error ->               
                        if (currentState.countdown > 0)     
                            currentState.copy(countdown = currentState.countdown - 1) // decrease the countdown by 1 second
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
triggering google analytics or trigger navigation then Effects is what you are looking for.

The following counterparts to `on<Action>`, `onEnter` and `collectWhileInState` exists:

- `onActionEffect<Action>`: Like `on<Action>` this triggers whenever the described Action is dispatched.
- `onEnterEffect`: Like `onEnter` this triggers whenever you enter the state.
- `collectWhileInStateEffect`: Like `collectWhileInState` this is used to collect a `Flow`.


Effects behave the same way as their counterparts, i.e. cancelation etc. works just the same way as described in the section of `on<Action>`, `onEnter` and `collectWhileInState`.Effects

Usage:
```kotlin
class ItemListStateMachine : ListState, Action>(initialState = Loading) {

    init {
        spec {
            inState<ShowContent> {
               onEnterEffect { stateSnapshot ->
                   logMessage("Did enter $state") // note there is no state change
               }

               onActionEffect<ButtonClickedAction> { action, stateSnapshot ->
                    analyticsTracker.track(ButtonClickedEvent()) // note there is no state change
               }

               collectWhileInStateEffect(someFlow) {value, stateSnapshot ->
                    logMessage("Collected $value from flow while in state $stateSnapshot") // note there is no state change
               }
            }

        }
    }
}
```

## Custom condition for inState

We already covered `inState<State>` that builds upon the recommended best practice that every State in your state
machine is expressed us it's own type in Kotlin. Again, this is a best practice and the recommended way.

Sometimes, however, you need a bit more flexibility then just relaying on type. For that use case you can
use `inStateWithCondition(isInState: (State) -> Boolean)`.

Example: One could have also modeled the state for our example above as the following:

```kotlin
// TO MODEL YOUR STATE LIKE THIS IS NOT BEST PRACTICE! Use sealed class instead.
data class State(
    val loading: Boolean, // true means loading, false means not loading
    val items: List<Items>, // empty list if no items loaded yet
    val error: Throwable?, // if not null we are in error state
    val errorCountDown: Int? // the seconds for the error countdown
)
```

**AGAIN, the example shown above is not the recommended way. We strongly recommend to use sealed classes instead to
model state as shown at the beginning of this document.**

We just do this for demo purpose to demonstrate a way how to customize `inState`. Given the state from above, what we
can do now with our DSL is the following:

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
                onEnter { stateSnapshot: State ->
                    // we entered the Loading, so let's do the http request
                    try {
                        val items = httpClient.loadItems()
                        OverrideState(
                            State(loading = false, items = items, error = null, errorCountdown = null)
                        )
                    } catch (t: Throwable) {
                        OverrideState(
                            State(loading = false, items = emptyList(), error = t, errorCountdown = 3)
                        ) // Countdown starts with 3 seconds
                    }
                }
            }

            inStateWithCondition(isInState = { state -> state.error != null }) {
                on<RetryLoadingAction> { action, stateSnapshot ->
                    OverrideState(
                        State(loading = true, items = emptyList(), error = null, errorCountdown = null)
                    )
                }

                collectWhileInState(timerThatEmitsEverySecond()) { value, stateSnapshot ->
                    MutateState<State, State> {
                        if (errorCountdown!! > 0)
                            copy(errorCountdown = errorCountdown!! - 1) //  decrease the countdown by 1 second
                        else
                            State(
                                loading = true,
                                items = emptyList(),
                                error = null,
                                errorCountdown = null
                            ) // transition to the Loading
                    }
                }
            }
        }
    }
}
```

Instead of `inState<State> { ... }` we can use `inStateWithCondition` that instead of generics take a lambda as
parameter that looks like `(State) -> Boolean` so that. If that lambda returns `true` it means we are in that state,
otherwise not (returning false). The rest still remains the same. You can use `onEnter`, `on<Action>`
and `collectWhileInState` the exact way as you already know. However, since `inStateWithCondition` has no generics,
FlowRedux cannot infer types in `onEnter`, `on`, etc.

## Acting across multiple states

If for whatever reason you want to trigger a state change for all states you can achieve that by
using `inState<>` on a base class.

```kotlin
// DSL specs
spec {
    inState<State> {
        // on, onEnter, collectWhileInState for all states
        // because State is the base class these would never get cancelled
    }

    inState<Loading> {
        // on, onEnter, collectWhileInState specific to Loading
    }

    inState<ShowContent> {
        // on, onEnter, collectWhileInState specific to ShowContent
    }
}
```

In case you want to trigger state changes from a subset of states you could introduce another
level to your state class hierarchy. For example the following would allow you to have a
`inState<PostLoading>` block to share actions between `ShowContent` and `Error`:

```kotlin
sealed class State {

    // Shows a loading indicator on screen
    object Loading : State()

    sealed class PostLoading : State()

    // List of items loaded successfully, show it on screen
    data class ShowContent(val items: List<Item>) : PostLoading()

    // Error while loading happened
    data class Error(val cause: Throwable) : PostLoading()
}
```


## ExecutionPolicy

Have you ever wondered what would happen if you would execute `Action` very fast 1 after another? For example:

```kotlin
spec {
    inState<FooState> {
        on<BarAction> { action, stateSnapshot ->
            delay(5000) // wait for 5 seconds
            OverrideState(OtherState())
        }
    }
}
```

The example above shows a problem with async. state machines like FlowRedux:
If our state machine is in `FooState` and a `BarAction` got triggered, we wait for 5 seconds and then set the state to
another state. What if while waiting 5 seconds (i.e. let's say after 3 seconds of waiting) another `BarAction` gets
triggered. That is possible right? With `ExecutionPolicy` you can specify what should happen in that case. There are three
options to choose from:

- `CANCEL_PREVIOUS`: This is the default one. It would cancel any previous execution and just run the latest one. In the example
  above it would meanwhile wait 5 seconds another `BarAction` gets triggered, the first execution of `on<BarAction>`
  block gets stopped and a new `on<BarAction>` block starts.
- `UNORDERED`: Choosing this causes all the blocks to continue running but there are no guarantees in which order. For
  example:

```kotlin
spec {
    inState<FooState> {
        on<BarAction>(executionPolicy = FlapMapPolicy.UNORDERED) { _, _, setState ->
            delay(randomInt()) // wait for some random time
            setState { OtherState }
        }
    }
}
```

Let's assume that we trigger two times `BarAction`. We use random amount of seconds for waiting. Since we
use `UNORDERED` `on<BarAction>` block gets executed 2 times without canceling the previous one (that is the difference
to `CANCEL_PREVIOUS`). Moreover, `UNORDERED` doesn't make any promise on order of execution of the block (see `ORDERED` if you need
promises on order). So if `on<BarAction>` gets executed two times it will run in parallel and the the second execution
could complete before the first execution (because using a random time of waiting).

- `ORDERED`: In contrast to `UNORDERED` and `CANCEL_PREVIOUS` `ORDERED` will not run `on<BarAction>` in parallel and will not cancel
  any previous execution. Instead, `ORDERED` will preserve the order.

All execution blocks except `onEnter` can specify a `ExecutionPolicy`:

- `on<Action>(executionPolicy = ExecutionPolicy.
){... }`
- `collectWhileInState(executionPolicy = ExecutionPolicy.CANCEL_PREVIOUS) { ... }`

## Best Practice

One very important aspect of the DSL is to provide a readable and maintainable way to reason about your state machine.
Let' take a look at our example state machine:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : ListState, Action>(initialState = Loading) {

    init {
        spec {
            inState<Loading> {
                onEnter { stateSnapshot ->
                    // we entered the Loading, so let's do the http request
                    try {
                        val items = httpClient.loadItems()
                        OverrideState(ShowContent(items))
                    } catch (t: Throwable) {
                        OverrideState(Error(cause = t, countdown = 3)) // Countdown starts with 3 seconds
                    }
                }
            }

            inState<Error> {
                on<RetryLoadingAction> { action, stateSnapshot ->
                    OverrideState(Loading)
                }

                collectWhileInState(timerThatEmitsEverySecond()) { value, stateSnapshot ->
                    MutateState<Error, State> {
                        if (this.countdownTimeLeft > 0) // this is referencing Error
                            this.copy(countdown = countdownTimeLeft - 1)  //  decrease the countdown by 1 second
                        else
                            Loading  // transition to the Loading
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

Do you notice something? With more blocks we add the state machine itself gets harder to read, understand and maintain.
What we are aiming for with the DSL is an overview about what the state machine is supposed to do on a high level that
reads like as specification. If you take a look at the example from above, however, you will notice that it isn't easy
to read and get bloated with implementation details.

### The recommended way

We recommend keeping the DSL really short, expressive, readable and maintainable. Therefore instead of having
implementation details in your DSL we recommend to use function references instead. Let's refactor the example above to
reflect this idea:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<ListState, Action>(initialState = Loading) {

    //
    // This is the specification of your state machine. Less implementation details, better readability.
    //
    init {
        spec {
            inState<Loading> {
                onEnter(::loadItemsAndMoveToContentOrError)
            }

            inState<Error> {
                on<RetryLoadingAction> { action, stateSnapshot ->
                    // For a single line statement it's ok to keep the block instead of moving to a function reference
                    OverrideState(Loading)
                }

                collectWhileInState(
                    timerThatEmitsEverySecond(),
                    ::onSecondElapsedMoveToLoadingOrDecrementCountdown
                )
            }
        }
    }


    //
    // All the implementation details are in the functions below.
    //

    private fun loadItemsAndMoveToContentOrError(stateSnapshot: Loading): ChangeState<State> {
        return try {
            val items = httpClient.loadItems()
            OverrideState(ShowContent(items))
        } catch (t: Throwable) {
            OverrideState(Error(cause = t, countdown = 3)) // Countdown starts with 3 seconds
        }
    }

    private fun onSecondElapsedMoveToLoadingOrDecrementCountdown(
        value: Int,
        stateSnapshot: Error
    ): ChangeState<State> {
        return MutateState<Error, State> {
            if (this.countdownTimeLeft > 0) // this is referencing Error
                this.copy(countdown = countdownTimeLeft - 1)  //  decrease the countdown by 1 second
            else
                Loading  // transition to the Loading
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

By using function references you can read the DSL better and can zoom in into implementation details anytime you want to
by looking into a function body.






## ChangeState

One key concept of the FlowRedux DSL is that the return type of every function such as `onEnter`, `onAction`
and `collectWhileInState`
(we will learn about them later) is of type `ChangeState<State>`. For example:

```kotlin
suspend fun handleLoadingAction(stateSnapshot: State): ChangeState<State> {
    val items = loadItems() // suspend function
    return OverrideState(ShowContent(items)) // OverrideState extends from ChangeState. We will talk about it in 1 minutes.
}
```

As the name suggests `ChangeState<State>` is the way to tell the Redux store what the next state is or how to "compute"
the next state(often also called reduce state).
`ChangeState` is a sealed class. You never use `ChangeState` directly but only one of the subtypes. There are 3 subtypes
that cover different use cases:

- `OverrideState<S>(nextState : State)`
- `MutateState<SubState, State>( reducer: (currentState : SubState) -> State )`
- `NoStateChange`

Next, let's talk about these 3 types of `ChangeState` in detail.

### `OverrideState<S>(nextState : State)`

`OverrideState<S>(nextState : State)` is overriding the current state of your redux store to whatever you pass in as
parameter. By using `OverrideState` you basically say "I don't care what the current state is, just set state to
whatever I give you". This is used if computing the next state is independent of current state. You literally override
the state regardless of what the current state is.

Usage:

```kotlin
suspend fun handleFooAction(action: Action, stateSnapshot: State): ChangeState<State> {
    ...
    return OverrideState(SomeOtherState())
}
```

### `MutateState<SubState, State>( reducer: (currentState : SubState) -> State )`

`MutateState<SubState, State>( reducer: (currentState : SubState) -> State )` is used if your next state computation is
based on the current state. Thus, `MutateState` expects a lambda block with signature `(State) -> State`. This is a
so-called reducer function to compute the next state. You may wonder why `MutateState` takes such a lambda as
constructor parameter and not a new state instance as `OverrideState` does? The reason is that FlowRedux is an
asynchronous state machine meaning multiple coroutines can run in parallel and mutate state. Here is a very simple
example (we will discuss the exact details of the used DSL later in this documentat):

```kotlin
suspend fun createRandomItem(): Item {
    val random = (0..10).random()
    delay(random * 1000) // randomly wait for up to 10 seconds
    return Item("random $random")
}

...
// DSL specs
spec {
    inState<ShowContent> {
        on<AddItemAction> { action: AddItemAction, state: ShowContent ->
            val item: Item = createRandomItem()
            MutateState { currentState: ShowContent -> currentState.copy(items = currentState.items + item) }
        }
    }
}
```

As you see `createRandomItem()` can take some time to return a value. Furthermore, every invocation
of `createRandomItem()` could take differently long to return a value. Let's take a look at the following scenario:
current state of our FlowRedux state machine is `ShowContent(items = listof( Item("1"), Item("2")))`. Next we
trigger `AddItemAction` 2 times very fast (within a few milliseconds) one after each other which eventually
triggers `createRandomItem()` 2 times. Let's assume the first invocation of `createRandomItem()` takes 6 seconds to
return new item, the second invocation takes 3 seconds. What is the expected state after this two `AddItemAction` are
handled? There should be 2 state changes: First `ShowContent(items = listof( Item("1"), Item("2"), Item("random 3")))`
and secondly `ShowContent(items = listof( Item("1"), Item("2"), Item("random 3"), Item("random 6")))`. This is exactly
what we get by using `MutateState`. If we use `OverrideState` or `MutateState` would not have a reducer lambda as
parameter then the code as follows:

```kotlin
// DSL specs
spec {
    inState<ShowContent> {
        on<AddItemAction> { action: AddItemAction, state: ShowContent ->
            val item: Item = createRandomItem()
            // WRONG: don't do that. This is just to demo an issue (see explanation bellow).
            OverrideState(state.copy(items = currentState.items + item)) // or MutateState( state.copy(items = currentState.items + item) )
        }
    }
}
```

and state transition are as follows:
First `ShowContent(items = listof( Item("1"), Item("2"), Item("random 3")))` and
secondly `ShowContent(items = listof( Item("1"), Item("2"), Item("random 6")))`. How comes that the second state
transition produces `ShowContent` without `Item("random 3")`? The reson is that we are accessing the state
parameter `on<AddItemAction> { action: AddItemAction, state: ShowContent -> ... }`
and that this state parameter is actually only a snapshot of the state when `on<AddItemAction>` did trigger. In our
example this is always capturing the following state `ShowContent(items = listof( Item("1"), Item("2") ) )`
The state could have changed before reaching `return OverrideState(...)`. Well, that is exactly what is happening.
Remember, within a few milliseconds we dispatch 2 times `AddItemAction` and then it takes 3 and 6 seconds
for `createRandomItem()` to return. Thus, the state transition actually overrides the first state transition. This is
why you must use `MutateState` with a reducer lambda in such cases when you want to change some properties of the
current state (but not transition to an entirely different state like `Error`) to handle cases properly where
parallel execution could have changed the state in the meantime.

### `NoStateChange`

`NoStateChange` is the last option that you have for `ChangeState` and it should be only used very carefully to cover
some cases that could not be covered otherwise.
`NoStateChange` is an `object`, thus a singleton. Basically what `NoStateChange` is good for is to tell that you
actually dont want to do a state transition. When you need this? Again, there should be only very limited use case
for `NoStateChange` but most likely if there is really no other way then at runtime check for some conditions and then
either trigger `OverrideState`, `MutateState` or `NoChangeState` if state should really not change (but you really only
know that at runtime only, but this is usually an indicator that you should rethink your state modelling to avoid having
this issue).

Usage:

```kotlin
suspend fun handleFooAction(action: Action, stateSnapshot: State) {
    if (action.data == "foo" && stateSnapshot.data == "bar") // just demo some random condition check
        return NoStateChange

    ...
    return OverrideState(OtherState())
}
```