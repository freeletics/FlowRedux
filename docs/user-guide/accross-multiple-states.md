# Acting across multiple states

If for whatever reason you want to trigger a state change for all states you can achieve that by
using `inState<>` on a base class.

```kotlin
// DSL specs
spec {
    inState<ListState> {
        // on, onEnter, collectWhileInState for all states because
        // ListState is the base class, thus these never get cancelled
    }

    inState<Loading> {
        // on, onEnter, collectWhileInState specific to Loading state
    }

    inState<ShowContent> {
        // on, onEnter, collectWhileInState specific to ShowContent state
    }

    ...
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

```
// DSL specs
spec {
    inState<PostLoading> {
        // on, onEnter, collectWhileInState for all PostLoading states.
        // It means as long as we are in ShowContent or ErrorState this DSL block
        // is active
    }
    ...
}
```