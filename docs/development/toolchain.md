# Daymark development toolchain

Execution started 31 October 2026 in the isolated `feature/attendance-mvp` worktree. Task 1 local-development gate is verified: both frameworks compile and both native hosts launch. Physical iPhone/older-OS evidence and official matrix qualification remain release gates.

## Build inputs

Use JDK 21, Android SDK platform 37 and build tools 36.0.0. Set `sdk.dir` in ignored `local.properties` to your own SDK. The version catalog pins Kotlin 2.4.21, Compose Multiplatform 1.12.2, Material 3 1.9.0, AGP 9.3.1, and KSP 2.3.12. KSP applies successfully; no database processor has been exercised yet. Unused feature-library pins remain candidates until their tasks compile them.

Gradle 9.8.2 is pinned with official distribution SHA-256 `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`. Dependency checksums are in `gradle/verification-metadata.xml`; generated lockfiles and the catalog are source inputs. Investigate unexpected checksum changes instead of regenerating trust metadata to bypass them. No credentials belong in source or logs.

On macOS select Java for the command, leaving global settings intact:

```sh
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :shared:compileAndroidMain :shared:linkDebugFrameworkIosSimulatorArm64 :shared:linkDebugFrameworkIosArm64 :androidApp:assembleDebug
```

Actual shared test tasks are `:shared:testAndroidHostTest` and `:shared:iosSimulatorArm64Test`. No application behavior tests exist yet; the foundation uses the plan's compile/run gate.

## Observed results

- The full Gradle build passed in 9m 21s, producing both arm64 iOS frameworks and the Android debug APK.
- The final KSP-enabled build with checksums/lockfiles passed in 38s: 62 tasks, 10 executed and 52 up to date.
- Android CLI installed/launched the 11 MB APK on Huawei JKM-LX1, API 28. Its UI hierarchy contains `Daymark · Development build`. This verifies native hosting, not attendance functionality.
- Xcode built the Swift host using simulator SDK 27.1 and deployment target 16.0. Installed/launched on iPhone 18 Pro (iOS 27.0); its screenshot visibly renders the Compose development screen. Strict dependency verification remained enabled. The two missing Compose resource-archive checksums were verified against pinned Maven Central module metadata before being added.
- Explicit catalog coordinates replace deprecated Compose dependency helpers. Gradle reports plugin deprecations for Gradle 10; these are not suppressed.

## Machine and compatibility qualifications

JDK 21.0.11 is installed; the global default is 24.0.1 and remains unchanged. Xcode 27.1 (27A9275) is the only installed Xcode. Available simulators include iOS 27.0 iPhone 18 Pro (`779285A4-F1C5-4274-9899-B38357C3B824`) and iOS 27.1 iPhone Duo. Old 18.5/26.5 runtimes are unavailable. Android platform `android-37.0` reports API 37.0, revision 2.

Kotlin's official matrix still lists Xcode 26.4 and Gradle through 9.7.0 for Kotlin 2.4.21. Gradle 9.8.2 carries the required free security fixes. Successful local compilation is observed evidence, not a claim of official coverage for this newer matrix. The observed newer matrix is accepted for local development after both native hosts build/run. Official matrix qualification and older-OS/physical-device evidence remain release gates; no support floor was changed and no warnings were suppressed. The Xcode AppIntents metadata warning is expected for a bootstrap without AppIntents.

The simulator linker also reports the Kotlin/Native bundled ICU data object as built for iOS Simulator 18.5 while the app deployment target is 16.0. This warning was present in the initial host build as well as the Google bridge build. Simulator 27 execution does not qualify iOS 16 compatibility; inspect the native dependency/minimum-target combination and run older-OS acceptance before release. The installed Xcode's XCTest framework also warns about its newer minimum in the test bundle. Keep the product floor at 16 pending that acceptance gate; do not suppress these warnings or describe the floor as device-verified.

## Disk-space checkpoint

The initial LLVM extraction failed with `No space left on device`. This run's incomplete LLVM files and disposable duplicated caches were removed. The user then approved removal of exactly five shared Gradle caches: 8.14.3, 9.1.0, 9.5.1, 9.7.1, and jars-9. Cleanup completed, and the resumed build reused the preserved shared dependencies. LLVM 21 is now extracted successfully; existing SDKs, LLVM 19, and source files remain intact.

After native compilation, free space fell below 1 GiB. The user separately approved removing the older Kotlin/Native 2.2.0 and 2.2.21 toolchains; those exact folders were removed, restoring about 4.6 GiB before the iOS host build. The current 2.4.21 toolchain and installed Android/iOS SDKs were preserved. Build logs, Android UI dump, and the visually inspected iOS screenshot are in this plan's ignored `.superpowers/sdd/2026-10-27-attendance-mvp/` directory. At that Task 1 checkpoint only bootstrap hosting was implemented; subsequent native integration evidence is recorded in the feasibility and provisioning documents.

References checked 31 October 2026: [Kotlin compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html), [AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes), [Gradle 9.8.2 security fixes](https://docs.gradle.org/9.8.2/release-notes.html), [Android KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin).
