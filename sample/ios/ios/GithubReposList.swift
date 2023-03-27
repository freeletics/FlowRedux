//
//  GithubReposList.swift
//  ios
//
//  Created by Hannes on 13.01.20.
//  Copyright © 2020 Hannes. All rights reserved.
//

import SwiftUI
import shared

struct GithubReposList: View {
    let contentState: ShowContentPaginationState
    let dispatchAction: (Action) -> Void

    var body: some View {
        List {
            ForEach(contentState.items) { repo in
                GithubRepositoryRow(repo: repo, dispatchAction: dispatchAction)
            }

            nextLoadingPageStatusView()
                .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder
    private func nextLoadingPageStatusView() -> some View {
        switch contentState.nextPageLoadingState {
        case .loading:
            HStack(alignment: .center) {
                LoadingIndicatorView(style: .small)
            }
        case .error:
            HStack(alignment: .center) {
                Text("An error has occurred")
                    .background(Color.black)
                    .foregroundColor(Color.white)
                    .padding(10)
            }
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

struct GithubReposList_Previews: PreviewProvider {
    static var previews: some View {
        VStack {
            view(forLoadingState: .loading)
            view(forLoadingState: .error)
            view(forLoadingState: .idle)
        }
    }

    private static func view(forLoadingState loadingState: NextPageLoadingState) -> GithubReposList {
        GithubReposList(contentState: .init(items: [GithubRepository(id: "1",
                                                                     name: "repo name",
                                                                     stargazersCount: 123,
                                                                     favoriteStatus: .notFavorite)],
                                            nextPageLoadingState: loadingState,
                                            currentPage: 1,
                                            canLoadNextPage: true),
                        dispatchAction: { _ in })
    }
}

extension GithubRepository: Identifiable {
}
