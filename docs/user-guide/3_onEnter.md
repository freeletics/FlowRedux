# onEnter

What do we want to do when we enter the `Loading` state?
We want to make the HTTP request to load the items from our server, right?
Let's specify that with the DSL in our state machine:

```kotlin
class ItemListStateMachineFactory(
    private val httpClient: HttpClient
) : FlowReduxStateMachineFactory<ListState, Action>() {

    init {
        initializeWith { Loading }

        spec {
            inState<Loading> {
                onEnter {
                    // we entered the Loading state,
                    // so let's do the HTTP request
                    try {
                        val items = httpClient.loadItems()  // loadItems() is a suspend function
                        override { ShowContent(items) }  // return ShowContent from onEnter block
                    } catch (t: Throwable) {
                        override { Error("A network error occurred") }   // return Error state from onEnter block
                    }
                }
            }
        }
    }
}
```

There are some new things like `onEnter` and `override`.
We will cover `override` in the next section.

Let's talk about `onEnter`:

- **`onEnter { ... }` is running asynchronously in a coroutine**. That means whatever you do inside the `onEnter` block
  is not blocking anything else. You can run any suspending calls (like doing an HTTP request).
- **`onEnter { ... }` expects a lambda (or function) with the following
  signature: `onEnter( ChangeableState<T>.() -> ChangedState<T> )`**. We will cover that in detail in the next section.
- **`onEnter { ... }` is executed exactly once when the surrounding `inState<T>` condition is met**.
  It will only be executed the next time when the state machine transitions out of the current state and back to it again.
- **The execution of the `onEnter { ... }` is canceled as soon as the state condition specified in the surrounding `inState`
doesn't hold anymore**, i.e., the state has been changed by some other block of the DSL.
Recall that FlowRedux is a multi-threaded asynchronous state machine. We will talk about that later.

The key takeaway here is that with `onEnter { ... }` you can do some work whenever your state machine is entering this state and then move on to another state by calling `State.override()` or `State.mutate()`

To be able to fully understand the code snippet from above, let's take a look at `ChangeableState<T>` and `ChangedState<T>`.
