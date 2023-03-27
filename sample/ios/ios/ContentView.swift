//
//  ContentView.swift
//  ios
//
//  Created by Hannes on 14.12.19.
//  Copyright © 2019 Hannes. All rights reserved.
//

import SwiftUI
import shared
import os.log

struct ContentView: View {
    @State private var state: PaginationState = LoadFirstPagePaginationState()
    private let stateMachine: PaginationStateMachine = PaginationStateMachine(
        githubApi: GithubApi(),
        scope: NsQueueCoroutineScope()
    )

    var body: some View {
        contentView()
            .onAppear(perform: startStateMachine)

    }

    @ViewBuilder
    private func contentView() -> some View {
        switch state {
        case is LoadFirstPagePaginationState:
            LoadingIndicatorView()
        case let state as ShowContentPaginationState:
            GithubReposList(contentState: state, dispatchAction: dispatchAction)
        case is LoadingFirstPageError:
            Button(action: triggerReloadFirstPage) {
                Text("An error has occured.\nClick here to retry.")
            }
        default:
            EmptyView()
        }
    }

    private func triggerReloadFirstPage() {
        self.stateMachine.dispatch(action: RetryLoadingFirstPage())
    }
    
    
    private func dispatchAction(action : Action){
        self.stateMachine.dispatch(action: action)
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
