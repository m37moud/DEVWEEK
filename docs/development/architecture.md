# Daymark architecture

The application uses feature-oriented MVVM with one isolated Koin container per `AppGraph`.

```text
Screen → ViewModel → Repository → focused data-source interface → implementation
                                                               ├─ Room/local services
                                                               └─ Ktor/native adapters
```

`repository/Repository` exposes focused attendance, checks, planning, time off, schedule reads, setup, settings, dashboard, reporting, application, and data-lifecycle contracts. `AppRepository` only holds constructor-injected interfaces. Existing services implement those contracts; their attendance rules, transaction boundaries, and resource ownership remain intact.

## Presentation

```text
presentation/features/<feature>/
├── FeatureScreen.kt
├── FeatureViewModel.kt
├── FeatureUiState.kt
├── FeatureUiEffect.kt  (when one-time UI actions exist)
├── FeatureMapper.kt    (when model conversion exists)
└── component/
```

Today, History, Plan, Settings, and Setup have separate state files and state-driven previews. Feature screens observe immutable state and wire callbacks; content components receive state and callbacks. Shared components live under `presentation/component`; routes live under `navigation`, and theme code lives under `ui/theme`.

The app ViewModel observes bootstrap settings and a portable tracker summary, retaining tracker ID and revision for derived configuration refresh. It coordinates activation, deletion suspension, and history navigation context through repository interfaces. Today owns dashboard/report observation. Settings owns automation changes, reminder permission coordination, report connection/retry operations, and confirmed local deletion. Native permission dialogs and URI opening retain their platform ports/UI lifetimes.

## Data and dependency ownership

Room implementations, database factories, DAOs, and entities live under `data/local`. Remote contracts live under `data/remote/data_source/domain`, with Ktor implementations under `data/remote/data_source/data/ktor`. Portable attendance, setup, settings, reporting, and dashboard models live under `models`. Pure rules retain focused attendance/calendar packages. Preferences remain in Room; token access retains the existing serialized, generation-fenced implementation.

Koin bindings under `di/ktor` connect interfaces to existing implementations. Native modules load last. ViewModels remain lifecycle scoped, Android workers share the application container, and `ReportingRuntime` still owns connection/sync/lifecycle coordinators and HTTP client teardown. The repository itself does not resolve dependencies.

## Compatibility

- The saved database filename, SQL schema version 2, identity hashes, and 1→2 migration are unchanged. The moved database type exports schemas under `dev.daymark.data.local.DaymarkDatabase`; historical exports remain preserved.
- Moved navigation types explicitly retain their former `dev.daymark.ui.*Route` serial names and existing arguments.
- Swift entry points and native bridge contracts remain stable.
- The architecture regression check rejects storage/composition dependencies in presentation; existing migration, attendance, report, and host tests remain authoritative.

This architecture amendment changes implementation organization, not attendance semantics, permissions, support floors, report ownership, or release acceptance requirements. Frozen design documents are preserved.

## Refactor verification

The shared JVM, Android host, Android app, and desktop suites passed 307 tests. Android debug and release assembly and lint passed, and Android device-test sources compiled. Both Kotlin iOS targets compiled, and Xcode built the Daymark simulator host with signing disabled. Room v1 and v2 schema exports matched their historical copies, including the identity hashes.

Regression tests exercise bootstrap recovery on foreground return, reminder permission state, dashboard observation across activation, and legacy Navigation 3 back-stack restoration.

Device journeys and live Google authorization remain release checks.
