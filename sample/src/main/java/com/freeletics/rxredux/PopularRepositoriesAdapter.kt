package com.freeletics.rxredux

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.freeletics.rxredux.businesslogic.github.GithubRepository

const val VIEW_TYPE_REPO = 1
const val VIEW_TYPE_LOADING_NEXT = 2


class MainAdapter(val layoutInflater: LayoutInflater) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var items = emptyList<GithubRepository>()
    var showLoading = false

    private fun realSize() = items.size + (if (showLoading) 1 else 0)

    override fun getItemViewType(position: Int): Int =
        if (showLoading && position == items.size) // Its the last item and show loading
            VIEW_TYPE_LOADING_NEXT
        else
            VIEW_TYPE_REPO


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_TYPE_REPO -> GitRepositoryViewHolder(
                layoutInflater.inflate(
                    R.layout.item_repository,
                    parent,
                    false
                )
            )
            VIEW_TYPE_LOADING_NEXT -> LoadingNextPageViewHolder(
                layoutInflater.inflate(
                    R.layout.item_load_next,
                    parent,
                    false
                )
            )
            else -> throw IllegalArgumentException("ViewType $viewType is unexpected")
        }

    override fun getItemCount(): Int = realSize()

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is GitRepositoryViewHolder) {
            holder.bind(items[position])
        }
    }

    inner class GitRepositoryViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val repoName = v.findViewById<TextView>(R.id.repoName)
        private val starCount = v.findViewById<TextView>(R.id.starCount)

        fun bind(repo: GithubRepository) {
            repo.apply {
                repoName.text = name
                starCount.text = stars.toString()
            }
        }
    }

    inner class LoadingNextPageViewHolder(v: View) : RecyclerView.ViewHolder(v)
}
