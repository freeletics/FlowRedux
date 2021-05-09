# DSL Guide

FlowRedux provides a convenient DSL to describe your state machine. This page introduces you the DSL that you can use.

To do that we will stick with a simple example of loading a list of items from a web service. As you read this section
and more concepts of the DSL will be introduced we will extend this sample.

For now to get started, let's define the `States` our state machine has. As said before we load a list of items from a
web service and display that list. While loading the list we show a loading indicator on the screen and if an error
occurs we show an error message on the screen with a retry button.

This gives us the following states:

```kotlin
sealed class State {

    // Shows a loading indicator on screen
    object LoadingState : State()

    // List of items loaded successfully, show it on screen
    data class ShowContentState(val items: List<Item>) : State()

    // Error while loading happened
    data class ErrorState(val cause: Throwable) : State()
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

Every `FlowReduxStateMachine` needs an initial state. This is in which state the state machine starts. In our example we
start with the `LoadingState`.

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

Please note the constructor parameter of `FlowReduxStateMachine(initialState = ...)`. This is how you define the initial
state of your state machine. Next, we already see that we need an `init {...}` block containing a `spec { ... }` block
inside. The `spec { ... }` block is actually where we write our DSL inside.

## ChangeState

One key concept of the FlowRedux DSL is that the return type of every function such as `onEnter`, `onAction`
and `collectWhileInState`
(we will learn about them later) is of type `ChangeState<State>`. For example:

```kotlin
suspend fun handleLoadingAction(stateSnapshot: State): ChangeState<State> {
    val items = loadItems() // suspend function
    return OverrideState(ShowContentState(items)) // OverrideState extends from ChangeState. We will talk about it in 1 minutes.
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
current state (but not transition to an entirely different state like `ErrorState`) to handle cases properly where
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

Please note that `inState` itself doesn't do anything. All we did so far with `inState<LoadingState>` is set an entry
point. Next let's discuss what an `inState` can contain as triggers to actually "do something":

1. `onEnter`: Triggers whenever we enter that state
2. `on<Action>`: Triggers whenever we are in this state and the specified action is triggered from the outside by
   calling `FlowReduxStateMachine.dispatch(action)`.
3. `collectWhileInState( flow )`: You can subscribe to any arbitrary `Flow` while your state machine is in that state.

Let's go through them as we build our state machine.

### onEnter

What do we want to do when we enter the `LoadingState`? We want to do the http request, right? Let's do that by
extending our example:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                onEnter { stateSnapshot: LoadingState ->
                    // we entered the LoadingState, so let's do the http request
                    try {
                        val items = httpClient.loadItems()  // loadItems() is a suspend function
                        OverrideState(ShowContentState(items)) // return OverrideState
                    } catch (t: Throwable) {
                        OverrideState(ErrorState(t))
                    }
                }
            }
        }
    }
}
```

There are a some new things like  `onEnter` and `OverrideState`. We covered `OverrideState` in
a [dedicated section](#ChangeState). Let's talk about `onEnter`:

- **`onEnter { ... }` is running asynchronously in a coroutine**. That means whatever you do inside the `onEnter` block
  is not blocking anything else. You can totally run here long-running and expensive calls (like doing a http request).
- **`onEnter { ... }` expects a lambda (or function) with the following
  signature: `onEnter( (State) -> ChangeState<State> )`**: `OverrideState` extends from `ChangeState`.
- **The execution of the `onEnter { ... }` is canceled as soon as state condition specified in the surrounding `inState`
  doesn't hold anymore (i.e. state has been changes by something else).**

### on`<Action>`

How do we deal with external user input like clicks in FlowRedux? This is what Actions are for. In FlowRedux DSL you can
react on Actions by using a `on<MyAction>{ ... }` block.

In our example we want to retry loading if we are in `ErrorState` and the user clicks on a retry button. Clicking on
that button dispatches a `RetryLoadingAction` to our state machine. Let's extend our FlowReduxStateMachine to react on
such an action if the current state is `ErrorState`:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                onEnter { stateSnapshot: LoadingState ->
                    // we entered the LoadingState, so let's do the http request
                    try {
                        val items = httpClient.loadItems()
                        OverrideState(ShowContentState(items))
                    } catch (t: Throwable) {
                        OverrideState(ErrorState(t))
                    }
                }
            }

            // let's add a new inState{...} with an on{...} block
            inState<ErrorState> {
                on<RetryLoadingAction> { action: RetryLoadingAction, stateSnapshot: ErrorState ->
                    // This block triggers if we are in ErrorState 
                    // RetryLoadingAction has been dispatched to this state machine.
                    // In that case we transition to LoadingState which then starts the http
                    // request to load items again as the inState<LoadingState> + onEnter { ... } triggers

                    OverrideState(LoadingState)
                }
            }
        }
    }
}
```

A `on { ... }` block gets 3 parameters:  `action` which is the actual instance of the `Action` that triggered this block
and `stateSnapshot` which is a snapshot of the current state.
`on { ... }` is actually pretty similar to `onEnter {...}` just with a different "trigger" (action vs. entering a state)
. Furthermore, `on { ... }` has the same characteristics as `onEnter { ... }`:

- **`on { ... }` is running asynchronously in a coroutine**. That means whatever you do inside the `on` block is not
  blocking anything else. You can totally run here long-running and expensive calls (like doing a http request).
- **`on { ... }` expects a lambda (or function) with the following
  signature: `(action : Action , stateSnapshot : State) -> ChangeState<State>`**.
- **The execution of the `on { ... }` is canceled as soon as state condition specified in the surrounding `inState`
  doesn't hold anymore (i.e. state has been changes by something else).**

### collectWhileInState()

This one is useful if you want to collect a `Flow` only while being exactly in that state. To give a concrete example
how this is useful let's extend our example from above. Let's say whenever our state machine is in `ErrorState` we want
to retry loading the items after 3 seconds in `ErrorState` or anytime before the 3 seconds have elapsed if the user
clicks the retry button. Furthermore the 3 seconds countdown timer should be displayed in our app:

To implement this let's first extend our `ErrorState`:

```kotlin
data class ErrorState(
    val cause: Throwable,
    val countdown: Int    // This value is decreased from 3 then 2 then 1 and represents the countdown value.
) : State()
```

Now let's add some countdown capabilities to our state machine by using `collectWhileInState()`:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                inState<LoadingState> {
                    onEnter { stateSnapshot: LoadingState ->
                        // we entered the LoadingState, so let's do the http request
                        try {
                            val items = httpClient.loadItems()
                            OverrideState(ShowContentState(items))
                        } catch (t: Throwable) {
                            OverrideState(ErrorState(t))
                        }
                    }
                }

                // let's add a new inState{...} with an on{...} block
                inState<ErrorState> {
                    on<RetryLoadingAction> { action: RetryLoadingAction, stateSnapshot: ErrorState ->
                        OverrideState(LoadingState)
                    }

                    collectWhileInState(timerThatEmitsEverySecond()) { value: Int, stateSnapshot: ErrorState ->
                        // This block triggers every time the timer emits
                        // which happens every second
                        MutateState<ErrorState, State> { // in this block, this references ErrorState 
                            if (countdown > 0)   // is the same as this.countdown references ErrorState
                                copy(countdown = countdown - 1) //  decrease the countdown by 1 second
                            else
                                LoadingState // transition to the LoadingState
                        }
                    }
                }
            }
        }
    }

    private fun timerThatEmitsEverySecond(): Flow<Int> {
        var timeElapsed = 0
        while (isActive) {  // is Flow still active?
            delay(1000)     // wait 1 second
            timeElapsed++
            emit(timeElapsed) // Flow Emits value
        }
    }
}
```

Let's look at the source code above step by step. Whenever we are in `LoadingState` and an error occurs while loading
the items we go into
`ErrorState`. Nothing has changes from previous code snipped. What is new is that `ErrorState` contains an additional
field  `countdown` which we set on transitioning from `LoadingState` to `ErrorState(countdown = 3)` (means 3 seconds
left).

We extend ` inState<ErrorState> { ... }` block and add a `collectWhileInState(timer)`.
`timer` is a `Flow<Int>` that emits a new (incremented) number every second.
`collectWhileInState(timer)` calls `.collect {...}` on the flow passed as parameter and executes the block with the
parameters `value`
every time `timer` emits a new value. In other words: instead of calling `timer.collect { ... }` you
call `collectWhileInState(timer) { ... }` to collect the Flow's values as long as the state machine is in that state.

The passed Flow (in our case the timer) is automatically canceled once the state machine transitioned from
`ErrorState` into another state. This happens either when the user clicks on the retry button and which
triggers `on<RetryLoadingAction>` which causes a state transition to `LoadingState` or when 3 seconds have elapsed
because then the defined `MutateState` causes a transitions to `LoadingState`.

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
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(
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
                    // we entered the LoadingState, so let's do the http request
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
                            ) // transition to the LoadingState
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

## collectWhileInAnyState()

If for whatever reason you want to trigger a state change out of  `inState<>`, `onEnter { ... }`, `on<Action>`
or `collectWhileInState { ... }` by observing a `Flow` then `collectWhileInAnyState` is what you are looking for:

```kotlin
class MyStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<State, Action>(initialState = LoadingState) {

    init {
        spec {
            inState<LoadingState> {
                onEnter { stateSnapshot: LoadingState ->
                    ...
                }
            }

            inState<ErrorState> {
                on<RetryLoadingAction> { action: RetryLoadingAction, stateSnapshot: ErrorState ->
                    ...
                }

                collectWhileInState(timer) { value, stateSnapshot: ErrorState ->
                    ...
                }
            }

            val aFlow: Flow<Int> = flowOf(1, 2, 3, 4)
            collectWhileInAnyState(aFlow) { value: Int, stateSnapshot: State ->
                // Will trigger anytime flow emits a value
                ...
            }

            collectWhileInAnyState(anotherFlow) { value: String, stateSnapshot: State ->
                // Will trigger anytime flow emits a value
                ...
            }
        }
    }
}
```

`collectWhileInAnyState()` is like `collectWhileInState()` just that it is not bound to the current state
like `collectWhileInState()` is.
`collectWhileInAnyState()` will stop collecting the passed in Flow only if the CoroutineScope of the whole
FlowReduxStateMachine gets canceled.

## FlatMapPolicy

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
triggered. That is possible right? With `FlatMapPolicy` you can specify what should happen in that case. There are three
options to choose from:

- `LATEST`: This is the default one. It would cancel any previous execution and just run the latest one. In the example
  above it would meanwhile wait 5 seconds another `BarAction` gets triggered, the first execution of `on<BarAction>`
  block gets stopped and a new `on<BarAction>` block starts.
- `MERGE`: Choosing this causes all the blocks to continue running but there are no guarantees in which order. For
  example:

```kotlin
spec {
    inState<FooState> {
        on<BarAction>(flatMapPolicy = FlapMapPolicy.MERGE) { _, _, setState ->
            delay(randomInt()) // wait for some random time
            setState { OtherState }
        }
    }
}
```

Let's assume that we trigger two times `BarAction`. We use random amount of seconds for waiting. Since we
use `MERGE` `on<BarAction>` block gets executed 2 times without canceling the previous one (that is the difference
to `LATEST`). Moreover, `MERGE` doesn't make any promise on order of execution of the block (see `CONCAT` if you need
promises on order). So if `on<BarAction>` gets executed two times it will run in parallel and the the second execution
could complete before the first execution (because using a random time of waiting).

- `CONCAT`: In contrast to `MERGE` and `LATEST` `CONCAT` will not run `on<BarAction>` in parallel and will not cancel
  any previous execution. Instead, `CONCAT` will preserve the order and execute one block after another.

All execution blocks can specify a `FlatMapPolicy`:

- `on<Action>(flatMapPolicy = FlatMapPolicy.LATEST){... }`
- `onEnter(flatMapPolicy = FlatMapPolicy.LATEST) { ... }`
- `collectWhileInState(flatMapPolicy = FlatMapPolicy.LATEST) { ... }`

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
                onEnter { stateSnapshot ->
                    // we entered the LoadingState, so let's do the http request
                    try {
                        val items = httpClient.loadItems()
                        OverrideState(ShowContentState(items))
                    } catch (t: Throwable) {
                        OverrideState(ErrorState(cause = t, countdown = 3)) // Countdown starts with 3 seconds
                    }
                }
            }

            inState<ErrorState> {
                on<RetryLoadingAction> { action, stateSnapshot ->
                    OverrideState(LoadingState)
                }

                collectWhileInState(timerThatEmitsEverySecond()) { value, stateSnapshot ->
                    MutateState<ErrorState, State> {
                        if (this.countdownTimeLeft > 0) // this is referencing ErrorState
                            this.copy(countdown = countdownTimeLeft - 1)  //  decrease the countdown by 1 second
                        else
                            LoadingState  // transition to the LoadingState
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
                on<RetryLoadingAction> { action, stateSnapshot ->
                    // For a single line statement it's ok to keep the block instead of moving to a function reference
                    OverrideState(LoadingState)
                }

                collectWhileInState(
                    timerThatEmitsEverySecond(),
                    ::onSecondElapsedMoveToLoadingStateOrMoveToDecrementCountdown
                )
            }
        }
    }


    //
    // All the implementation details are in the functions below.
    //

    private fun loadItemsAndMoveToContentOrErrorState(stateSnapshot: LoadingState): ChangeState<State> {
        return try {
            val items = httpClient.loadItems()
            OverrideState(ShowContentState(items))
        } catch (t: Throwable) {
            OverrideState(ErrorState(cause = t, countdown = 3)) // Countdown starts with 3 seconds
        }
    }

    private fun onSecondElapsedMoveToLoadingStateOrMoveToDecrementCountdown(
        value: Int,
        stateSnapshot: ErrorState
    ): ChangeState<State> {
        return MutateState<ErrorState, State> {
            if (this.countdownTimeLeft > 0) // this is referencing ErrorState
                this.copy(countdown = countdownTimeLeft - 1)  //  decrease the countdown by 1 second
            else
                LoadingState  // transition to the LoadingState
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