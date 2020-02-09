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
There are a some new things like  `onEnter`, `getState` and `setState`.
Let's first talk a bit about `onEnter`:

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
 The takeaway is: the full `onEnter { ... }` block will be executed once a state has been entered (there is an exception, we will talk about that in `FlatMapPolicy` section).

## SetState

## GetState

## FlatMapPolicy