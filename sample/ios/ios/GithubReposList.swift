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
    var endOfListReached: () -> Void
    var showLoadMoreIndicator : Bool
    
    var body: some View {
        List {
            ForEach(repositories) { repo in
                Text("\(repo.name)")
            }
            
            if (showLoadMoreIndicator){
                LoadingIndicatorView(style: .small)
            }
            
            // Work around to get notified when we have reached the end of the list by showing an invisible rect
            Rectangle()
                .size(width: 0, height: 0)
                .onAppear(perform: endOfListReached)
        }
    }
}

struct GithubReposList_Previews: PreviewProvider {
    static var previews: some View {
        GithubReposList(repositories: [GithubRepository(id: "1", name: "Repop name", stargazersCount: 123)], endOfListReached: { }, showLoadMoreIndicator: true)
        .previewLayout(.fixed(width: 300, height: 70))
    }
}

extension GithubRepository : Identifiable {
}
