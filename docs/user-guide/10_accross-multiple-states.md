# Acting across multiple states

Let's assume we have our state modeled like this:

```kotlin
sealed interface ListState
object Loading : ListState
data class ShowContent : ListState
data class Error (val message : String) : ListState
```

If, for whatever reason, you want to trigger a state change for all states, you can achieve that by
using `inState<>` on a base class.

```kotlin
// DSL specs
spec {
    inState<ListState> {
        // on, onEnter, collectWhileInState for all states because
        // ListState is the base class, thus these never get canceled
    }

    inState<Loading> {
        // on, onEnter, collectWhileInState specific to Loading state only
    }

    inState<ShowContent> {
        // on, onEnter, collectWhileInState specific to ShowContent state only
    }

    // ...
}
```

In case you want to trigger state changes from a subset of states, you could introduce another
level to your state class hierarchy. For example, the following would allow you to have an
`inState<PostLoading>` block to share actions between `ShowContent` and `Error`:

```kotlin
sealed interface ListState {

    // Shows a loading indicator on screen
    object Loading : ListState

    sealed interface PostLoading : ListState

    // List of items loaded successfully, show it on screen
    data class ShowContent(val items: List<Item>) : PostLoading

    // Error while loading happened
    data class Error(val message: String) : PostLoading
}
```

```
// DSL specs
spec {
    inState<PostLoading> {
        // on, onEnter, collectWhileInState for all PostLoading states.
        // It means as long as we are in ShowContent or Error state this DSL block
        // is active.
    }

    inState<ListState> {
         // on, onEnter, collectWhileInState for all ListState states, so for all states of this state machine.
    }

    inState<Loading> {
        // on, onEnter, collectWhileInState specific to Loading state only
    }

    inState<ShowContent> {
        // on, onEnter, collectWhileInState specific to ShowContent state only
    }
    ...
}
```
