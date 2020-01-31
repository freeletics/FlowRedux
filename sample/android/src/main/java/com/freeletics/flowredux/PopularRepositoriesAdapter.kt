package com.freeletics.flowredux

import com.freeletics.flowredux.sample.shared.GithubRepository
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateLayoutContainer
import kotlinx.android.synthetic.main.item_repository.*

/**
 * Represents a loading object
 */
object LoadingItem

class PopularRepositoriesAdapter : ListDelegationAdapter<List<Any>>(
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
