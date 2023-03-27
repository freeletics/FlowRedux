import SwiftUI

struct ErrorView: View {
    let action: () -> Void

    var body: some View {
        HStack {
            Text("❌ An error has occurred.")
            Button("Retry!", action: action)
        }
    }
}
