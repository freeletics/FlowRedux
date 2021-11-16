package com.freeletics.flowredux.traditional

import androidx.recyclerview.widget.DiffUtil
import com.freeletics.flowredux.R
import com.freeletics.flowredux.sample.shared.GithubRepository
import com.hannesdorfmann.adapterdelegates4.AsyncListDifferDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateLayoutContainer
import kotlinx.android.synthetic.main.item_repository.*

/**
 * Represents a loading object
 */
object LoadingItem

class PopularRepositoriesAdapter : AsyncListDifferDelegationAdapter<Any>(
    object : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean =
            newItem == oldItem

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean = false
    },
    githubAdapterDelegate(),
    loadingAdapterDelegate()
)

private fun githubAdapterDelegate() =
    adapterDelegateLayoutContainer<GithubRepository, Any>(R.layout.item_repository) {
        bind {
            repoName.text = item.name
            starCount.text = item.stargazersCount.toString()
        }
    }

private fun loadingAdapterDelegate() =
    adapterDelegateLayoutContainer<LoadingItem, Any>(R.layout.item_load_next) {
        // Nothing to bind
    }
