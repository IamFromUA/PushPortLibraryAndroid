# PushPort Android SDK

Android push notifications with a small Kotlin API, automatic installation registration, and application locale synchronization.

```kotlin
PushPort.initWithContext(this, "PUSHPORT_APP_ID")
```

**Version 0.0.1 is published on Maven Central** as `dev.pushport:android-sdk:0.0.1`. The SDK is licensed under [Apache License 2.0](LICENSE).

## Integration

1. Create an application in the PushPort dashboard and configure its Firebase credentials there.
2. Add the SDK dependency to your Android application:

   ```kotlin
   dependencies {
       implementation("dev.pushport:android-sdk:0.0.1")
   }
   ```

3. Initialize PushPort from your `Application.onCreate` and request notification permission from an activity when appropriate.

The dependency resolves through `mavenCentral()`. Add `google()` for Android and Firebase dependencies. No private repository, credentials or manual AAR download is needed.

Firebase Messaging, WorkManager, manifest components, and consumer R8 rules are included through the dependency. The host application does not need a PushPort service-account key, `google-services.json`, or Google Services Gradle plugin for this integration. Its own Firebase integration can remain independent.

**Requirements:** Android 6.0 / API 23+, a device with Google Play services for FCM delivery, and a reachable PushPort backend. This project builds with compile SDK 36, AGP 9.2.1 and Java 17 bytecode. See the [compatibility policy](docs/compatibility.md) before integrating into an older toolchain.

## Documentation

| Guide | Covers |
| --- | --- |
| [Quick start](docs/quick-start.md) | Kotlin and Java integration, permissions, local testing |
| [API reference](docs/api.md) | Public contracts and generated Dokka reference |
| [Architecture](docs/architecture.md) | Responsibilities, dependency rules, extension points |
| [Notification images and links](docs/rich-notifications.md) | Image limits, click behavior, network access |
| [Data and lifecycle](docs/data-and-lifecycle.md) | Collected data, synchronization, retries, storage |
| [Compatibility](docs/compatibility.md) | API baseline, persistent components, versioning |
| [Testing](docs/testing.md) | Local checks, consumer verification, emulator scenarios |
| [Release process](docs/releasing.md) | Maven artifacts, publishing and maintaining releases |
| [Licensing](docs/licensing.md) | SDK license, attribution and distribution scope |
| [Roadmap](docs/roadmap.md) | Current limits and directions for growth |
| [Contributing](CONTRIBUTING.md) | Development and review conventions |
| [Changelog](CHANGELOG.md) | Changes and migration notes |

## Working on the SDK

Source code and contribution instructions are available in [PushPortLibraryAndroid](https://github.com/IamFromUA/PushPortLibraryAndroid) and the [contributing guide](CONTRIBUTING.md). The [verification workflow](.github/workflows/verify.yml) uses Gradle to test the SDK, verify the local Maven package and compile a Kotlin/Java consumer with R8. See [release process](docs/releasing.md) for publisher setup.

## Design

The public surface is `PushPort`, `PushPortConfig`, and `PushPortStatus`. Internal services receive their dependencies through constructors. Core synchronization rules depend on models and narrow interfaces; Android, storage, JSON and Firebase stay in adapters. Architecture tests enforce these boundaries.

SDK initialization remains explicit. No activity is retained, no permission dialog is shown automatically, and no network call runs in the public initialization or status method.

The default service address is `https://pushport.dev`. For local testing, use an explicit development endpoint as shown in the quick start.

## License and publisher

Copyright 2026 Oleh Yurkov. Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE) for attribution and [licensing](docs/licensing.md) for scope and redistribution guidance.

PushPort is the project's public name. The SDK publisher and copyright holder is Oleh Yurkov; the public contact is [support@pushport.dev](mailto:support@pushport.dev).

This license covers this SDK module, including its source code, tests, build scripts and documentation. Other PushPort services and sibling projects have separate terms. Third-party dependencies retain their own licenses. The [release process](docs/releasing.md) documents publisher setup and future releases.
