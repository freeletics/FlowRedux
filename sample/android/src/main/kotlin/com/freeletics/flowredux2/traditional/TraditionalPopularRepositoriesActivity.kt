package com.freeletics.flowredux2.traditional

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.freeletics.flowredux2.sample.android.databinding.ActivityTraditionalPopularRepositoriesBinding
import com.freeletics.flowredux2.sample.shared.LoadFirstPagePaginationState
import com.freeletics.flowredux2.sample.shared.LoadNextPage
import com.freeletics.flowredux2.sample.shared.LoadingFirstPageError
import com.freeletics.flowredux2.sample.shared.NextPageLoadingState
import com.freeletics.flowredux2.sample.shared.PaginationState
import com.freeletics.flowredux2.sample.shared.RetryLoadingFirstPage
import com.freeletics.flowredux2.sample.shared.ShowContentPaginationState
import com.google.android.material.snackbar.Snackbar
import kotlin.LazyThreadSafetyMode.NONE
import timber.log.Timber

class TraditionalPopularRepositoriesActivity : ComponentActivity() {
    private val viewModel by viewModels<PopularRepositoriesViewModel>()

    private val binding by lazy(NONE) { ActivityTraditionalPopularRepositoriesBinding.inflate(layoutInflater) }
    private val adapter: PopularRepositoriesAdapter by lazy(NONE) {
        PopularRepositoriesAdapter(dispatch = viewModel::dispatch)
    }

    private var snackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.recyclerView.adapter = adapter

        viewModel.stateLiveData.observe(this) {
            Timber.d("render $it")
            render(it)
        }
        binding.error.setOnClickListener { viewModel.dispatch(RetryLoadingFirstPage) }

        val endOfListReached = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                val endReached = !recyclerView.canScrollVertically(1)
                Timber.d("Scroll changed: $endReached")
                if (endReached) {
                    viewModel.dispatch(LoadNextPage)
                }
            }
        }

        binding.recyclerView.addOnScrollListener(endOfListReached)
    }

    private fun render(state: PaginationState) = when (state) {
        LoadFirstPagePaginationState -> {
            binding.run {
                error.gone
                recyclerView.gone
                loading.visible
                snackbar?.dismiss()
            }
        }
        is ShowContentPaginationState -> {
            adapter.items = when (state.nextPageLoadingState) {
                NextPageLoadingState.LOADING -> state.items + LoadingItem
                else -> state.items
            }
            binding.run {
                error.gone
                recyclerView.visible

                if (state.nextPageLoadingState == NextPageLoadingState.ERROR) {
                    snackbar = Snackbar
                        .make(rootView, "An error occurred", Snackbar.LENGTH_LONG)
                        .apply { show() }
                } else {
                    snackbar?.dismiss()
                    snackbar = null
                }

                loading.gone
            }
        }
        is LoadingFirstPageError -> {
            binding.run {
                error.visible
                recyclerView.gone
                loading.gone
                snackbar?.dismiss()
            }
        }
    }
}

inline val View.gone: Unit
    get() {
        visibility = View.GONE
    }

inline val View.visible: Unit
    get() {
        visibility = View.VISIBLE
    }
