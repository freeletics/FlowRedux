# inScopedState

Large states often contain behavior that only needs a subset of the state. `inScopedState` lets you write that behavior against a projected state instead of the complete state. The projection can be an existing nested property or a new type assembled from multiple properties.

Consider a camera state with a nested capture state:

```kotlin
data class CameraState(
    val capture: CaptureState = CaptureState(),
    val focusLocked: Boolean = false,
)

data class CaptureState(
    val pendingCaptures: Int = 0,
)

sealed interface CameraAction
data object ShutterPressed : CameraAction
```

The capture behavior can be scoped inside `inState`:

```kotlin
spec {
    inState<CameraState> {
        inScopedState(
            get = { it.capture },
            set = { copy(capture = it) },
        ) {
            on<ShutterPressed> {
                mutate {
                    copy(pendingCaptures = pendingCaptures + 1)
                }
            }
        }
    }
}
```

Inside the block, `snapshot`, `mutate`, conditions, identities, and flow builders use `CaptureState`. They cannot access `CameraState.focusLocked`, which makes the chosen state boundary explicit.

## Getter and setter

`inScopedState` requires two mapping functions:

- `get` projects a scoped state from the current `inState` state.
- `set` applies an updated scoped state to the current `inState` state.

For a scope over one property, the getter and setter should refer to the same property. More generally, `set` should write back every mutable value represented by `get` and preserve all values outside the projection.

FlowRedux does not retain a separate copy of the scoped state. When a scoped handler produces a change, FlowRedux extracts the scoped value from the latest state, reduces the scoped change, and applies the result through `set`. This means a suspended scoped handler does not overwrite changes made to sibling properties while it was running.

## Projecting multiple properties

A scope does not need to correspond to one property in the parent state. You can create a purpose-built state that combines an area-specific slice with shared properties needed by its behavior:

```kotlin
data class CaptureScopeState(
    val capture: CaptureState,
    val focusLocked: Boolean,
)

spec {
    inState<CameraState> {
        inScopedState(
            get = {
                CaptureScopeState(
                    capture = it.capture,
                    focusLocked = it.focusLocked,
                )
            },
            set = { scoped ->
                copy(
                    capture = scoped.capture,
                    focusLocked = scoped.focusLocked,
                )
            },
        ) {
            on<ShutterPressed> {
                if (snapshot.focusLocked) {
                    mutate {
                        copy(
                            capture = capture.copy(
                                pendingCaptures = capture.pendingCaptures + 1,
                            ),
                        )
                    }
                } else {
                    noChange()
                }
            }
        }
    }
}
```

`CaptureScopeState` is not stored separately. `get` creates it from the latest `CameraState` whenever FlowRedux needs the scoped state, and `set` maps a scoped update back into the latest `CameraState`. This is useful when several parent-state variants expose common properties required by a reusable scoped specification.

Treat `get` and `set` as a bidirectional state mapping. Applying an unchanged projection should leave the parent unchanged, and projecting a parent after applying a scoped update should return that update. If `set` ignores a projected property, changes to that property inside the scope will not persist.

## Supported DSL

The scoped builder supports the regular state DSL:

- `on` and `onActionEffect`
- `onEnter` and `onEnterEffect`
- `collectWhileInState` and `collectWhileInStateEffect`
- `onEnterStartStateMachine` and `onActionStartStateMachine`
- `condition`
- `untilIdentityChanges`

Actions are not scoped or mapped. They keep their normal type filtering and broadcast semantics, so the same action can be handled by root behavior and by multiple scoped blocks.

Scoped handlers remain active only while the surrounding `inState` and any scoped `condition` or `untilIdentityChanges` block remain active. Leaving one of those boundaries cancels ongoing handlers and flow collections normally.

## Reusing a scoped specification

You can declare the state mapping once in an application-specific extension:

```kotlin
fun InStateBuilder<CameraState, CameraState, CameraAction>.capture(
    block: ScopedStateBuilder<CaptureState, CameraAction>.() -> Unit,
) {
    inScopedState(
        get = { it.capture },
        set = { copy(capture = it) },
        block = block,
    )
}
```

The specification then focuses only on capture behavior:

```kotlin
spec {
    inState<CameraState> {
        capture {
            on<ShutterPressed> {
                mutate {
                    copy(pendingCaptures = pendingCaptures + 1)
                }
            }
        }
    }
}
```

This also allows the behavior inside `capture` to be reused with another root state by providing a different mapping. The same approach works with a purpose-built projection when the reusable behavior needs multiple properties.

## When not to use a scope

Using multiple properties does not by itself require root-scoped behavior. Prefer a derived scope when those properties form one cohesive, reusable boundary. Use root-scoped behavior when an operation coordinates unrelated concerns or needs access to the complete parent state. For example, switching cameras might reset several otherwise independent areas atomically:

```kotlin
on<SwitchCamera> {
    mutate {
        copy(
            capture = CaptureState(),
            focusLocked = false,
        )
    }
}
```

`inScopedState` does not create a child state machine or store, scope actions, or provide a way to transition to another root-state type. Its purpose is to isolate behavior to an explicit state projection, whether that projection is one nested value or a new state assembled from several parent properties.
