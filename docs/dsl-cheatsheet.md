# DSL Cheatsheet

If you want to learn more about a particular part of the DSL, we recommend taking a look at the [user guide](user-guide/).

The following section describes the syntax and usage of the DSL blocks:

```kotlin
spec {
    inState<State1>{ // inState is always a "top level" element

        // Handle external "input", called Actions
        on<Action1>{ action, state -> ... } //  Handle an action
        on<Action2>(ExecutionPolicy){ action, state -> ... } //  You can have multiple on<Action> blocks. Optionally specify ExecutionPolicy
       
        // Do something when you enter the state.
        onEnter{ state -> ... } // called exactly one time when the given state has been entered
        onEnter{ state -> ... } // You can have multiple onEnter blocks
        
        // Collect a Flow (from coroutine package) as long as the state machine is in the state (see inState<State>)
        collectWhileInstate(flow1) { valueEmitedFromFlow, state -> ... } // stops flow collection when state is left
        collectWhileInstate(flow2) { valueEmitedFromFlow, state -> ... } // You can have multiple collectWhileInstate

        // Effects to do something without changing the state (i.e. logging, analytics, ...)
        onActionEffect<Action1>{ action, state -> ... } // You can have multiple onActionEffect
        onEnterEffect{ state -> ... } // You can have multiple onEnterEffect
        collectWhileInStateEffect(flow1){ valueEmitedFromFlow, state -> ... } // You can have multiple collectWhileInstate

        // hierarchical state machines
        onEnterStartStateMachine(
            stateMachineFactory = { stateSnapshot : State1  -> OtherStateMachine() },
            stateMapper = { state : State<State1>, otherStateMachineState : OtherState ->
                ...
            }
        )     
        onActionStartStateMachine<Action1>(
            stateMachineFactory = { action, stateSnapshot : State1 -> OtherStateMachine() },
            stateMapper = { state : State<State1>, otherStateMachineState :OtherState ->
                    ...
                }
            )
        onEnterStartStateMachine(...) // You can have multiple onEnterStartStateMachine
        onActionStartStateMachine(...) // You can have multiple onActionStartStateMachine

        // Custom conditions
        condition({state.someString == "Hello" }){
            // Everything inside this block only executes if the surounding condition is met 
            // and the state machine is in the state as specified by the top level inState<State1>.
            // 
            //  you can have multiple of the dsl blocks, i.e. multiple on<Action> blocks and so on.
            on<Action3>{ action, state -> ... } // you can have multiple on<Action>
            onEnter{ state -> ... }
            collectWhileInState(flow){ valueEmitedFromFlow, state -> ... } 
            onActionEffect{ action, state -> ...}
            onEnterEffect{ state -> ... }
            collectWhileInStateEffect(flow){ valueEmitedFromFlow, state -> ... } 
            onEnterStartStateMachine(...)
            onActionStartStateMachine(...)

            untilIdentityChanged({ state.id }) {
                // Everyhting inside this block executes only as long as the "identity" (in this example state.id)
                // doesn't change. When it changes, then the previous executions will be canceled and 
                // this block starts again but with the changed state
                //
                //  you can have multiple of the dsl blocks, i.e. multiple on<Action> blocks and so on.
                on<Action3>{ action, state -> ... } // you can have multiple on<Action>
                onEnter{ state -> ... }
                collectWhileInState(flow){ valueEmitedFromFlow, state -> ... } 
                onActionEffect{ action, state -> ...}
                onEnterEffect{ state -> ... }
                collectWhileInStateEffect(flow){ valueEmitedFromFlow, state -> ... } 
                onEnterStartStateMachine(...)
                onActionStartStateMachine(...)

                // Please note that you cannot have a condition block inside an untilIdentityChanged block
            }

            // Please note taht you cannot have nested conditions inside a condition block
        }
    }

    inState<State2> { ... } // You can have multiple "top level" inState blocks
}
```