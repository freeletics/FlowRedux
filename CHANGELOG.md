Change Log
==========

## 1.1.1 **UNRELEASED**
​
-
​
​
## 1.1.0 *(2022-05-08)*
​
- Updated to Kotlin 1.8.21 and Coroutines 1.7.0.
- Added support for Kotlin/JS
- Added support for all [tier 1, 2 and 3 Kotlin/Native targets](https://kotlinlang.org/docs/native-target-support.html)
- Removed the `@FlowPreview` opt in annotation, `@ExperimentalCoroutinesApi` is still required.
- The sub state machine DSL methods now take `StateMachine` instead of `FlowReduxStateMachine` as parameter.
  This allows using different kinds of state machines together with FlowRedux which might ease migrating
  a state machine to FlowRedux.
- A few methods which already had reified overloads are now hidden from auto complete (this change is binary
  compatible).
- Fixed race condition when using sub state machines that could cause a crash when an action is dispatched 
  shortly after the sub state machine starts.
​
​
## 1.0.2 *(2022-11-15)*

- Support optional values in the `actionMapper` of sub state machines. When the mapper returns null the action won't be forwarded which removes the need to either handle all
  actions in the sub state machine or introduce a no op action.


## 1.0.1 *(2022-11-04)*

- Dependency updates


## 1.0.0 *(2022-07-06)*

- Remove deprecated `ChangeState` and other (deprecated friends)
- Add DSL marker
- Restructure, update and extend docs


## 0.13.0 *(2022-06-13)*

**New State object**

Previously all the methods in the DSL had a `stateSnapshot: YourStateClass` parameter, which represented the state machine's current state at the time the DSL method was called. For all non effect methods this parameter is now replaces by a state parameter that looks like this `state: State<YourStateClass>`. The new `State` class has a `snapshot` allowing access to the same value that you received before. It also has `.mutate { }` (returns the same type as the current state, usually used with copy), `.override { }` (to transition to another state subclass) and `.noChange { }` methods to create the `ChangeState` objects. So instead of doing `MutateState { ... }` you should now do `state.mutate { ... }`.

This has a few advantages:
- `State` knows about the `InputState` type so it's not possible anymore to accidentally create `MutateState` instances with the wrong input type, this is now enforced by the type system, before it would have crashed at runtime
- it's also not necessary anymore to specify the type parameters for mutate state, the generics are inferred


**Lambdas in the DSL**

We now recommend to use lambdas instead of method references in the flow redux DSL  (e.g. `on<ButtonClicked> { action, state -> transitionToSomeState(action, state) }` over `on(::buttonClicked`/`on(::transitionToSomeState)`) because:
 - Parameters of the reference are required to be there but the compiler warns when they are unused, in a lambda you can just use `_` and remove the parameter from the method.
- The function name should describe what it does (`doSomething`) but in practice we usually see it being named after the action (e.g. `on(::buttonClicked)`). The assumption is that if you have `on<ButtonClicked> { ... }` you are less inclined to call the method `buttonClicked` as well.
- An addition to the above is that if you do `on(::doSomething)` you lose the information of when it happens while `on<ButtonClicked> { transitionToSomeState(it) }` would make getting an overview easier. Of course you can combine both in your function name but that leads to long/weird names. For example `onButtonClickedTransitionToSomeState` is long and would then result in `on(::on...)` while leaving out the `on` from the method name reads weirdly when looking at just the function.
- Method references don't work well when the method has a dependency. In practice the function is just put into the state machine class which means it's not testable on its own anymore. While we introduced functional interfaces in [#189].(https://github.com/freeletics/FlowRedux/issues/189) to make this easier, they are also a lot more verbose. If you just call a method from the lambda you can pass additional parameters to it and it keeps the syntax more aligned.
- In some circumstances (not sure when it happens) the IDE can't really auto complete the referenced function when you're writing `::...`


**Other changes**
- Renamed `stateMachine` DSL method to `onEnterStartStateMachine`
- `initialStateSupplier` is now called whenever the state collection starts
- The initial state in the compose integration is now `null`
- Don't swallow ClassCastExceptions from the handler
- The state machine will now throw an exception when state is collecting more than once at the same time #308
- `ChangeState.reduce` is now public for testing purposes #309


## 0.12.0 *(2022-03-21)*

**Breaking changes:**

- Removed `dsl` artifact (and gradle module).`FlowReduxStateMachine` and all DSL stuff now lives inside `flowredux` artifact. Change your maven coordintaes
  from `com.freeletics.flowredux:dsl:0.11.0` to `com.freeletics.flowredux:flowredux:0.12.0`. Package names renamed the same so no breaking change at code level. This is just a
  breaking change at artifact and packaging level.
- Removes Logger as we don't need it

**Fixed:**

- Add usage of atomic variables
- remove obsolete experimental annotations


## 0.11.0 *(2021-11-23)*

**New**

- Convenience artefact to work with jetpack compose: `FlowReduxStateMachine.rememberStateAndDispatch()`

```kotlin
val stateMachine = MyFlowReduxStateMachine()

@Composable
fun MyUi(){
  val (state, dispatch) = stateMachine.rememberStateAndDispatch()
  ...
}
```

- Overload for sub-statemachine


## 0.10.0 *(2021-10-27)*

**New:**
- support for composable child state machines
- Support for Apple Silicon targets
- Added a check that throws an exception if `FlowReduxStateMachine.dispatch(action)` is called before collecting `FlowReduxStateMachine.state`

**Breaking API change:**
- `FlatMapPolicy` is renamed to `ExecutionPolicy`

**Fix:**
- fix a crash caused by resubscribing to a state machine (introduced in 0.9.0)


## 0.9.0 *(2021-10-20)*

**Breaking API changes:**
- `FlowReduxStateMachine` is not using `StateFlow` anymore. That makes the Statemachine "cold" again.

**Addition:**
- Turned type alias for DSL lambdas into `fun interface`


## 0.8.0 *(2021-08-25)*

**New:**
- Introduced `onActionEffect`, `onEnterEffect` and `onCollectWhileInStateEffect` to do some work as a sort of "side effect" without changing the state
- Introduced `collectWhileInState(flowBuilder: (Flow<State> -> Flow<Value>)`
- Overloads for `collectWhileInstate` and `on<Action>` to pass in function references without the need of specifying `FlatMapPolicy` or explicitly use named argument for handler.


## 0.7.0 *(2021-08-02)*

A bunch of Bug fixes, please update!

**Fixes:**
- Cancel `onEnter` block if state is left
- Cancel `onAction` block if state is left
- Cancel `collectWhileInState` block if state is left
- Fixed unit tests to run with multithreaded dispatcher

**API changes:**
- Removed FlatMapPolicy from `onEnter`
- Removed  `collectWhileInAnyState`. Use `inState<RootClassFromStateHierarchy> { collectWhileInState(flow) {... }  } ` instead to get to the same endresult.


## 0.6.0 *(2021-06-21)*

- Some internal improvements mainly around removing `Channel` and replacing it with  `MutableSharedFlow`


## 0.5.0 *(2021-05-12)*

This is a major change and milestone towards 1.0 release

This release contains breaking changes

- `ChangeState` is return type for all DSL blocks such as `onEnter{ ... }`. It replaces `setState{ ... }` to trigger state transitions. Furthermore, this allows us to easily write functions that can be unit tested more easy compared to how it worked before (`setState` and 'getState'). A function signature i.e. to handle an action looks as follows:
 `fun handleAction(action : MyAction, stateSnapshot : MyState) : ChangeState<State> `
- `getState' has been removed as it's not needed anymore because `ChangeState` replaces it.
- `inStateWithCondition` replaces `inState(condition : (State) -> Boolean)' to avoid issues with overloads and type erasure on jvm.


## 0.4.0 *(2020-10-01)*

 - Compiled with Kotlin 1.4.10 (binary compatible with Kotlin 1.4.0)

**Breaking change**

Artifact coordinates did change:
For multiplatform are from now on
```groovy
implementation 'com.freeletics.flowredux:flowredux:0.4.0'
implementation 'com.freeletics.flowredux:dsl:0.4.0'
```
and for jvm:
```groovy
implementation 'com.freeletics.flowredux:flowredux-jvm:0.4.0'
implementation 'com.freeletics.flowredux:dsl-jvm:0.4.0'
```
This is more streamlined now with kotlin multiplatform library packaging best practices.


## 0.3.0 *(2020-02-14)*

**New**
- Added `setState (runIf: (State) -> Boolean ) { ... }` where in `runIf` you can specify if this setState block should actually run or not. Per default it will only run if you are still in the state specified in `inState`
- Added a generic way to define `inState(isInState = (State) -> Boolean) { ... }` in addintion to `isInState<State>`.

**Breaking changes**
- Renamed `observeWhileInState` to `collectWhileInState()` #63
- Renamed `observe()` to `collectInAnyState()`
- Renamed type alias `StateAccessor` to `GetState`

**Improvement**
- Don't package test libraries in jvm dsl jar artifact #54


## 0.2.1 *(2020-02-02)*

- Multiplatform release for iOS, jvm, watchOS and TvOS. JavaScript not included yet.
- Renamed `library` artifact to `flowredux`
- introduces `inState<> { onEnter() }` to DSL
- Added a sample app for android and iOS (using SwiftUI).


## 0.1.0 *(2019-10-24)*

First official release targeting JVM only (Multiplatform coming soon). Contains

 - FlowrRedux: the core library (think of it as the low level API)
 - FlowRedux-DSL: A fluend DSL to describe your ReduxStore (think of it as the high level API)l
