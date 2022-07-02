# Extensions for jetpack compose

This package provides some functions that may be useful if you use Jetpack Compose.


### state and dispatch action
Let's say we have a very basic address book UI build with JetpackCompose
and with a FlowRedux powered state machine.

Let's take a look at this over-simplified code sample:

```kotlin
val stateMachine = AddressBookStateMachine()

@Compose
fun AddressBookUi(){
  // Extension function that is provided by this artifact
  val (state, dispatchAction) = stateMachine.rememberStateAndDispatch()
  Column {
    SearchBoxUi(state.searchQuery, dispatch)
  }

  LazyColumn {
    items(state.contacts) { contact : Contact ->
       ContactUi(contact, dispatchAction)
    }
  }
}

@Compose
fun SearchBoxUi(searchQuery : String, dispatchAction: (AddressBookAction) -> Unit) {
    Column {
      TextField(
        value = searchQuery,
        onValueChange = { text -> dispatchAction(SearchQueryChangedAction(text)) } // dispatches action async to state machine
      )
   }
}
```

`rememberStateAndDispatch()`, as the name already suggests, is remembered across recompositions.

### rememberState()
If you only need state of from your stateMachine but not an async way to dispatch actions
then `rememberState()` extension is what you are looking for.

```kotlin
import androidx.compose.runtime.State

val stateMachine = AddressBookStateMachine()

@Compose
fun MyUi(){
  val state : State<AddressBookState> = stateMachine.rememberState() // this returns Compose State
  LazyColumn {
      items(state.contacts) { contact : Contact ->
         ContactUi(contact)
      }
    }
}
```
