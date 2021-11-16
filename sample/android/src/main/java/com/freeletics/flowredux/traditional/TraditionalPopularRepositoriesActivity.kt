package com.freeletics.flowredux.traditional

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.freeletics.flowredux.R
import com.freeletics.flowredux.sample.shared.LoadFirstPagePaginationState
import com.freeletics.flowredux.sample.shared.LoadNextPage
import com.freeletics.flowredux.sample.shared.LoadingFirstPageError
import com.freeletics.flowredux.sample.shared.PaginationState
import com.freeletics.flowredux.sample.shared.RetryLoadingFirstPage
import com.freeletics.flowredux.sample.shared.ShowContentAndLoadingNextPageErrorPaginationState
import com.freeletics.flowredux.sample.shared.ShowContentAndLoadingNextPagePaginationState
import com.freeletics.flowredux.sample.shared.ShowContentPaginationState
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber

class TraditionalPopularRepositoriesActivity : ComponentActivity() {

    private val viewModel by viewModels<PopularRepositoriesViewModel>()
    private lateinit var adapter: PopularRepositoriesAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var loading: View
    private lateinit var error: View
    private lateinit var rootView : View
    private var snackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_traditional_popular_repositories)

        rootView = findViewById(R.id.rootView)
        recyclerView = findViewById(R.id.recyclerView)
        loading = findViewById(R.id.loading)
        error = findViewById(R.id.error)

        adapter = PopularRepositoriesAdapter()
        recyclerView.adapter = adapter
        viewModel.liveData.observe(this) {
            Timber.d("render $it")
            render(it)
        }
        error.setOnClickListener { viewModel.dispatch(RetryLoadingFirstPage) }

        val endOfListReached = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                val endReached = !recyclerView!!.canScrollVertically(1)
                Timber.d("Scroll changed: $endReached")
                if (endReached) {
                    viewModel.dispatch(LoadNextPage)
                }
            }
        }

        recyclerView.addOnScrollListener(endOfListReached)

    }


    private fun render(state: PaginationState) = when (state) {
        LoadFirstPagePaginationState -> {
            error.gone
            recyclerView.gone
            loading.visible
            snackbar?.dismiss()
        }
        is ShowContentPaginationState -> {
            adapter.items = state.items
            error.gone
            recyclerView.visible
            snackbar?.dismiss()
            loading.gone
        }
        is ShowContentAndLoadingNextPagePaginationState -> {
            adapter.items = state.items + LoadingItem
            recyclerView.smoothScrollToPosition(adapter.itemCount)
            error.gone
            recyclerView.visible
            loading.gone
            snackbar?.dismiss()
        }
        is ShowContentAndLoadingNextPageErrorPaginationState -> {
            adapter.items = state.items
            error.gone
            recyclerView.visible
            loading.gone
            snackbar = Snackbar.make(rootView, "An error occurred", Snackbar.LENGTH_LONG)
            snackbar!!.show()
        }
        is LoadingFirstPageError -> {
            error.visible
            recyclerView.gone
            loading.gone
            snackbar?.dismiss()
        }
    }

}

private val View.gone: Unit
    get() {
        visibility = View.GONE
    }

private val View.visible: Unit
    get() {
        visibility = View.VISIBLE
    }


