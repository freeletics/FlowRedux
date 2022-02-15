package com.freeletics.flowredux.traditional

import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import com.freeletics.flowredux.R
import com.freeletics.flowredux.sample.shared.GithubRepository
import com.hannesdorfmann.adapterdelegates4.AsyncListDifferDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegate

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
    adapterDelegate<GithubRepository, Any>(R.layout.item_repository) {
        val repoName = findViewById<TextView>(R.id.repoName)
        val starCount = findViewById<TextView>(R.id.starCount)
        bind {
            repoName.text = item.name
            starCount.text = item.stargazersCount.toString()
        }
    }

private fun loadingAdapterDelegate() = adapterDelegate<LoadingItem, Any>(R.layout.item_load_next) {
        // Nothing to bind
    }
