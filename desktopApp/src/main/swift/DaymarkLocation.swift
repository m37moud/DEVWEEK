import AppKit
import CoreLocation
import Foundation

final class LocationCommand: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private let command: String
    private var finished = false
    private var requested = false
    private var authorizationRequested = false
    private var candidate: CLLocation?

    init(command: String) {
        self.command = command
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func run() {
        if command == "settings" {
            let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_LocationServices")!
            finish(NSWorkspace.shared.open(url) ? "OK" : "ERROR\tUNAVAILABLE")
            return
        }
        if command == "status" {
            finish(capabilities())
            return
        }
        guard command == "authorize" || command == "locate" else {
            finish("ERROR\tUNAVAILABLE")
            return
        }
        Timer.scheduledTimer(withTimeInterval: 29, repeats: false) { [weak self] _ in
            if let location = self?.candidate {
                self?.finishLocation(location)
            } else {
                self?.finish("ERROR\tTIMEOUT")
            }
        }
        advance()
        while !finished {
            RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.05))
        }
    }

    private func capabilities() -> String {
        let authorized = manager.authorizationStatus == .authorizedAlways
        let precise = manager.accuracyAuthorization == .fullAccuracy
        return "CAP\t\(authorized ? 1 : 0)\t\(precise ? 1 : 0)\t\(CLLocationManager.locationServicesEnabled() ? 1 : 0)"
    }

    private func advance() {
        guard !finished else { return }
        guard CLLocationManager.locationServicesEnabled() else {
            finish(command == "authorize" ? capabilities() : "ERROR\tSERVICES_DISABLED")
            return
        }
        switch manager.authorizationStatus {
        case .notDetermined:
            if !authorizationRequested {
                authorizationRequested = true
                manager.requestWhenInUseAuthorization()
            }
        case .denied, .restricted:
            finish(command == "authorize" ? capabilities() : "ERROR\tPERMISSION_DENIED")
        case .authorizedAlways:
            if command == "authorize" {
                finish(capabilities())
            } else if !requested {
                requested = true
                manager.startUpdatingLocation()
            }
        @unknown default:
            finish("ERROR\tUNAVAILABLE")
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        advance()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        for location in locations.sorted(by: { $0.timestamp > $1.timestamp }) {
            let age = Date().timeIntervalSince(location.timestamp)
            guard location.horizontalAccuracy >= 0, age >= 0 else { continue }
            if candidate == nil || location.timestamp > candidate!.timestamp {
                candidate = location
            }
            if age <= 30 && location.horizontalAccuracy <= 50 {
                finishLocation(location)
                return
            }
        }
    }

    private func finishLocation(_ location: CLLocation) {
        let received = Date()
        let ageMillis = received.timeIntervalSince(location.timestamp) * 1000
        let capturedMillis = Int64((location.timestamp.timeIntervalSince1970 * 1000).rounded(.down))
        let receivedMillis = Int64((received.timeIntervalSince1970 * 1000).rounded(.down))
        finish("FIX\t\(location.coordinate.latitude)\t\(location.coordinate.longitude)\t\(location.horizontalAccuracy)\t\(capturedMillis)\t\(receivedMillis)\t\(ageMillis)")
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let reason = (error as? CLError)?.code == .denied ? "PERMISSION_DENIED" : "UNAVAILABLE"
        finish("ERROR\t\(reason)")
    }

    private func finish(_ reply: String) {
        guard !finished else { return }
        finished = true
        manager.stopUpdatingLocation()
        print(reply)
    }
}

let operation = CommandLine.arguments.dropFirst().first ?? "status"
if operation == "authorize" || operation == "locate" {
    let application = NSApplication.shared
    application.setActivationPolicy(.accessory)
    application.activate(ignoringOtherApps: true)
}
let command = LocationCommand(command: operation)
command.run()
