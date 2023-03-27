//
//  GithubRepositoryRow.swift
//  ios
//
//  Created by Hannes on 13.01.20.
//  Copyright © 2020 Hannes. All rights reserved.
//

import SwiftUI
import shared

struct GithubRepositoryRow: View {
    let repo: GithubRepository
    let dispatchAction: (Action) -> Void


    var body: some View {
        HStack {
            Text(repo.name)
            
            Spacer()

            Text("\(repo.stargazersCount)")
            
            switch repo.favoriteStatus {
            case .notFavorite:
                Image(systemName: "star")
                    .onTapGesture {
                        dispatchAction(ToggleFavoriteAction(id: repo.id))
                    }
        
            case .favorite:
                Image(systemName: "star.fill")
                    .onTapGesture {
                        dispatchAction(ToggleFavoriteAction(id: repo.id))
                    }
                
            case .operationFailed:
                Image(systemName: "exclamationmark.circle")
                    .onTapGesture {
                        dispatchAction(RetryToggleFavoriteAction(id: repo.id))
                    }
                
                
            case .operationInProgress:
                LoadingIndicatorView(style: .verySmall)

            default:
                fatalError("Unhandled case: \(repo.favoriteStatus)")
            }
        }
    }
}

struct GithubRepositoryRow_Previews: PreviewProvider {
    static var previews: some View {
        GithubRepositoryRow(
            repo: GithubRepository(id: "some_id", name: "Github Repo name", stargazersCount: 23, favoriteStatus: FavoriteStatus.notFavorite),
            dispatchAction: { action in
                
            }
        )
    }
}
