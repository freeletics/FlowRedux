# FlowRedux

[![Download](https://maven-badges.herokuapp.com/maven-central/com.freeletics.flowredux/flowredux/badge.svg) ](https://maven-badges.herokuapp.com/maven-central/com.freeletics.flowredux/flowredux)

Building async. running Kotlin Multiplatform state machine made easy with a DSL and coroutines.

## Usage

Full documentation and best practices can be found here: https://freeletics.github.io/FlowRedux/


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
                onEnter { stateSnapshot : LoadingState ->
                    // executes this block whenever we enter LoadingState
                    try {
                        val items = loadItems() // suspending function / coroutine to load items
                        OverrideState( ContentState(items) ) // Transition to ContentState
                    } catch (t : Throwable) {
                        OverrideState( ErrorState(t) ) // Transition to ErrorState
                    }
                }
            }

            inState<ErrorState> {
                on<RetryLoadingAction> { action : RetryLoadingAction, stateSnapshot : ErrorState ->
                    // executes this block whenever ErrorState is current state and RetryLoadingAction is emitted
                    OverrideState( LoadingState ) // Transition to LoadingState which loads list again
                 }
            }

            inState<ContentState> {
                collectWhileInState( flowOf(1,2,3) ) { value : Int, stateSnapshot : ContentState ->
                    // observes the given flow as long as state is ContentState.
                    // Once state is changed to another state the flow will automatically
                    // stop emitting.
                    MutateState<ContentState, State> { 
                        copy( items = this.items + Item("New item $value"))
                    }
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

In an Android Application you could use it with AndroidX `ViewModel` like that:

```kotlin
class MyViewModel @Inject constructor(private val stateMachine : StateMachine) : ViewModel() {
    val state = MutableLiveData<State>()

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

### Multiplatform
```groovy
implementation 'com.freeletics.flowredux:flowredux:0.10.0'
implementation 'com.freeletics.flowredux:dsl:0.10.0'
```

### JVM only
```groovy
implementation 'com.freeletics.flowredux:flowredux-jvm:0.10.0'
implementation 'com.freeletics.flowredux:dsl-jvm:0.10.0'
```

### Native binaries
```groovy
implementation 'com.freeletics.flowredux:flowredux-iosx64:0.10.0'
implementation 'com.freeletics.flowredux:flowredux-iosarm64:0.10.0'
implementation 'com.freeletics.flowredux:flowredux-iosarm32:0.10.0'
implementation 'com.freeletics.flowredux:flowredux-watchosx86:0.10.0'
implementation 'com.freeletics.flowredux:flowredux-watchosarm64:0.10.0'
implementation 'com.freeletics.flowredux:flowredux-watchosarm32:0.10.0'
implementation 'com.freeletics.flowredux:flowredux-tvosx64:0.10.0'
implementation 'com.freeletics.flowredux:flowredux-tvosxarm64:0.10.0'

implementation 'com.freeletics.flowredux:dsl-iosx64:0.10.0'
implementation 'com.freeletics.flowredux:dsl-iosarm64:0.10.0'
implementation 'com.freeletics.flowredux:dsl-iosarm32:0.10.0'
implementation 'com.freeletics.flowredux:dsl-watchosx86:0.10.0'
implementation 'com.freeletics.flowredux:dsl-watchosarm64:0.10.0'
implementation 'com.freeletics.flowredux:dsl-watchosarm32:0.10.0'
implementation 'com.freeletics.flowredux:dsl-tvosx64:0.10.0'
implementation 'com.freeletics.flowredux:dsl-tvosxarm64:0.10.0'
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
implementation 'com.freeletics.flowredux:dsl:0.10.1-SNAPSHOT'
```
