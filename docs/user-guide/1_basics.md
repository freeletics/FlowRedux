# Basics: State and Action

FlowRedux provides a convenient DSL to describe your state machine.
This page introduces and explains step by step the FlowRedux DSL.

Let's build an example app using FlowRedux.
This app loads a list of items from a web service.
As we introduce more concepts of the DSL, we will extend this example with more features.

This page is meant to be read from top to bottom.


## FlowReduxStateMachineFactory and FlowReduxStateMachine

The two base classes in FlowRedux are `FlowReduxStateMachineFactory<State, Action>` and `FlowReduxStateMachine<State, Action>`.
The `FlowReduxStateMachineFactory` is the class you extend and write your state machine specification in. It can then be used
to obtain a `FlowReduxStateMachine` instance through the `launchIn`, `shareIn` and `produceStateMachine` functions.

`FlowReduxStateMachine` has a simple public API:

```kotlin
class FlowReduxStateMachine<State, Action> {
    val state : Flow<State>
    suspend fun dispatch(action : Action)
}
```

Every `FlowReduxStateMachine` works on a `State` class.
How you model your state is up to you and depends on what your app and business logic actually require.
You can collect the `FlowReduxStateMachine.state : Flow<State>` by calling `.collect()` on it.
Whenever the state of the state machine changes, observers get the updated state via this `Flow`.

We also need a way to "input" something to our state machine, like when a user clicks a button in the UI.
"Inputs" are called `Actions` in FlowRedux.
An example is `data class LoginSubmittedAction(val username : String, val password : String)`.
Again, how you model your Actions is up to you.
There are no constraints or limitations from FlowRedux.
You can dispatch an `Action` with `FlowReduxStateMachine.dispatch(action)`.


That should be enough information to get started with our example app based on FlowRedux.
Let's define the `States` for our state machine.
As said before, we load a list of items from a web server (via HTTP) and display that list.
While loading the list, we show a loading indicator on the screen, and if an error occurs, we show an error message on the screen with a retry button.

This gives us the following states:

```kotlin
sealed interface ListState {

    // Shows a loading indicator on screen
    object Loading : ListState

    // List of items loaded successfully, show it on screen
    data class ShowContent(val items: List<Item>) : ListState

    // Error while loading happened
    data class Error(val message: String) : ListState
}
```

If the state machine reaches the `Error` state, then we display an error message in our UI and also a button the users of our app can click to retry loading the items.

This gives us the following `Actions`:

```kotlin
sealed interface Action {
    object RetryLoadingAction : Action
}
```

This is how the UI looks:

![Sample UI](../images/lce.gif)

## Initial State

Every `FlowReduxStateMachineFactory` needs an initial state, which is provided by the `initializeWith { ... }` block.
This specifies in which state the state machine starts.
In our example we start with the `Loading` state.

```kotlin
class ItemListStateMachineFactory(
    private val httpClient: HttpClient
) : FlowReduxStateMachineFactory<ListState, Action>() {

    init {
        initializeWith { Loading }

        spec {
            // will be filled in next section
            ...
        }
    }
}
```

In FlowRedux we need an `init { ... }` block containing an `initializeWith { ... }` and a `spec { ... }` block inside.
The `spec { ... }` block is actually where we write our state machine specification with the DSL.
