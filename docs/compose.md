# Extensions for Jetpack Compose

This package provides some functions that may be useful if you use Jetpack Compose.


### `produceStateMachine()`

Let's say we have a very basic address book UI build with Jetpack Compose
and with a FlowRedux powered state machine.

Let's take a look at this over-simplified code sample:

```kotlin
val stateMachineFactory = AddressBookStateMachineFactory()

@Compose
fun AddressBookUi(){
  // Extension function that is provided by FlowRedux where the `state` of the
  // create `StateMachine` is a Compose `State`.
  val stateMachine = stateMachineFactory.produceStateMachine()
  val state = stateMachine.state.value
  Column {
    SearchBoxUi(state.searchQuery, dispatch)
  }

  LazyColumn {
    items(state.contacts) { contact : Contact ->
       ContactUi(contact, stateMachine::dispatch)
    }
  }
}

@Compose
fun SearchBoxUi(searchQuery : String, dispatchAction: (AddressBookAction) -> Unit) {
    Column {
      TextField(
        value = searchQuery,
        // dispatches action async to state machine
        onValueChange = { text -> dispatchAction(SearchQueryChangedAction(text)) }
      )
   }
}
```
