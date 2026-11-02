import SwiftUI

@main
struct VoCabApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .fontDesign(.rounded)
        }
    }
}
