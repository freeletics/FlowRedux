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
    let contentState: ShowContentPaginationState
    let dispatchAction: (Action) -> Void

    var body: some View {
        List {
            ForEach(contentState.items) { repo in
                GithubRepositoryRow(repo: repo, dispatchAction: dispatchAction)
            }

            switch contentState.nextPageLoadingState {
            case .loading:
                HStack(alignment: .center) {
                    LoadingIndicatorView(style: .small)
                }.frame(maxWidth: .infinity)
            case .error:
                HStack(alignment: .center) {
                    Text("An error has occurred")
                        .background(Color.black)
                        .foregroundColor(Color.white)
                        .padding(10)
                }.frame(maxWidth: .infinity)
            case .idle:
                // Work around to get notified when we have reached the end of the list by showing an invisible rect
                Rectangle()
                    .size(width: 0, height: 0)
                    .onAppear(perform: { dispatchAction( LoadNextPage() ) })
            default:
                fatalError("Unhandled case: \(contentState.nextPageLoadingState)")

            }
        }
    }
}

/*
struct GithubReposList_Previews: PreviewProvider {
    static var previews: some View {
        GithubReposList(contentState: [GithubRepository(id: "1", name: "Repop name", stargazersCount: 123)], endOfListReached: { }, showLoadMoreIndicator: true)
        .previewLayout(.fixed(width: 300, height: 70))
    }
}
*/

extension GithubRepository: Identifiable {
}
