package com.freeletics.flowredux

import com.freeletics.flowredux.sample.shared.GithubRepository
import com.freeletics.flowredux.sample.shared.Item
import com.freeletics.flowredux.sample.shared.Loading
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateLayoutContainer
import kotlinx.android.synthetic.main.item_repository.*

class PopularRepositoriesAdapter : ListDelegationAdapter<List<Item>>(
    githubAdapterDelegate(),
    loadingAdapterDelegate()
)

private fun githubAdapterDelegate() =
    adapterDelegateLayoutContainer<GithubRepository, Item>(R.layout.item_repository) {
        bind {
            repoName.text = item.name
            starCount.text = item.stargazersCount.toString()
        }
    }

private fun loadingAdapterDelegate() =
    adapterDelegateLayoutContainer<Loading, Item>(R.layout.item_load_next) {
        // Nothing to bind
    }