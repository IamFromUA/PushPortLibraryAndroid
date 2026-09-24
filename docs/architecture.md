# Architecture

The SDK is one distributable Android library. It uses package boundaries and constructor injection; there is no runtime DI framework and no requirement for a host application's DI setup.

## Dependency direction

| Layer | Responsibility | Permitted SDK dependencies |
| --- | --- | --- |
| Public API | Stable integration methods, configuration, status | Runtime composition root and permission adapter |
| `internal.core` | Installation lifecycle, snapshots, synchronization, token recovery, message deduplication | Models, ports, platform-free public value types |
| `internal.model` | Immutable installation and protocol data | Platform-free public configuration |
| `internal.ports` | Narrow interfaces for external capabilities | Models and platform-free configuration |
| Adapters | Android, Firebase, HTTP, JSON, atomic persistence | Ports and models; serialization where needed |
| `internal.runtime` | Construct services and connect callbacks | All implementation layers |
| Android entry points | Receive broadcasts, open notifications, run background work | Runtime and public facade |

Architecture tests inspect compiled bytecode to prevent Android/Firebase/JSON/adapter dependencies in the core, reverse dependencies from models/ports into orchestration, and cycles between internal packages.

## Responsibilities

- `InstallationController` validates initialization and changes user preferences.
- `SessionTracker` measures foreground visits and duration with an injectable monotonic clock.
- `UserController` validates optional data and maintains the bounded account-aware operation queue.
- `SnapshotCollector` gathers metadata through a port and increments the revision only when values change.
- `SyncCoordinator` serializes network synchronization, acknowledges only uploaded revisions, drains opened events, and decides whether work should retry.
- `TokenRefresher` distinguishes ready, unconfigured, invalid, and temporarily unavailable FCM state. Metadata can still register when FCM is unavailable.
- `PushHandler` filters owned messages and suppresses duplicates; presentation is delegated.
- `NotificationOpenTracker` maintains the bounded opened-event queue independently of presentation.
- `SdkRuntime` owns dependency wiring and process-local lifecycle registration. Core services never retrieve it themselves.

## SOLID in this codebase

Single responsibilities follow observable behavior, not one interface for every class. External capabilities have small ports: `InstallationRepository`, `InstallationApi`, `PushTokenProvider`, `DeviceInfoProvider`, `SyncScheduler`, `NotificationPresenter`, `NotificationStateProvider`, and `Clock`.

New transport or storage implementations can satisfy the existing ports without changing synchronization rules. Implementations must preserve port contracts: repository updates are atomic, API calls fail before acknowledgement, and token retrieval propagates coroutine cancellation. Tests substitute these ports with deterministic in-memory implementations.

Use concrete internal collaborators when there is no replaceable boundary. Do not add generic repositories, service locators, inheritance hierarchies, or modules solely to satisfy a pattern.

## Extending the SDK

For an additional feature such as notification customization:

1. Specify the public contract and server protocol before adding API surface.
2. Put state and invariants in typed models/core services.
3. Add a port only when a new external capability needs substitution.
4. Implement Android/network/persistence details in adapters.
5. Test failure, cancellation, state migration, and consumer compatibility.
6. Review the API baseline and update KDoc, migration notes and changelog.

Split a package into a Gradle module when an independently testable or distributable feature justifies it. Push-only SDKs do not yet need the feature-module structure of a much larger engagement platform.

## References and decisions

- [Kotlin API simplicity](https://kotlinlang.org/docs/api-guidelines-simplicity.html): explicit public contracts and a small integration surface.
- [Kotlin compatibility guidelines](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html): API dumps, explicit types, and care with data classes/default arguments.
- [Android library release guidance](https://developer.android.com/build/publish-library/prep-lib-release): namespaces, resources and consumer configuration.
- [OneSignal Android SDK](https://github.com/OneSignal/OneSignal-Android-SDK): separate integration, contribution, migration and example documentation. Its size does not determine this SDK's module count.

The implementation and documentation are original; reference projects inform conventions rather than provide copied SDK code.

See [partner tracking links](partner-integration.md) for application-side URL construction using the confirmed user ID.
