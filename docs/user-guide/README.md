# User Guide
FlowRedux comes with a powerful yet convinient and concise way to define state machines. 

In the following chapters we cover the core concepts such as `State`, `Action` and the DSL primitives.
We explain these concepts by implementing a real world example.
Therefore, we recommend reading each sub section as the overall example is built step by step by introducing new concepts.

1. [FlowReduxStateMachine Basics: State and Action](FlowReduxStateMachine.md)
2. [inState](inState.md)
3. [onEnter](onEnter.md)
4. [State`<T>` and ChangedState`<T>`](State-ChangedState.md)
5. [on`<Action>`](onAction.md)
6. [collectWhileInState](collectWhileInState.md)
7. [Effects](effects.md)
8. [Conditions inside inState](condition.md)
9. [untilIdentityChanged](untilIdentityChanged.md)
10. [Acting accross multiple states](accross-multiple-states.md)
11. [ExecutionPolicy](ExecutionPolicy.md)
12. [Improve readability of your DSL spec](improve-readability.md)
13. [Composing state machines (hierarchical state machines): onActionStartStateMachine and onEnterStartStateMachine)](composing-statemachines.md)
14. [Writing Tests for your FlowReduxStateMachine](testing.md)
