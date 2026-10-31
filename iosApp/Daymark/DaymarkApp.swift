import SwiftUI

@main
struct DaymarkApp: App {
    var body: some Scene {
        WindowGroup {
#if DEBUG
            NativeProbeView()
#else
            ContentView()
#endif
        }
    }
}
