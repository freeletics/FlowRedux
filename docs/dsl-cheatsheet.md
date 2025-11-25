---
hide:
  - navigation
  - toc
---

# DSL Cheatsheet

If you want to learn more about a particular part of the DSL, we recommend taking a look at the [user guide](/user-guide/).

The following section describes the syntax and usage of the DSL blocks:

```kotlin
spec {
  inState<State1>{ // inState is always a "top level" element

    // Handle external "input", called Actions
    on<Action1> { action -> ... } // Handle an action
    on<Action2>(ExecutionPolicy) { action -> ... } // You can have multiple on<Action> blocks. Optionally specify ExecutionPolicy

    // Do something when you enter the state.
    onEnter { ... } // Called exactly one time when the given state has been entered
    onEnter { ... } // You can have multiple onEnter blocks

    // Collect a Flow (from kotlinx.coroutines) as long as the state machine is in the state (see inState<State>)
    collectWhileInState(flow1) { valueEmittedFromFlow -> ... } // Stops flow collection when state is left
    collectWhileInState(flow2) { valueEmittedFromFlow -> ... } // You can have multiple collectWhileInState

    // Effects to do something without changing the state (i.e. logging, analytics, ...)
    onActionEffect<Action1> { action -> ... } // You can have multiple onActionEffect
    onEnterEffect { ... } // You can have multiple onEnterEffect
    collectWhileInStateEffect(flow1) { valueEmittedFromFlow -> ... } // You can have multiple collectWhileInState

    // Hierarchical state machines
    onEnterStartStateMachine(
      stateMachineFactoryBuilder = { stateSnapshot : State1  -> OtherStateMachine() },
    ) { otherStateMachineState : OtherState -> ... }
    onEnterStartStateMachine(...) // You can have multiple onEnterStartStateMachine
    onActionStartStateMachine(...) // You can have multiple onActionStartStateMachine

    untilIdentityChanged({ state.id }) {
      // Everything inside this block executes only as long as the "identity" (in this example state.id)
      // doesn't change. When it changes, then the previous executions will be canceled and
      // this block starts again but with the changed state

      // You can have multiple of the DSL blocks, i.e. multiple on<Action> blocks and so on.
      on<Action3> { action -> ... } // You can have multiple on<Action>
      onEnter { ... }
      collectWhileInState(flow) { valueEmittedFromFlow -> ... }
      onActionEffect { action -> ...}
      onEnterEffect { ... }
      collectWhileInStateEffect(flow) { valueEmittedFromFlow -> ... }
      onEnterStartStateMachine(...)
      onActionStartStateMachine(...)
    }

    // Custom conditions
    condition({ state.someString == "Hello" }){
      // Everything inside this block only executes if the surrounding condition is met
      // and the state machine is in the state as specified by the top level inState<State1>.

      // You can have each DSL block multiple times, i.e. multiple on<Action> blocks and so on.
      on<Action3> { action -> ... }
      onEnter { ... }
      collectWhileInState(flow) { valueEmittedFromFlow -> ... }
      onActionEffect { action -> ...}
      onEnterEffect { ... }
      collectWhileInStateEffect(flow) { valueEmittedFromFlow -> ... }
      onEnterStartStateMachine(...)
      onActionStartStateMachine(...)

      untilIdentityChanged(...) { // Version of untilIdentityChanged that is only run if the condition block is active
        on<Action3> { action -> ... }
        onEnter { ... }
        collectWhileInState(flow) { valueEmittedFromFlow -> ... }
        onActionEffect { action -> ...}
        onEnterEffect { ... }
        collectWhileInStateEffect(flow) { valueEmittedFromFlow -> ... }
        onEnterStartStateMachine(...)
        onActionStartStateMachine(...)

        // Please note that you cannot have a condition block inside an untilIdentityChanged block
      }

      // Please note that you cannot have nested conditions inside a condition block
    }
  }

  inState<State2> { ... } // You can have multiple "top level" inState blocks
}
```
