# ExecutionPolicy

Have you ever wondered what would happen if you execute an `Action` very fast, one after another?
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

The example above shows a problem with asynchronous state machines like FlowRedux:
If our state machine is in `FooState` and a `BarAction` gets triggered, we wait for 5 seconds and then set the state to another state.
What if, while waiting 5 seconds (let's say after 3 seconds of waiting), another `BarAction` gets triggered?
That is possible, right?
With `ExecutionPolicy` you can specify what should happen in that case.
There are three options to choose from:

- `CancelPrevious`: **This is the default automatically applied unless specified otherwise.** It cancels any previous
  execution and just runs the latest one. In the example mentioned, it means the previously still-running `BarAction` handler
  gets canceled and a new one with the latest `BarAction` starts.
- `Unordered`: Choosing this causes all the blocks to continue running but there are no guarantees in which order. For example:

    ```kotlin
    spec {
        inState<FooState> {
            on<BarAction>(executionPolicy = ExecutionPolicy.Unordered) {
                delay(randomInt()) // wait for some random time
                override { OtherState }
            }
        }
    }
    ```

    Let's assume that we trigger `BarAction` two times.
    We use a random amount of seconds for waiting.
    Since we use `Unordered` as the policy for `on<BarAction>`, the handler block gets executed twice without canceling the previous one (that is the difference to `CancelPrevious`).
    Moreover, `Unordered` doesn't make any promise on the order of execution of the block (see `Ordered` if you need guarantees on order).
    If `on<BarAction>` gets executed two times, it will run in parallel and the second execution
    could complete before the first execution (because of the random waiting time).

- `Ordered`: In contrast to `Unordered` and `CancelPrevious`, `Ordered` will not run `on<BarAction>` in parallel and will not cancel any previous execution. Instead, `Ordered` will preserve the order.
- `Throttled(duration: Duration)`: This policy will throttle the execution of the block to the specified duration. This means that if the action is triggered more than once within the specified duration,
  only the first one will be executed.

`on<Action>` and `collectWhileInState()` as well as their effect counterparts can specify an `ExecutionPolicy`:

- `on<Action>(executionPolicy = ExecutionPolicy.CancelPrevious) { ... }`
- `onActionEffect<Action>(executionPolicy = ExecutionPolicy.CancelPrevious) { ... }`
- `collectWhileInState(executionPolicy = ExecutionPolicy.CancelPrevious) { ... }`
- `collectWhileInStateEffect(executionPolicy = ExecutionPolicy.CancelPrevious) { ... }`

Please note that `onEnter` doesn't have the option to specify `ExecutionPolicy`.
