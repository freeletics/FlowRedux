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
    // TODO Not idiomatic Swift UI, maybe use @BindableObject instead or find a better way?
    @Binding var contentState : PaginationState
    var endOfListReached: () -> Void
    
    var body: some View {
        List {
            ForEach((contentState as! ContainsContentPaginationState).items) { repo in
                Text("\(repo.name)")
            }
            
            if (contentState is ShowContentAndLoadingNextPagePaginationState){
                LoadingIndicatorView(style: .small)
            } else if (contentState is ShowContentAndLoadingNextPageErrorPaginationState){
                Text("An error has occurred")
                    .background(Color.black)
                    .foregroundColor(Color.white)
                    .padding(10)
            } else {
            
            // Work around to get notified when we have reached the end of the list by showing an invisible rect
            Rectangle()
                .size(width: 0, height: 0)
                .onAppear(perform: endOfListReached)
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
extension GithubRepository : Identifiable {
}
