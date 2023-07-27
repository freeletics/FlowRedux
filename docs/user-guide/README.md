# User Guide
FlowRedux comes with a powerful yet convinient and concise way to define state machines. 

In the following chapters we cover the core concepts such as `State`, `Action` and the DSL primitives.
We explain these concepts by implementing a real world example.
Therefore, we recommend reading each sub section as the overall example is built step by step by introducing new concepts.

1. [FlowReduxStateMachine Basics: State and Action](1_FlowReduxStateMachine.md)
2. [inState](2_inState.md)
3. [onEnter](3_onEnter.md)
4. [State`<T>` and ChangedState`<T>`](4_State-ChangedState.md)
5. [on`<Action>`](5_onAction.md)
6. [collectWhileInState](6_collectWhileInState.md)
7. [Effects](7_effects.md)
8. [Conditions inside inState](8_condition.md)
9. [untilIdentityChanged](9_untilIdentityChanged.md)
10. [Acting accross multiple states](10_accross-multiple-states.md)
11. [ExecutionPolicy](11_ExecutionPolicy.md)
12. [Improve readability of your DSL spec](12_improve-readability.md)
13. [Composing state machines (hierarchical state machines): onActionStartStateMachine and onEnterStartStateMachine)](13_composing-statemachines.md)
14. [Writing Tests for your FlowReduxStateMachine](14_testing.md)
