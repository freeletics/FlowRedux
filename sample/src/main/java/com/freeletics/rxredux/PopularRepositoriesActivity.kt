package com.freeletics.rxredux

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.freeletics.rxredux.businesslogic.pagination.Action
import com.freeletics.rxredux.util.viewModel
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.*
import javax.inject.Inject
import javax.inject.Provider

class PopularRepositoriesActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelProvider: Provider<PopularRepositoriesViewModel>

    private val viewModel by lazy {
        viewModel<PopularRepositoriesViewModel>(SimpleViewModelProviderFactory(viewModelProvider))
    }

    @Inject
    lateinit var viewBindingFactory: ViewBindingFactory

    private val viewBinding by lazy {
        viewBindingFactory.create<PopularRepositoriesViewBinding>(
            PopularRepositoriesActivity::class.java,
            rootView
        )
    }

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applicationComponent.inject(this)

        viewModel.state.observe(this, Observer {
            viewBinding.render(it!!)
        })

        disposables.add(
            viewBinding.endOfRecyclerViewReached
                .map { Action.LoadNextPageAction }
                .subscribe(viewModel.input)
        )

        viewModel.input.accept(Action.LoadFirstPageAction)

        disposables.add(
            viewBinding.retryLoadFirstPage
                .map { Action.LoadFirstPageAction }
                .subscribe(viewModel.input)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    private val Activity.applicationComponent
        get() = (application as SampleApplication).applicationComponent
}

