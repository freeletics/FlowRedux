package com.freeletics.flowredux.traditional

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import com.freeletics.flowredux.R
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.FavoriteStatus
import com.freeletics.flowredux.sample.shared.GithubRepository
import com.freeletics.flowredux.sample.shared.RetryToggleFavoriteAction
import com.freeletics.flowredux.sample.shared.ToggleFavoriteAction
import com.hannesdorfmann.adapterdelegates4.AsyncListDifferDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegate

/**
 * Represents a loading object
 */
object LoadingItem

class PopularRepositoriesAdapter(dispatch: (Action) -> Unit) : AsyncListDifferDelegationAdapter<Any>(
    object : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean =
            if (newItem is GithubRepository && oldItem is GithubRepository) {
                newItem.id == oldItem.id
            } else {
                false
            }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return if (oldItem is GithubRepository && newItem is GithubRepository) {
                newItem == oldItem
            } else {
                false
            }
        }
    },
    githubAdapterDelegate(dispatch),
    loadingAdapterDelegate()
)

private fun githubAdapterDelegate(dispatch: (Action) -> Unit) =
    adapterDelegate<GithubRepository, Any>(R.layout.item_repository) {
        val repoName = findViewById<TextView>(R.id.repoName)
        val starCount = findViewById<TextView>(R.id.starCount)
        val starIcon = findViewById<ImageView>(R.id.starIcon)
        val progressBar = findViewById<View>(R.id.loadingFavorite)

        starIcon.setOnClickListener {
            when (item.favoriteStatus) {
                FavoriteStatus.NOT_FAVORITE,
                FavoriteStatus.FAVORITE,
                -> dispatch(ToggleFavoriteAction(item.id))
                FavoriteStatus.FAILED_MARKING_AS_FAVORITE -> dispatch(RetryToggleFavoriteAction(item.id))
                FavoriteStatus.MARKING_IN_PROGRESS -> {}
            }
        }

        bind {
            repoName.text = item.name
            starCount.text = item.stargazersCount.toString()
            when (item.favoriteStatus) {
                FavoriteStatus.FAVORITE -> {
                    progressBar.gone
                    starIcon.visible
                    starIcon.setImageResource(R.drawable.ic_star_yellow_24dp)
                }
                FavoriteStatus.NOT_FAVORITE -> {
                    progressBar.gone
                    starIcon.visible
                    starIcon.setImageResource(R.drawable.ic_star_black_24dp)
                }
                FavoriteStatus.MARKING_IN_PROGRESS -> {
                    starIcon.gone
                    progressBar.visible
                }
                FavoriteStatus.FAILED_MARKING_AS_FAVORITE -> {
                    progressBar.gone
                    starIcon.visible
                    starIcon.setImageResource(R.drawable.ic_warning)
                }
            }
            starIcon
        }
    }

private fun loadingAdapterDelegate() = adapterDelegate<LoadingItem, Any>(R.layout.item_load_next) {
    // Nothing to bind
}
