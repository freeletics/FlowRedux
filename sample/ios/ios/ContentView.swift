//
//  ContentView.swift
//  ios
//
//  Created by Hannes on 14.12.19.
//  Copyright © 2019 Hannes. All rights reserved.
//

import SwiftUI
import AFNetworking
import shared_code
import os.log

struct ContentView: View {
    @State private var state : PaginationState = LoadFirstPagePaginationState()
    
    func start(){
        let _ = PaginationStateMachine(
            logger: Logger(),
            githubApi: GithubApi_iOSKt.githubApi_iOS,
            scope: NsQueueCoroutineScope(),
            stateChangeListener: { (paginationState: PaginationState) -> Void in
                NSLog("Swift UI \(paginationState) to render")
                self.state = paginationState
        } )
    }
    
    
    var body: some View {
        VStack {
        if state is LoadFirstPagePaginationState {
            LoadingFirstPageView()
        } else if state is ShowContentPaginationState {
            Text("Default")
        }
    }
}
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
      ContentView()
    }
}

class Logger : LibraryFlowReduxLogger{
    
    func log(message: String) {
        // NSLog(message)
    }
}
