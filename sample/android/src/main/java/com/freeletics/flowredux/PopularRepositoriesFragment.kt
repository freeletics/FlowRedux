package com.freeletics.flowredux

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.freeletics.flowredux.sample.shared.LoadingPaginationState
import com.freeletics.flowredux.sample.shared.PaginationState
import kotlinx.android.synthetic.main.fragment_repositories.*
import timber.log.Timber

class PopularRepositoriesFragment : Fragment() {

    val viewModel by viewModels<PopularRepositoriesViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_repositories, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.liveData.observe(viewLifecycleOwner, Observer {
            Timber.d("render $it")
            render(it)
        })
    }

    private fun render(state: PaginationState) = when (state) {
        LoadingPaginationState -> {
            error.gone
            recyclerView.gone
            loading.visible
            Timber.d("Here")
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
