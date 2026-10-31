import SwiftUI

@main
@MainActor
struct DaymarkApp: App {
    private let googleDriver: AppAuthDriver
    private let googleAuth: IosGoogleAuthAdapter

    init() {
        let driver = AppAuthDriver()
        googleDriver = driver
        googleAuth = IosGoogleAuthAdapter(driver: driver)
    }
    var body: some Scene {
        WindowGroup {
            Group {
#if DEBUG
            NativeProbeView(googleAuth: googleAuth)
#else
            ContentView()
#endif
            }
            .onOpenURL { url in _ = googleDriver.resume(url) }
        }
    }
}
