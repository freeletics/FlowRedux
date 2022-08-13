//
//  GithubRepositoryRow.swift
//  ios
//
//  Created by Hannes on 13.01.20.
//  Copyright © 2020 Hannes. All rights reserved.
//

import SwiftUI
import shared_code

struct GithubRepositoryRow: View {
    let repo: GithubRepository

    var body: some View {
        Text(repo.name)
        // TODO add star count
    }
}

struct GithubRepositoryRow_Previews: PreviewProvider {
    static var previews: some View {
        GithubRepositoryRow(
            repo: GithubRepository(id: "some_id", name: "Github Repo name", stargazersCount: 23, favoriteStatus: FavoriteStatus.notFavorite)
        )
    }
}
