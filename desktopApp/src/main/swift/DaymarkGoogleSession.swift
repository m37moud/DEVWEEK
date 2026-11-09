import Foundation
import Security

let query: [String: Any] = [
    kSecClass as String: kSecClassGenericPassword,
    kSecAttrService as String: "dev.daymark.desktop.oauth",
    kSecAttrAccount as String: "google-session"
]

guard CommandLine.arguments.count == 2 else { exit(1) }
switch CommandLine.arguments[1] {
case "read":
    var request = query
    request[kSecReturnData as String] = true
    request[kSecMatchLimit as String] = kSecMatchLimitOne
    var item: CFTypeRef?
    let status = SecItemCopyMatching(request as CFDictionary, &item)
    if status == errSecItemNotFound { exit(0) }
    guard status == errSecSuccess, let data = item as? Data else { exit(1) }
    FileHandle.standardOutput.write(data)
case "write":
    let data = FileHandle.standardInput.readDataToEndOfFile()
    guard !data.isEmpty, data.count <= 65536 else { exit(1) }
    let attributes: [String: Any] = [
        kSecValueData as String: data,
        kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
    ]
    let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
    if status == errSecSuccess { exit(0) }
    guard status == errSecItemNotFound else { exit(1) }
    exit(SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil) == errSecSuccess ? 0 : 1)
case "clear":
    let status = SecItemDelete(query as CFDictionary)
    exit(status == errSecSuccess || status == errSecItemNotFound ? 0 : 1)
default:
    exit(1)
}
