# FlowRedux

[![CircleCI](https://circleci.com/gh/freeletics/FlowRedux.svg?style=svg)](https://circleci.com/gh/freeletics/FlowFedux)
[![Download](https://maven-badges.herokuapp.com/maven-central/com.freeletics.flowredux/flowredux/badge.svg) ](https://maven-badges.herokuapp.com/maven-central/com.freeletics.flowredux/flowredux)

Building kotlin multiplatform StateMachine made easy with DSL and coroutines.

## Usage

```kotlin
sealed class State

object LoadingState : State()
data class ContentState(val items : List<Item>) : State()
data class ErrorState(val error : Throwable) : State()


sealed class Action
object RetryLoadingAction : Action()


class MyStateMachine : FlowReduxStateMachine<State, Action>(LoadingState){
    init {
        spec {
            inState<LoadingState> {
                onEnter  { getState, setState ->
                    // executes this block whenever we enter LoadingState
                    try {
                        val items = loadItems() // suspending function / coroutine to load items
                        setState { ContentState(items) } // Transition to ContentState
                    } catch (t : Throwable) {
                        setState { ErrorState(t) } // Transition to ErrorState
                    }
                }
            }

            inState<ErrorState> {
                on<RetryLoadingAction> { action, getState, setState ->
                    // executes this block whenever
                    // ErrorState is current state and RetryLoadingAction is emitted
                    setState { LoadingState } // Transition to LoadingState which loads list again
                 }
            }

            inState<ContentState> {
                observeWhileInState( flowOf(1,2,3) ) { getState, setState ->
                    // observes the given flow as long as state is ContentState.
                    // Once state is changed to another state the flow will automatically
                    // stop emitting.
                }
            }
        }
    }
}
```

```kotlin
val statemachine = MyStateMachine()

launch {  // Launch a coroutine
    statemachine.state.collect { state ->
      // do something with new state like update UI
      renderUI(state)
    }
}

// emit an Action
launch { // Launch a coroutine
    statemachine.dispatch(Action)
}
```

In an Android Application you would use it with AndroidX `ViewModel` like that:

```kotlin
class MyViewModel @Inject constructor(private val stateMachine : StateMachine) : ViewModel() {
    val state : LiveData<State> = MutableLiveData<State>()

    init {
        viewModelScope.launch { // automatically canceled once ViewModel lifecycle reached destroyed.
            stateMachine.state.collect { newState ->
                state.value = newState
            }
        }
    }

    fun dispatch(action : Action) {
        viewModelScope.launch {
            stateMachine.dispatch(action)
        }
    }
}
```

## Dependencies
There are two artifacts that you can include as dependency::
1. `flowredux`: this is the core library. Usually you dont want to use the core library directly but rather use the `dsl`.
2. `dsl` which provides a convenient DSL on top of the core library. Usually this is what you want.

```groovy
implementation 'com.freeletics.flowredux:flowredux-multiplatform:0.2.1'
implementation 'com.freeletics.flowredux:dsl-multiplatform:0.2.1'
```

### JVM only
```groovy
implementation 'com.freeletics.flowredux:flowredux:0.2.1'
implementation 'com.freeletics.flowredux:dsl:0.2.1'
```

### Native binaries
```groovy
implementation 'com.freeletics.flowredux:flowredux-iosx64:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-iosarm64:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-iosarm32:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-watchosx86:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-watchosarm64:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-watchosarm32:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-tvosx64:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-tvosxarm64:0.2.1'

implementation 'com.freeletics.flowredux:dsl-iosx64:0.2.1'
implementation 'com.freeletics.flowredux:dsl-iosarm64:0.2.1'
implementation 'com.freeletics.flowredux:dsl-iosarm32:0.2.1'
implementation 'com.freeletics.flowredux:dsl-watchosx86:0.2.1'
implementation 'com.freeletics.flowredux:dsl-watchosarm64:0.2.1'
implementation 'com.freeletics.flowredux:dsl-watchosarm32:0.2.1'
implementation 'com.freeletics.flowredux:dsl-tvosx64:0.2.1'
implementation 'com.freeletics.flowredux:dsl-tvosxarm64:0.2.1'
```

### JavaScript
No javascript version release yet but its on our TODO list.


#### Snapshot
Latest snapshot (directly published from master branch from Travis CI):

```groovy
allprojects {
    repositories {
        // Your repositories.
        // ...
        // Add url to snapshot repository
        maven {
            url "https://oss.sonatype.org/content/repositories/snapshots/"
        }
    }
}
```

Then just use `-SNAPSHOT`suffix as version like
```groovy
implementation 'com.freeletics.flowredux:dsl:0.2.2-SNAPSHOT'
```
