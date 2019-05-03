# RxRedux Pagination example

This example shows an app that loads a list of popular repositories (number of stars) on Github.
It users github api endpoint to query popular repositories.
Github doesn't give us the whole list of repositories in one single response but offers pagination
to load the next page once you hit the end of the list and need more repositories to display.

![First page](https://github.com/freeletics/RxRedux/blob/master/sample/.readme-images/screen1.png?raw=true)
![Second page](https://github.com/freeletics/RxRedux/blob/master/sample/.readme-images/screen2.png?raw=true)

To implement that of course we use `RxRedux`. The User can trigger `LoadFirstPageAction` and
`LoadNextPageAction`.
This Actions are handled by:`
- `fun loadFirstPageSideEffect(action : Observable<Action>) : Observable<Action>`
- `fun loadNextPageSideEffect(action : Observable<Action>) : Observable<Action>`

Furthermore, if a error occurs while loading the next page an internal Action
(not triggered by the user)  `ErrorLoadingPageAction` is emitted
which is handled by another internal SideEffect:
`fun showAndHideLoadingErrorSideEffect(action : Observable<Action>) : Observable<Action>` takes care
of showing and hiding a `SnackBar` that is used to display an error on screen.


## SideEffects
As a user of this app scrolls to the end of the list, the next page of popular Github repositories is loaded.
The real deal with `RxRedux` is `SideEffect` (Action in, Actions out) as we will try to highlight in the following example (source code is available on [Github](https://github.com/freeletics/RxRedux/tree/master/sample)).

To set up our Redux Store with RxRedux we use `.reduxStore()`:

```kotlin
// Actions triggered by the user in the UI / View Layer
val userActions : Observable<Action> = ...

actionsFromUser
  .observeOn(Schedulers.io())
  .reduxStore(
    initialState = State.LOADING,
    sideEffects = listOf(::loadNextPageSideEffect, ::showAndHideErrorSnackbarSideEffect, ... ),
    reducer = ::reducer
  )
  .distinctUntilChanged()
  .subscribe { state -> view.render(state) }
```

For the sake of readability we just want to focus on two side effects in this blog post to highlight the how easy it is to compose (and reuse) functionality via `SideEffects` in `RxRedux` (but you can check the full sample code on [Github](https://github.com/freeletics/RxRedux/tree/master/sample))


```kotlin
fun loadNextPageSideEffect(actions : Observable<Action>, state : StateAccessor) : Observable<Action> =
  actions
    .ofType(LoadNextPageAction::class.java)
    .switchMap {
      val currentState : State = state()
      val nextPage : Int = currentState.page + 1

      githubApi.loadNextPage(nextPage)
        .map { repositoryList ->
          PageLoadedAction(repositoryList, nextPage) // Action with the loaded items as "payload"
        }
        .startWith( StartLoadingNextPageAction )
        .onErrorReturn { error -> ErrorLoadingPageAction(error) }
    }
```

Let's recap what `loadeNextPageSideEffect()` does:

 1. This `SideEffect` only triggers on `LoadNextPageAction` (emitted in `actionsFromUser`)
 2. Before making the http request this SideEffect emits a `StartLoadingNextPageAction`. This action runs through the `Reducer` and the output is a new State that causes the UI to display a loading indicator at the end of the list.
 3. Once the http request completes `PageLoadedAction` is emitted and processed by the `Reducer` as well to compute the new state. In other words: the loading indicator is hidden and the loaded data is added to the list of Github repositories displayed on the screen.
 4. If an error occures while making the http request, we catch it an emit a `ErrorLoadingPageAction`. We will see in a minute how we process this action (spoiler: with another SideEffect).

The state transitions (for the happy path - no http networking error) are reflected in the UI as follows:

![RxRedux](https://raw.githubusercontent.com/freeletics/RxRedux/master/sample/docs/sideeffect1-ui.png)


So let's talk how to handle the http networking error case.
In `RxRedux` a `SideEffect` emits `Actions`.
These Actions go through the Reducer but are alse piped back into the system.
That allows other `SideEffect` to react on `Actions` emitted by a `SideEffect`.
We do exactly that to show and hide a `Snackbar` in case that loading the next page fails.
Remember: `loadNextPageSideEffect` emits a `ErrorLoadingPageAction`.

```kotlin
fun showAndHideErrorSnackbarSideEffect(actions : Observable<Action>, state : StateAccessor) : Observable<Action> =
  actions
    .ofType(ErrorLoadingPageAction::class.java) // <-- HERE
    .switchMap { action ->
        Observable.timer(3, TimeUnit.SECONDS)
          .map { HideLoadNextPageErrorAction(action.error) }
          .startWith( ShowLoadNextPageErrorAction(action.error) )
    }
```

What `showAndHideErrorSnackbarSideEffect()` does is the following:

1. This side effect only triggers on `ErrorLoadingPageAction`
2. We show a Snackbar for 3 seconds on the screen by using `Observable.timer(3, SECONDS)`. We do that by emitting `ShowLoadNextPageErrorAction` first. `Reducer`will then change the state to show Snackbar.
3. After 3 seconds we emit `HideLoadNextPageErrorActionHideLoadNextPageErrorAction`. Again, the reducer takes care to compute new state that causes the UI to hide the Snackbar.

![RxRedux](https://raw.githubusercontent.com/freeletics/RxRedux/master/sample/docs/sideeffect2-ui.png)

Confused? Here is a (pseudo) sequence diagram that illustrates how action flows from SideEffect to other SideEffects and the Reducer:

![RxRedux](https://raw.githubusercontent.com/freeletics/RxRedux/master/sample/docs/pagination-sequence.png)

Please note that every Action goes through the `Reducer` first.
This is an explicit design choice to allow the `Reducer` to change state before `SideEffects` start.
If Reducer doesn't really care about an action (i.e. `ErrorLoadingPageAction`) Reducer just returns the previous State.

Of course one could say "why do you need this overhead just to display a Snackbar"?
The reason is that now this is testable.
Moreover, `showAndHideErrorSnackbarSideEffect()` can be reused.
For Example: If you add a new functionality like loading data from database, error handling is just emitting an Action and `showAndHideErrorSnackbarSideEffect()` will do the magic for you.
With `SideEffects` you can create a plugin system.

# Testing
Testing is fairly easy in a state machine based architecure because all you have to do trigger
input actions and then check for state changes caused by an action.
So at the end it's basically `assertEquals(expectedState, actualStates)`.

## Functional testing
Of course we could test our side effects and reducers individually.
However, since they are pure functions, we believe that writing functional tests for the whole system
adds more value then single unit tests.
Actually we have two kind of functional tests:

1. Functional tests that run on JVM: Here we basically have no real UI but just a mocked one that
records states that should be rendered over time. Eventually, this allows us to do `assertEquals(expectedState, recordedStates)`
2. Functional tests that run on real Android Device: Same idea as for functional tests on JVM, in this case, however, we run our tests on a real android device interacting with real android UI widgets. We use `ViewBinding` to interact with UI Widgets. While running the function tests we use a `RecordingViewBinding` that again records the state changes over time which then allows us to check `assertEquals(expectedState, recordedStates)`.

## Screenshot testing
Since our app is state driven and a state change also triggers a UI change, we can easily screenshot
test our app since we only have to wait until a state transition happen and then make a screenshot.
The procedure looks as follows

1. Record the screenshots with `./gradlew executeScreenshotTests -Precord`.
You have to run this whenever you change your UI on purpose.
2. Run verification with `./gradlew executeScreenshotTests`.
This runs the test and compares the screenshots with the previously recored screenshots (see step 1.)
3. See test report in `RxRedux/sample/build/reports/shot/verification/index.html`

Please keep in mind that you always have to use the same device to run your screenshot test.
The screenshots added to this repository have been taken from a Nexus 5X emulator (default settings) running Android API 26.

### Language Localization
We can go one step further and create screenshots for each language we support in our app.
We use [fastlane](https://fastlane.tools) for that. From command line run

```
fastlane screengrab
```

You can see the generated report in `fastlane/metadata/android/screeshots.html`.
This report can be used to do localization QA.
