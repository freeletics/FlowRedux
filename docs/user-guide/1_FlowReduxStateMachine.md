# FlowReduxStateMachine Basics: State and Action

FlowRedux provides a convenient DSL to describe your state machine.
This page introduces and explains step by step the FlowRedux DSL.

Let's build an example app using FlowRedux:
This app loads a list of items from a web service.
As we introduce more concepts of the DSL we will extend this example with more features.

This page is meant to be read from top to bottom.


## FlowReduxStateMachine
The base class in FlowRedux is `FlowReduxStateMachine`.
It has a very simple public API:

```kotlin
class FlowReduxStateMachine<State, Action> {
    val state : Flow<State>
    suspend fun dispatch(action : Action)
}
```

Every `FlowReduxStateMachine` works on a `State` class.
How you model your state is up to you and depends on what your app and business logic actually has as requirements.
You can collect the `FlowReduxStateMachine.state : Flow<State>` (from Kotlin coroutines library) by calling `.collect()` on it.
Whenever the state of the state machine changes, observers get the updated state via this `Flow`.

We also need a way to "input" something to our state machine like a user has clicked on a button in the UI.
"Inputs" are called `Actions` in FlowRedux.
An example is `data class LoginSubmittedAction(val username : String, val password : String)`.
Again, how you model your Actions is up to you.
There are no constraints or limitations from FlowRedux.
You can dispatch an `Action` with `FlowReduxStateMachine.dispatch(action)`.


That should be enough information to get started with our example app based on FlowRedux.
Let's define the `States`for our state machine.
As said before we load a list of items from a web server (via http) and display that list.
While loading the list we show a loading indicator on the screen and if an error occurs we show an error message on the screen with a retry button.

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

If the state machine reaches the `Error` state then we display an error message in our UI but also a button the users of our app can click to retry loading the items.

This gives us the following `Actions`:

```kotlin
sealed interface Action {
    object RetryLoadingAction : Action
}
```

This is how the UI looks like:

![Sample UI](../images/lce.gif)

## Initial State

Every `FlowReduxStateMachine` needs an initial state.
This specifies in which state the state machine starts.
In our example we start with the `Loading` state.

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient
) : FlowReduxStateMachine<ListState, Action>(initialState = Loading) {

    init {
        spec {
            // will be filled in next section
            ...
        }
    }
}
```

In FlowRedux we need an `init {...}` block containing a `spec { ... }` block inside.
The `spec { ... }` block is actually where we write our state machine specification with the DSL.