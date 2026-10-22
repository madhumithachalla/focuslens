import SwiftUI

@main
struct FocusLensApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.container, edges: .bottom)
        }
    }
}
