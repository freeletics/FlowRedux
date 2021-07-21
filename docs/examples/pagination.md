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
That means if a user scrolls to the last `Article` in the list, then the next "page" of Articles are loaded an displayed.
That means we have som sort of loading states, error states along with showing a list of articles on the screen. 
This translates to the following state classes:

```kotlin
sealed interface State

object FirstPageLoadingState : State
object  FirstPageErrorState : State

enum class LoadNextPageState {
  NOT_LOADING,
  LOADING,
  ERROR
}

data class ShowArticles(
  val articles : List<Article>,
  val loadingNextPageState : LoadNextPageState,
  internal val currentPage : Int
)
```

The idea is to have a different UI for loading the first page and "next" pages. 
For example the UI for loading the first page shows a big circular progress spinner  whereas when loading 2nd page a loading spinner should be shown at the bottom of the list of articles. 
Similarly, handling errors (while loading articles) should result in different UI: 
error while loading the first page ahold show a big error icon in the middle of the screen as well as a retry button. An error on loading 2nd page should just show a temporarily toast measage while still displaying the list of previously loaded articles.

This is why we have `FirstPageLoadingState` and `FirstPageErrorState`.
