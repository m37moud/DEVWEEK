#if DEBUG
import SwiftUI

struct NativeProbeView: View {
    @State private var message = "No reminder scheduled"
    @State private var scheduling = false

    var body: some View {
        VStack {
            ContentView()
            Button("Schedule one reminder in 60 seconds") {
                scheduling = true
                Task { @MainActor in
                    message = await ReminderProbe.schedule()
                    scheduling = false
                }
            }
            .disabled(scheduling)
            Text(message).font(.caption)
        }
        .onOpenURL { url in
            guard url.scheme == "daymark", url.host == "native-probe" else { return }
            message = "Widget opened the foreground probe. Choose Check once to request location."
        }
    }
}
#endif
