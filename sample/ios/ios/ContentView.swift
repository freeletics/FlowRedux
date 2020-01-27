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
    
    var body: some View {

        NSLog("rendering \(state)")
        
        return VStack {
        if state is LoadFirstPagePaginationState {
            LoadingFirstPageView()
        } else if state is ShowContentPaginationState {
            Text("Default")
        }
        }.onAppear(perform: start)
        
    }
    
    private func start(){
           // This instantiates and actually starts the async jobs inside the statemachine.
           // updates follow via stateChangeListener
           PaginationStateMachine(
               logger: Logger(),
               githubApi: GithubApi_iOSKt.githubApi_iOS,
               scope: NsQueueCoroutineScope(),
               stateChangeListener: { (paginationState: PaginationState) -> Void in
                   NSLog("Swift UI \(paginationState) to render")
                   self.state = paginationState
           } )
       }
       
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
      ContentView()
    }
}

class Logger : FlowreduxFlowReduxLogger{
    
    func log(message: String) {
        // NSLog(message)
    }
}
