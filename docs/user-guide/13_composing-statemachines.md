# Composing state machines (hierarchical state machines)
With FlowRedux you can compose state machines from other state machines.
This concept is called hierarchical state machines.
In this section we will introduce `onActionStartStateMachine()` and `onEnterStartStateMachine()`.

Think about [Jetpack Compose](https://developer.android.com/jetpack/compose), [SwiftUI](https://developer.apple.com/xcode/swiftui/) or [React](https://reactjs.org/).
They are declarative frameworks to build UI.
Furthermore, you are encouraged by these frameworks to build reusable UI components.
Wouldn't it be great to get the same for your business logic?
With FlowRedux's `onActionStartStateMachine()` and `onEnterStartStateMachine()` you can do that.

Advantages:
- reuse state machines while still keep them decoupled and encapsulated from each other
- favor composition over inheritance
- easier to test

### `onActionStartStateMachine()`
Let's continue enhancing our `ItemListStateMachineFactory`.
`ShowContent` state is defined as following:

```kotlin
data class ShowContent(val items: List<Item>) : ListState

data class Item(val id : Int, val name : String)
```

Let's say we want to have the option to mark an `Item` as favorite (and also remove an item as favorite).
The favorite items are actually saved on a server and we communicate with it over HTTP.
Let's extend the `Item` class to model these new requirements:

```kotlin
data class Item(
    val id : Int,
    val name : String,
    val favoriteStatus : FavoriteStatus
)

sealed interface FavoriteStatus {
    val itemId : Int

    // It is not marked as favorite yet
    data class NotFavorite(override val itemId : Int) : FavoriteStatus

    // Marked as favorites
    data class Favorite(override val itemId : Int) : FavoriteStatus

    // An operation (read: HTTP request) is in progress to either mark
    // it as favorite or not mark it as favorite
    data class OperationInProgress(
        override val itemId : Int,
        val markAsFavorite : Boolean // true means mark as favorite, false means unmark it
    ) : FavoriteStatus

    // The operation (read: HTTP request) to either mark it as favorite
    // or unmark it as favorite has failed; so did not succeed.
    data class OperationFailed(
        override val itemId : Int,
        val markAsFavorite : Boolean // true means mark as favorite, false means unmark it
    ) : FavoriteStatus
}
```

You may wonder why we need `FavoriteStatus` and why it is not just a `Boolean` to reflect marked as favorite or not.
Remember: we also need to talk to a server (via HTTP) whenever the user wants to mark an `Item` as favorite or unmark it.
The UI looks like this:

![Item state favorite](../images/item-favorite-state.gif)


For now, let's ignore the `ItemListStateMachineFactory` and only focus on our new requirements: marking an `Item` as favorite (or unmarking it) plus the communication with our backend server to store that information.
We could add these new requirements with our DSL to `ItemListStateMachineFactory`, or we extract that into a small stand-alone state machine.
Let's call this state machine `FavoriteStatusStateMachine` and use the FlowRedux DSL to define its logic.
Additionally, let's say when a network error in the communication with the backend server happens, we will show an error for 3 seconds and then reset back to either marked as favorite or not.

```kotlin
class FavoriteStatusStateMachine(
    item : Item,
    private val httpClient : HttpClient
) : FlowReduxStateMachineFactory<FavoriteStatus, Nothing>() { // doesn't handle Action, thus we can use Nothing
    init {
        initializeWith {
            OperationInProgress(
                itemId = item.itemId,
                markAsFavorite = item.favoriteStatus is NotFavorite
            )
        }

        spec {
            inState<OperationInProgress>{
                onEnter {
                    toggleFavoriteAndSaveToServer()
                }
            }

            inState<OperationFailed>{
                onEnter{
                    waitFor3SecondsThenResetToOriginalState()
                }
            }
        }
    }

    private suspend fun ChangeableState<OperationInProgress>.toggleFavoriteAndSaveToServer() : ChangedState<FavoriteStatus> {
        return try {
            val itemId = state.snapshot.itemId
            val markAsFavorite = state.snapshot.markAsFavorite

            httpClient.toggleFavorite(
                itemId = itemId,
                markAsFavorite = markAsFavorite // if false then unmark it, if true mark it as favorite
            )

            if (markAsFavorite) {
                override { Favorite(itemId) }
            } else {
                override { NotFavorite(itemId) }
            }
        } catch (exception : Throwable) {
            state.override { OperationFailed(itemId, markAsFavorite) }
        }
    }

    private suspend fun ChangeableState<OperationFailed>.waitFor3SecondsThenResetToOriginalState() : ChangedState<FavoriteStatus> {
        delay(3_000) // Wait for 3 seconds
        val itemId = state.snapshot.itemId
        val markAsFavorite = state.snapshot.markAsFavorite
        return if (markAsFavorite) {
            // marking as favorite failed,
            // thus original status was "not marked as favorite"
            override { NotFavorite(itemId) }
        } else {
            override { Favorite(itemId) }
        }
    }
}
```

All that `FavoriteStatusStateMachine` does is make an HTTP request to the backend and, in case of error, reset back to the previous state after showing an error state for 3 seconds.

This is how the UI should look:

![Sample UI](../images/favorite-state-list.gif)


Now let's connect this with our `ItemListStateMachineFactory` by using `onActionStartStateMachine()`.

```kotlin
data class ToggleFavoriteItemAction(val itemId : Int) : Action

class ItemListStateMachineFactory(
    private val httpClient: HttpClient
) : FlowReduxStateMachineFactory<ListState, Action>() {

    // This is the specification of your state machine.
    // Less implementation details, better readability.
    init {
        initializeWith { Loading }

        spec {
            inState<Loading> {
                onEnter { loadItemsAndMoveToContentOrError(it) }
            }

            inState<Error> {
                on<RetryLoadingAction> { action, state ->
                   state.override { Loading }
                }

                collectWhileInState(timerThatEmitsEverySecond()) { value, state  ->
                    decrementCountdownAndMoveToLoading(value, state)
                }
            }

            // NEW DSL block
            inState<ShowContent> {

                // on ToggleFavoriteItemAction start state machine
                onActionStartStateMachine(
                    stateMachineFactoryBuilder = { action: ToggleFavoriteItemAction ->
                        val item : Item = snapshot.items.find { it == action.itemId }
                        // create and return a new FavoriteStatusStateMachine instance
                        FavoriteStatusStateMachine(item, httpClient)
                    },
                ) { favoriteStatus: FavoriteStatus ->
                    mutate {
                        val itemToReplace: Item = this.items.find { it == favoriteStatus.itemId }
                        val updatedItem: Item = itemToReplace.copy(favoriteStatus = favoriteStatus)

                        // Create a copy of ShowContent state with the updated item
                        this.copy(items = this.items.copyAndReplace(itemToReplace, updatedItem))
                    }
                }
            }
        }
    }

    // ...
}
```

First, let's take a look at the `onActionStartStateMachine()` public API.
It has three parameters.
Multiple overloads exist, and in our case the one with only two parameters is enough.
Nevertheless, let's explain all three parameters of `onActionStartStateMachine()`:

1. `stateMachineFactoryBuilder: State<State>.(Action) -> FlowReduxStateMachineFactory`: Inside this block you create a state machine. In our case we create a `FavoriteStatusStateMachine`. You have access to the current state of the `ItemListStateMachineFactory` and the `Action` that has triggered `onActionStartStateMachine()`.
2. `handler: ChangeableState<T>.(StateOfNewStateMachine) -> ChangedState<T>`: We need a way to combine the state of the newly started state machine with the one of the "current" state machine. In our case we need to combine `ItemListStateMachineFactory`'s state with `FavoriteStatusStateMachine`'s state. That is exactly what the `handler` is for. The difference is that `ItemListStateMachineFactory` provides a `ChangeableState<T>` to the `handler` as receiver, whereas `FavoriteStatusStateMachine` provides the current `FavoriteState` (not `ChangeableState<FavoriteState>`). The reason is that, in the end, we need to get a compatible state for `ItemListStateMachineFactory`, and that is what we need to do through the already known `ChangeableState<T>.override()` or `ChangeableState<T>.mutate()` methods.
3. `actionMapper: (Action) -> OtherStateMachineAction?`: We didn't need this in our example above because `FavoriteStatusStateMachine` is not dealing with any action. In theory, however, we need to "forward" actions from `ItemListStateMachineFactory` to `FavoriteStatusStateMachine`. But since the actions of the two state machines could be of different types, we would need to map an action type of `ItemListStateMachineFactory` to another action type of `FavoriteStatusStateMachine` or `null` if not all actions are supposed to be handled by `FavoriteStatusStateMachine`. Returning `null` in the `actionMapper` means the action is not forwarded to `FavoriteStatusStateMachine`. Again, this is not needed here in this example, but in theory could be needed in other use cases.

You may wonder what the lifecycle of the state machine started from `onActionStartStateMachine()` looks like:
- The started state machine (in our case `FavoriteStatusStateMachine`) will be kept alive as long as the surrounding `inState<State>` holds true. This works just like the other DSL primitives (like `on<Action>`). In our example a `FavoriteStatusStateMachine` is canceled when `ItemListStateMachineFactory` transitions away from the `ShowContent` state.
- Every time an `Action` that is handled by `onActionStartStateMachine()` is dispatched, the `stateMachineFactoryBuilder` is invoked and a new state machine gets started. It’s important that actions are distinguished by their `.equals()` method. In our example `ToggleFavoriteItemAction(itemId = 1)` and `ToggleFavoriteItemAction(itemId = 2)` are two different actions because `ToggleFavoriteItemAction.equals()` also takes `itemId` into account. Therefore, two instances of `FavoriteStatusStateMachine` are started, one for itemId = 1 and one for itemId = 2.
- If the same-by-`.equals()` `ToggleFavoriteItemAction(itemId = 1)` gets dispatched again, then the previously started state machine gets canceled and a new one starts (with the latest `action` as the trigger). There is always only one state machine for the same `action` trigger running.


### Make DSL even more readable with custom DSL additions
In the previous section we have introduced `onActionStartStateMachine()` but it is quite a bit of code in our otherwise nicely readable `spec { }` block:

```kotlin
spec {
    inState<Loading> {
        onEnter { loadItemsAndMoveToContentOrError(it) }
    }

    inState<Error> {
        on<RetryLoadingAction> { action, state ->
            state.override { Loading }
        }

        collectWhileInState(timerThatEmitsEverySecond()) { value, state  ->
            decrementCountdownAndMoveToLoading(value, state)
        }
    }

    inState<ShowContent> {

        // Quite a bit of unreadable code
        onActionStartStateMachine(
            stateMachineFactory = {
                action: ToggleFavoriteItemAction, stateSnapshot : ShowContent ->
                val item : Item = stateSnapshot.items.find { it == action.itemId}
                // create and return a new FavoriteStatusStateMachine instance
                FavoriteStatusStateMachine(item, httpClient)
            },

            stateMapper = {
                itemListState : State<ShowContent>, favoriteStatus :FavoriteStatus ->

                itemListState.mutate {
                    val itemToReplace : Item = this.items.find { it == favoriteStatus.itemId }
                    val updatedItem : Item = itemToReplace.copy(favoriteStatus = favoriteStatus)

                    // Create a copy of ShowContent state with the updated item
                    this.copy(items = this.items.copyAndReplace(itemToReplace, updatedItem) )
                }
            }
        )
    }
}
```

We can do better than this, right?
How?
Which Kotlin extension functions and receivers.
The receiver type is `InStateBuilderBlock` is what `inState<S>` is operating in.

```kotlin
spec {
    inState<Loading> {
        onEnter { loadItemsAndMoveToContentOrError(it) }
    }

    inState<Error> {
        on<RetryLoadingAction> { action, state ->
            state.override { Loading }
        }

        collectWhileInState(timerThatEmitsEverySecond()) { value, state  ->
            decrementCountdownAndMoveToLoading(value, state)
        }
    }

    inState<ShowContent> {
        onActionStartStateMachine({ action -> itemStateMachine(action.itemId) }) { favoriteStatus ->
            updateItemWithFavoriteStatus(favoriteStatus)
        }
    }
}

private fun State<ShowContent>.itemStateMachine(itemId : Int) : FavoriteStatusStateMachine {
    val item : Item = snapshot.items.find { it == itemId }
    // create and return a new FavoriteStatusStateMachine instance
    FavoriteStatusStateMachine(item, httpClient)
}

private fun ChangeableState<ShowContent>.updateItemWithFavoriteStatus(favoriteStatus : FavoriteStatus) : ChangedState<ShowContent> {
    return mutate {
        val itemToReplace : Item = this.items.find { it == favoriteStatus.itemId }
        val updatedItem : Item = itemToReplace.copy(favoriteStatus = favoriteStatus)

        // Create a copy of ShowContent state with the updated item
        this.copy(items = this.items.copyAndReplace(itemToReplace, updatedItem))
    }
}
```

### onEnterStartStateMachine()

Similar to `onActionStartStateMachine()`, FlowRedux provides a primitive to start a state machine on enter with `onEnter { ... }`.

The syntax looks quite similar to `onActionStartStateMachine()`:

```kotlin
spec {
    inState<MyState> {
        onEnterStartStateMachine(
            stateMachineFactoryBuilder = { SomeFlowReduxStateMachine() },
            stateMapper = { someOtherStateMachineState : S ->
                override { ... }
            }
        )
    }
}
```

### Effects

Both `onActionStartStateMachine()` and `onEnterStartStateMachine()` also have effect counterparts: `onActionStartStateMachineEffect()` and `onEnterStartStateMachineEffect()`.
Like other effects, these behave the same as the regular variants but don't change the state of the state machine.
