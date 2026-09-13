# Compatibility policy

## Current baseline

| Area | Baseline |
| --- | --- |
| Android runtime | API 23 and above |
| Push transport | Firebase Cloud Messaging; Google Play services required |
| Library build | AGP 9.2.1, Gradle 9.4.1, compile SDK 36 |
| Consumer compile SDK | At least 36, as recorded in the AAR metadata |
| Bytecode | Java 17 |
| Public language API | Kotlin, with static Java entry points and constructor overloads |
| State | Schema 1, with a reader for unversioned schema 0 |
| Package / artifact | `dev.pushport.sdk` / `dev.pushport:android-sdk` |

This is the tested build configuration, not a claim that every older consumer toolchain is supported. Transitive Android/Firebase dependencies may impose their own compile SDK, AGP and Kotlin metadata requirements. The release AAR and POM must be tested from a consumer build.

## API review

`api/PushPortLibrary.api` is the reviewed Kotlin/JVM signature baseline. `apiCheck` builds the release AAR, extracts its actual `classes.jar`, and compares it with that file. The build fails on differences, including additions, so API changes receive an explicit review.

```shell
./gradlew apiCheck
# Only after reviewing the proposed API change:
./gradlew apiDump
```

Do not run `apiDump` automatically in CI. Updating the reference does not prove compatibility; inspect signatures and update the version/migration guide as appropriate.

The AGP 9 build uses built-in Kotlin. The current native Kotlin ABI DSL is unavailable on that Android extension, and BCV 0.18.1 does not automatically register its Android tasks for it. The build therefore uses JetBrains BCV task types with Android's public AAR artifact API. This also verifies the distributable classes rather than a guessed intermediate directory. Revisit this bridge when the upstream tools support the configuration directly.

## Evolving public types

Public return types and visibility are explicit. Keep implementation details internal and document every public entry point through KDoc.

`PushPortConfig` and `PushPortStatus` already expose data-class constructors, `copy`, and `componentN` methods. Their existing constructor shapes are compatibility commitments; adding a defaulted constructor field can still break compiled callers. Introduce new types or deliberate overloads instead. Prefer regular immutable classes with controlled constructors for future expandable public models.

Before 1.0, minor releases may contain documented breaking changes. Patch releases preserve API/storage contracts except explicitly documented corrective behavior. From 1.0, breaking public changes require a major release and a migration path. Do not remove an API without deprecation and migration notes.

## Android and state compatibility

Maintain the identities of `SyncWorker`, `NotificationOpenActivity`, `PushPortMessageReceiver`, and `EnvironmentReceiver`; WorkManager records and existing PendingIntents can outlive an app update. These restricted classes remain in the binary baseline even though they are not application-facing APIs.

The filename, schema, installation identity, channel ID, work names and message keys are separate compatibility contracts that an API dump cannot verify. Preserve them or implement an explicit migration. Keep tests for earlier stored formats and run an emulator update without clearing app data.

The previous EasyPush-to-PushPort rename changed packages, filenames, component names and Maven coordinates. It was a pre-release breaking change; the legacy JSON fixture verifies schema readability, not automatic discovery of the old filename. Version 0.2.0 starts the PushPort component-name baseline.

## Firebase coexistence

The SDK uses the named Firebase application `PUSHPORT_FCM`. It leaves the host's default Firebase application intact. Token registration retains the currently verified Firebase Messaging integration mode; changing that transport path requires its own compatibility test and release note.

SDK-only builds and R8 consumer builds are checked locally. A matrix of older AGP/Kotlin versions, multiple OEM devices, and host applications using custom WorkManager initialization remains future validation work, not a current guarantee.
