//
//  GithubReposList.swift
//  ios
//
//  Created by Hannes on 13.01.20.
//  Copyright © 2020 Hannes. All rights reserved.
//

import SwiftUI
import shared_code

struct GithubReposList: View {
    var repositories : [GithubRepository]
    var body: some View {
        List(repositories) { repo in
            Text("\(repo.name)")
        }
    }
}

struct GithubReposList_Previews: PreviewProvider {
    static var previews: some View {
        GithubReposList(repositories: [GithubRepository(id: "1", name: "Repop name", stargazersCount: 123)])
        .previewLayout(.fixed(width: 300, height: 70))
    }
}

extension GithubRepository : Identifiable {
}
