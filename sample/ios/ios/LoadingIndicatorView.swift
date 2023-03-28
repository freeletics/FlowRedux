//
//  LoadingFirstPageView.swift
//  ios
//
//  Created by Hannes on 24.12.19.
//  Copyright © 2019 Hannes. All rights reserved.
//

import SwiftUI

enum IndicatorStyle: CGFloat{
    case big = 100
    case small = 25
    case verySmall = 10
}

struct LoadingIndicatorView: View {
    @State private var spinCircle = false
    private let style: IndicatorStyle

    init(style: IndicatorStyle = .big) {
        self.style = style
    }

   var body: some View {
       ZStack {
           Circle()
               .trim(from: 0.5, to: 1)
               .stroke(Color.blue, lineWidth: 4)
            .frame(width: style.rawValue)
               .rotationEffect(.degrees(spinCircle ? 0 : -360), anchor: .center)
               .animation(.linear(duration: 1).repeatForever(autoreverses: false))
       }
       .onAppear {
           spinCircle = true
       }
   }
}

struct LoadingIndicatorView_Previews: PreviewProvider {
    static var previews: some View {
        LoadingIndicatorView()
    }
}
