# FlowRedux

Building async. running Kotlin Multiplatform state machine made easy with a DSL and coroutines.

## Usage

Full documentation and best practices can be found here: https://freeletics.github.io/FlowRedux/dsl/

```kotlin
sealed interface State

object Loading : State
data class ContentState(val items : List<Item>) : State
data class Error(val error : Throwable) : State


sealed interface Action
object RetryLoadingAction : Action


class MyStateMachine : FlowReduxStateMachine<State, Action>(initialState = Loading){
    init {
        spec {
            inState<Loading> {
                onEnter { state : State<Loading> ->
                    // executes this block whenever we enter Loading state
                    try {
                        val items = loadItems() // suspending function / coroutine to load items
                        state.override { ContentState(items) } // Transition to ContentState
                    } catch (t : Throwable) {
                        state.override { Error(t) } // Transition to Error state
                    }
                }
            }

            inState<Error> {
                on<RetryLoadingAction> { action : RetryLoadingAction, state : State<Error> ->
                    // executes this block whenever Error state is current state and RetryLoadingAction is emitted
                    state.override { Loading } // Transition to Loading state which loads list again
                 }
            }

            inState<ContentState> {
                collectWhileInState( flowOf(1,2,3) ) { value : Int, state : State<ContentState> ->
                    // observes the given flow as long as state is ContentState.
                    // Once state is changed to another state the flow will automatically
                    // stop emitting.
                    state.mutate {
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
    statemachine.dispatch(action)
}
```

In an Android Application you could use it with AndroidX `ViewModel` like that:

```kotlin
class MyViewModel @Inject constructor(private val stateMachine : MyStateMachine) : ViewModel() {
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
There are two artifacts that you can include as dependencis:

1. `flowredux`: this is the core library and includes the DSL.
2. `compose`: contains some convenient extensions to work with `FlowReduxStateMachine` in [Jetpack Compose](https://developer.android.com/jetpack/compose).

[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/freeletics/flowredux?logo=github&sort=semver)](https://github.com/freeletics/flowredux/releases)

### JVM / Android only
```groovy
implementation 'com.freeletics.flowredux:flowredux-jvm:<latest-version>'
implementation 'com.freeletics.flowredux:compose:<latest-version>'
```

### Multiplatform
```groovy
implementation 'com.freeletics.flowredux:flowredux:<latest-version>'
```

FlowRedux is supported on:

- JVM / Android
- iOS
- watchOS
- tvOS
- macOS
- Linux
- Windows

We do plan to add support for JavaScript but it’s not available yet.

### Snapshot
Latest snapshot (directly published from `main` branch from CI on each change):

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

Then just use `-SNAPSHOT`suffix as version name like

```groovy
implementation 'com.freeletics.flowredux:flowredux:1.1.1-SNAPSHOT'
```
