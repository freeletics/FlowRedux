package com.freeletics.flowredux

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.freeletics.flowredux.sample.shared.LoadFirstPagePaginationState
import com.freeletics.flowredux.sample.shared.LoadingFirstPageError
import com.freeletics.flowredux.sample.shared.PaginationState
import com.freeletics.flowredux.sample.shared.ShowContentAndLoadingNextPageErrorPaginationState
import com.freeletics.flowredux.sample.shared.ShowContentAndLoadingNextPagePaginationState
import com.freeletics.flowredux.sample.shared.ShowContentPaginationState
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_repositories.*
import timber.log.Timber

class PopularRepositoriesFragment : Fragment() {

    private val viewModel by viewModels<PopularRepositoriesViewModel>()
    private var adapter: PopularRepositoriesAdapter? = null
    private var snackbar : Snackbar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_repositories, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PopularRepositoriesAdapter()
        recyclerView.adapter = adapter
        viewModel.liveData.observe(viewLifecycleOwner, Observer {
            Timber.d("render $it")
            render(it)
        })
    }

    private fun render(state: PaginationState) = when (state) {
        LoadFirstPagePaginationState -> {
            error.gone
            recyclerView.gone
            loading.visible
        }
        is ShowContentPaginationState -> {
            adapter!!.items = state.items
            error.gone
            recyclerView.visible
            loading.gone
        }
        is ShowContentAndLoadingNextPagePaginationState -> {
            adapter!!.items = listOf(state.items, LoadingItem)
            error.gone
            recyclerView.visible
            loading.gone
        }
        is ShowContentAndLoadingNextPageErrorPaginationState -> {
            adapter!!.items = state.items
            error.gone
            recyclerView.visible
            loading.gone
            snackbar = Snackbar.make(view!!, "An error occurred", Snackbar.LENGTH_LONG)
            snackbar!!.show()
        }
        is LoadingFirstPageError -> {
            error.visible
            recyclerView.gone
            loading.gone
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
        snackbar = null
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
