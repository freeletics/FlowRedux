# ExecutionPolicy

Have you ever wondered what would happen if you would execute `Action` very fast 1 after another?
For example:

```kotlin
spec {
    inState<FooState> {
        on<BarAction> { action ->
            delay(5000) // wait for 5 seconds
            override { OtherState() }
        }
    }
}
```

The example above shows a problem with async. state machines like FlowRedux:
If our state machine is in `FooState` and a `BarAction` got triggered, we wait for 5 seconds and then set the state to another state.
What if while waiting 5 seconds (i.e. let's say after 3 seconds of waiting) another `BarAction` gets
triggered.
That is possible, right?
With `ExecutionPolicy` you can specify what should happen in that case.
There are three options to choose from:

- `CancelPrevious`: **This is the default one automatically applied unless specified otherwise.** It would cancel any previous
  execution and just run the latest one. In the example mentioned it means the previous still running `BarAction` handler
  gets canceled and a new one with the laster `BarAction` starts.
- `Unordered`: Choosing this causes all the blocks to continue running but there are no guarantees in which order. For example:

    ```kotlin
    spec {
        inState<FooState> {
            on<BarAction>(executionPolicy = FlapMapPolicy.Unordered) {
                delay(randomInt()) // wait for some random time
                override { OtherState }
            }
        }
    }
    ```

    Let's assume that we trigger `BarAction` two times.
    We use random amount of seconds for waiting.
    Since we use `Unordered` as policy `on<BarAction>` the handler block gets executed 2 times without canceling the previous one (that is the difference  to `CANCEL_PREVIOUS`).
    Moreover, `Unordered` doesn't make any promise on order of execution of the block (see `Ordered` if you need promises on order).
    If `on<BarAction>` gets executed two times it will run in parallel and the the second execution
    could complete before the first execution (because using a random time of waiting).

- `Ordered`: In contrast to `Unordered` and `CancelPrevious`, `Ordered` will not run `on<BarAction>` in parallel and will not cancel any previous execution. Instead, `Ordered` will preserve the order.
- `Throttled(duration: Duration)`: This policy will throttle the execution of the block to the specified duration. This means that if the action is triggered more than once within the specified duration,
  only the first one will be executed.

`on<Action>` and `collectWhileInstate()` as well as their effect counter parts can specify an `ExecutionPolicy`:

- `on<Action>(executionPolicy = ExecutionPolicy.CancelPrevious) { ... }`
- `onActionEffect<Action>(executionPolicy = ExecutionPolicy.CancelPrevious) { ... }`
- `collectWhileInState(executionPolicy = ExecutionPolicy.CancelPrevious) { ... }`
- `collectWhileInStateEffect(executionPolicy = ExecutionPolicy.CancelPrevious) { ... }`

Please note that `onEnter` doesn't have the option to specify `ExecutionPolicy`.
