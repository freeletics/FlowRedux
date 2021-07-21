# Example: Pagination

Let's build a real world example with FlowRedux: 
let's build a news application that will load a list of news articles.
An `Article` has just an id, title and a url to an image:

```kotlin
data class Article (
  val id : Int,
  val title : String,
  val imageUrl : String
)
```


## States

Our app suppots pagination. 
We have a repository to load a "page" of articles:

```kotlin
class ArticlesRepository {
  suspend fun load(page : Int) : List<Article> { 
   
    val articles : List<Article> = ...  // does some http request to load articles
    return articles
}
```

Pagination means that if a user scrolls to the last `Article` in the UI, then the next "page" of Articles is loaded an displayed.
That means we have some sort of loading states, error states along with showing a list of articles on the screen. 
This translates to the following state classes:

```kotlin
sealed interface State

object FirstPageLoadingState : State
object FirstPageErrorState : State

data class ShowArticlesState(
  val articles : List<Article>,
  val loadingNextPageState : LoadNextPageState,
  internal val currentPage : Int
)

enum class LoadNextPageState {
  NOT_LOADING, // No loading in progress at the moment
  LOADING, // loading next page right now
  ERROR // Error has occurred while loading next page
}
```

The idea is to have a different UI for loading the first page and "next" pages. 
For example the UI for loading the first page shows a big circular progress spinner  whereas when loading 2nd page a loading spinner should be shown at the bottom of the list of articles. 
Similarly, handling errors (while loading articles) should result in different UI: 
error while loading the first page ahold show a big error icon in the middle of the screen as well as a retry button. An error on loading 2nd page should just show a temporarily toast measage while still displaying the list of previously loaded articles.

This is why we have `FirstPageLoadingState` and `FirstPageErrorState` to cover the cases of loading the first page (different UI). 
In contrast we consider loading 2nd page as some sort of "sub-state" of `ShowArticlesState` and use `LoadNextPageState` zo model these sub-states

## Initial StateMachine

Ok, so what kind of Actions do we need?
We only need one Action to trigger retry loading the first:

```kotlin
sealed interface Action
object RetryLoadFirstPageAction : Action
```

Our StateMachine definition looks as following:

```
class ArticlesStateMachine( private val repo : ArticlesRepository ) : FlowReduxStateMachine( initialState = FirstPageLoadingState ) {

  init {
    spec {
       // We will fill that out together in the next sections
    }
  }
}
```
