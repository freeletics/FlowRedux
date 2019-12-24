//
//  LoadingFirstPageView.swift
//  ios
//
//  Created by Hannes on 24.12.19.
//  Copyright © 2019 Hannes. All rights reserved.
//

import SwiftUI

struct LoadingFirstPageView: View {
   @State private var spinCircle = false

   var body: some View {
       ZStack {
           Circle()
               .trim(from: 0.5, to: 1)
               .stroke(Color.blue, lineWidth:4)
               .frame(width:100)
               .rotationEffect(.degrees(spinCircle ? 0 : -360), anchor: .center)
               .animation(Animation.linear(duration: 1).repeatForever(autoreverses: false))
       }
       .onAppear {
           self.spinCircle = true
       }
   }
}

struct LoadingFirstPageView_Previews: PreviewProvider {
    static var previews: some View {
        LoadingFirstPageView()
    }
}
