# Testing

You may wonder what is the best way to test a `FlowReduxStateMachineFactory`.
There are two strategies we want to discuss in this section:

1. functional integration tests: test the whole state machine as a whole.
2. Unit tests to test only a certain handler such as `onEnter {}`, `on<Action>` and so on.


### Functional integration tests with Turbine
This is our recommended way for testing `FlowReduxStateMachine`.
For this we need [Turbine](https://github.com/cashapp/turbine).
Turbine is a library that makes testing a `Flow` from Kotlin coroutines much easier.

Let's say we want to test our `ItemListStateMachineFactory`.
With Turbine we can do that step by step quite easily:

```kotlin
import kotlinx.coroutines.test.runTest

@Test
fun `state machine starts with Loading state`() = runTest {
    val stateMachine = ItemListStateMachineFactory(HttpClient()).shareIn(backgroundScope)
    stateMachine.state.test {
        // awaitItem() from Turbine waits until next state is emitted.
        // FlowReduxStateMachine emits initial state immediately.
        assertEquals(Loading, awaitItem())
    }
}

@Test
fun `move from Loading to ShowContent state on successful HTTP response`() = runTest {
    val items : List<Item> = generateSomeFakeItems()
    val httpClient = FakeHttpClient(successResponse = items)
    val stateMachine = ItemListStateMachineFactory(httpClient).shareIn(backgroundScope)
    stateMachine.state.test {
        assertEquals(Loading, awaitItem()) // initial state
        assertEquals(ShowContent(items), awaitItem()) // loading successful --> ShowContent state
    }
}

@Test
fun `move from Loading to Error state on error HTTP response`() = runTest {
    val exception = IOException("fake exception")
    val httpClient = FakeHttpClient(error = exception)
    val stateMachine = ItemListStateMachineFactory(httpClient).shareIn(backgroundScope)
    stateMachine.state.test {
        assertEquals(Loading, awaitItem()) // initial state
        assertEquals(Error(message = "A network error occurred", countdown = 3), awaitItem())
    }
}
```

#### Custom initial state

We can apply this pattern all the time, but isn't it a bit annoying to always start our state machine from the initial state and have to go through all the state transitions until we reach the state we want to test?
Well, one nice side effect of using state machines is that you can jump to a certain state right from the beginning, by calling `initializeWith { ... }` before starting the state machine.

Now let's write a test that checks that pressing the retry button works:

```kotlin
@Test
fun `from Error state to Loading if RetryLoadingAction is dispatched`() = runTest {
    val initialState = Error(message = "A network error occurred", countdown = 3)
    val factory = ItemListStateMachineFactory(httpClient)
    factory.initializeWith { initialState }
    val stateMachine = factory.shareIn(backgroundScope)

    stateMachine.state.test {
        assertEquals(initialState, awaitItem())
        // now we dispatch the retry action
        stateMachine.dispatch(RetryLoadingAction)

        // next state should then be Loading
        assertEquals(Loading, awaitItem())
    }
}

@Test
fun `once Error countdown is 0 move to Loading state`() = runTest {
    val msg = "A network error occurred"
    val initialState = Error(message = msg, countdown = 3)
    val factory = ItemListStateMachineFactory(httpClient)
    factory.initializeWith { initialState }
    val stateMachine = factory.shareIn(backgroundScope)

    stateMachine.state.test {
        assertEquals(initialState, awaitItem())
        assertEquals(Error(msg, 2))
        assertEquals(Error(msg, 1))
        assertEquals(Error(msg, 0))
        assertEquals(Loading, awaitItem())
    }
}
```

### Unit testing handlers
Another way you can test your state machines is at the unit test level, but it requires that your logic is extracted into functions.

For example, let's say we want to unit test `loadItemsAndMoveToContentOrError()`.

```kotlin
spec {
    inState<Loading> {
        onEnter { loadItemsAndMoveToContentOrError(it) }
    }
}

suspend fun loadItemsAndMoveToContentOrError(state: State<Loading>): ChangedState<State> {
    return try {
        val items = httpClient.loadItems()
        state.override { ShowContent(items) }
    } catch (t: Throwable) {
        state.override { Error(message = "A network error occurred", countdown = 3) }
    }
}
```

We can do that as such:

```kotlin
@Test
fun `on HTTP success move to ShowContent state`() = runTest{
    val items : List<Item> =  generateSomeFakeItems()
    val httpClient = FakeHttpClient(successResponse = items)
    val stateMachine = ItemListStateMachineFactory(httpClient)

    val startState = ChangeableState(Loading) // Create a FlowRedux ChangeableState object
    val changedState : ChangedState<ListState> = stateMachine.loadItemsAndMoveToContentOrError(startState)

    val result : ListState = changedState.reduce(startState.snapshot) // FlowRedux API: you must call reduce

    val expected = ShowContent(items)
    assertEquals(expected, result)
}
```

With FlowRedux you can write unit tests, but there is a bit of overhead:

1. You need to wrap the actual state into FlowRedux `ChangeableState` class.
2. To get from a `ChangedState` to the actual value you need to call `.reduce()` on it.

What we basically have to do here is what FlowRedux does internally.
In the future we may provide a more convenient way to write this kind of unit tests with less overhead.
