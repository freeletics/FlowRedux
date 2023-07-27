# Testing
You may wonder what is the best way to test a `FlowReduxStateMachine`?
There are two strategies we want to discuss here in this section:

1. functional integration tests: test the whole state machine as a whole.
2. Unit tests to test only a certain handler such as `onEnter {}`, `on<Action>` and so on.


### Functional integration tests with Turbine
This is our recommended way for testing `FlowReduxStateMachine`.
For this we need [Turbine](https://github.com/cashapp/turbine).
Turbine is a library that makes testing `Flow` from Kotlin coroutine much easier.

Let's say we want to test our `ItemListStateMachine`.
With turbine we can do that step by ste quite easily:

```kotlin
import kotlinx.coroutines.test.runTest

@Test
fun `state machine starts with Loading state`() = runTest {
    val statemachine = ItemListStateMachine(HttpClient())
    statemachine.state.test {
        // awaitItem() from Turbine waits until next state is emitted.
        // FlowReduxStateMachine emits initial state immediately.
        assertEquals(Loading, awaitItem())
    }
}

@Test
fun `move from Loading to ShowContent state on successful http response`() = runTest {
    val items : List<Item> =  generatesomeFakeItems()
    val httpClient = FakeHttpClient(successresponse = items)
    val statemachine = ItemListStateMachine(httpClient)
    statemachine.state.test {
        assertEquals(Loading, awaitItem()) // initial state
        assertEquals(ShowContent(items), awaitItem()) // loading successful --> ShowContent state
    }
}

@Test
fun `move from Loading to Error state on error http response`() = runTest {
    val exception = IOExpcetion("fake exception")
    val httpClient = FakeHttpClient(error = exception)
    val statemachine = ItemListStateMachine(httpClient)
    statemachine.state.test {
        assertEquals(Loading, awaitItem()) // initial state
        assertEquals(Error(cause = exception, countdown = 3), awaitItem())
    }
}
```

We can apply this pattern all the time, but isn't it a bit annoying to always start our state machine from initial state and have to go thorough all the state transitions until we reach the state we want to test?
Well, one nice side effect of using state machines is that you can jump to a certain state right from the beginning.
To be able to do that we need to pass the initial  state as constructor parameter like this:

```kotlin
class ItemListStateMachine(
    private val httpClient: HttpClient,
    initialState : ListState = Loading // now constructor parameter
) : <ListState, Action>(initialState) { ... }
```

Now let's write a test that checks that pressing the retry button works:

```kotlin
@Test
fun `from Error state to Loading if RetryLoadingAction is dispatched`() = runTest {
    val initialState = Error(cause = IOException("fake"), countdown = 3)
    val statemachine = ItemListStateMachine(httpClient, initialState)

    statemachine.state.test {
        assertEquals(initialState, awaitItem())
        // now we dispatch the retry action
        statemachine.dispatch(RetryLoadingAction)

        // next state should then be Loading
        assertEquals(Loading, awaitItem())
    }
}

@Test `once Error countdown is 0 move to Loading state`() = runTest {
    val cause = IOException("fake")
    val initialState = Error(cause = cause, countdown = 3)
    val statemachine = ItemListStateMachine(httpClient, initialState)

    statemachine.state.test {
        assertEquals(initialState, awaitItem())
        assertEquals(Error(cause, 2))
        assertEquals(Error(cause, 1))
        assertEquals(Error(cause, 0))
        assertEquals(Loading, awaitItem())
    }
}
```

### Unit testing handlers
Another way how you can test your state machines is on unit test level, but it requires that you logic is extracted into functions.

For example, let's say we want to unit test `loadItemsAndMoveToContentOrError()`

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
        state.override { Error(cause = t, countdown = 3) }
    }
}
```

We can do that as such:

```kotlin
@Test
fun `on http success move to ShowContent state`() = runTest{
    val items : List<Item> =  generatesomeFakeItems()
    val httpClient = FakeHttpClient(successresponse = items)
    val statemachine = ItemListStateMachine(httpclient)

    val startState = State(Loading) // Create a FlowRedux State object
    val changedState : ChangedState<ListState> = statemachine.loadItemsAndMoveToContentOrError(startState)

    val result : ListState = changedState.reduce(startState.snapshot) // FlowRedux API: you must call reduce

    val expected = ShowContent(items)
    assertEquals(expected, result)
}
```

With FlowRedux you can write unit tests, but there is a bit of overhead:

1. You need to wrap the actual state into FlowRedux `State` class.
2. To get from a `ChangedState` to the actual value you need to call `.reduce()` on it.

What we basically have to do here is what FlowRedux does internally.
In the future we may provide a more convenient way to write this kind of unit tests with less overhead.
