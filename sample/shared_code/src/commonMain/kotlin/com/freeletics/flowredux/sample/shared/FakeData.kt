package com.freeletics.flowredux.sample.shared

internal val githubData = (0..120).map {
    GithubRepository(
            id = "$it",
            name = "Repository $it",
            stargazersCount = it * 10
    )
}