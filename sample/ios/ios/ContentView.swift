//
//  ContentView.swift
//  ios
//
//  Created by Hannes on 14.12.19.
//  Copyright © 2019 Hannes. All rights reserved.
//

import SwiftUI
import shared_code
import os.log

struct ContentView: View {
    @State private var state: PaginationState = LoadFirstPagePaginationState()
    private let stateMachine: PaginationStateMachine = PaginationStateMachine(
        githubApi: GithubApi(),
        scope: NsQueueCoroutineScope()
    )

    var body: some View {

        NSLog("rendering \(state)")

        return ZStack {
            if state is LoadFirstPagePaginationState {
                LoadingIndicatorView()
            } else if let state = state as? ShowContentPaginationState {
                GithubReposList(
                    contentState: state,
                    endOfListReached: triggerLoadNextPage
                )
            } else if state is LoadingFirstPageError {
                // TODO extract standalone widget?
                Button(action: triggerReloadFirstPage) {
                    Text("An error has occured.\nClick here to retry.")
                }
            }
        }.onAppear(perform: startStateMachine)

    }

    private func triggerLoadNextPage() {
        self.stateMachine.dispatch(action: LoadNextPage())
    }

    private func triggerReloadFirstPage() {
        self.stateMachine.dispatch(action: RetryLoadingFirstPage())
    }

    private func startStateMachine() {
        self.stateMachine.start(stateChangeListener: { (paginationState: PaginationState) -> Void in
            NSLog("Swift UI \(paginationState) to render")
            self.state = paginationState
        })
    }

}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}

extension String: Error { }
