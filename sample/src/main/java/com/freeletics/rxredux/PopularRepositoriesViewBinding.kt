package com.freeletics.rxredux

import android.support.design.widget.Snackbar
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.freeletics.rxredux.businesslogic.github.GithubRepository
import com.freeletics.rxredux.businesslogic.pagination.PaginationStateMachine
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.Observable
import timber.log.Timber
import java.util.concurrent.TimeUnit


open class PopularRepositoriesViewBinding(protected val rootView: ViewGroup) {

    protected val recyclerView: RecyclerView = rootView.findViewById(R.id.recyclerView)
    protected val adapter: MainAdapter = MainAdapter(LayoutInflater.from(rootView.context))
    protected val loading: View = rootView.findViewById(R.id.loading)
    protected val error: View = rootView.findViewById(R.id.error)
    protected var snackBar: Snackbar? = null

    init {
        recyclerView.adapter = adapter
    }

    val retryLoadFirstPage = error.clicks()

    val endOfRecyclerViewReached = Observable.create<Unit> { emitter ->
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                val endReached = !recyclerView!!.canScrollVertically(1)
                Timber.d("Scroll changed: $endReached")
                if (endReached) {
                    emitter.onNext(Unit)
                }
            }
        }

        emitter.setCancellable { recyclerView.removeOnScrollListener(listener) }

        recyclerView.addOnScrollListener(listener)
    }.debounce(200, TimeUnit.MILLISECONDS)

    open fun render(state: PaginationStateMachine.State) =
        when (state) {
            PaginationStateMachine.State.LoadingFirstPageState -> {
                recyclerView.gone()
                loading.visible()
                error.gone()
            }
            is PaginationStateMachine.State.ShowContentState -> {
                showRecyclerView(items = state.items, showLoadingNext = false)
            }

            is PaginationStateMachine.State.ShowContentAndLoadNextPageState -> {
                showRecyclerView(items = state.items, showLoadingNext = true)
                recyclerView.scrollToPosition(adapter.items.count()) // Scroll to the last item
            }

            is PaginationStateMachine.State.ShowContentAndLoadNextPageErrorState -> {
                showRecyclerView(items = state.items, showLoadingNext = false)
                snackBar =
                        Snackbar.make(
                            rootView,
                            R.string.unexpected_error,
                            Snackbar.LENGTH_INDEFINITE
                        )
                snackBar!!.show()
            }

            is PaginationStateMachine.State.ErrorLoadingFirstPageState -> {
                recyclerView.gone()
                loading.gone()
                error.visible()
                snackBar?.dismiss()
            }
        }


    private fun showRecyclerView(items: List<GithubRepository>, showLoadingNext: Boolean) {
        recyclerView.visible()
        loading.gone()
        error.gone()

        adapter.items = items
        adapter.showLoading = showLoadingNext
        adapter.notifyDataSetChanged()

        snackBar?.dismiss()
    }

    private fun View.gone() {
        visibility = View.GONE
    }

    private fun View.visible() {
        visibility = View.VISIBLE
    }
}
