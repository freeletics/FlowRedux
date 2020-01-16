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
import com.freeletics.flowredux.sample.shared.ShowContentPaginationState
import kotlinx.android.synthetic.main.fragment_repositories.*
import timber.log.Timber

class PopularRepositoriesFragment : Fragment() {

    val viewModel by viewModels<PopularRepositoriesViewModel>()
    lateinit var adapter: PopularRepositoriesAdapter

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
            adapter.items = state.items
            error.gone
            recyclerView.visible
            loading.gone
        }
        is LoadingFirstPageError -> {
            error.visible
            recyclerView.gone
            loading.gone
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
