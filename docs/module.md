# Module PushPort Android SDK

Push notifications and installation registration for Android, with automatic language and locale synchronization.

## Start here

Initialize `PushPort` from the host application's `Application.onCreate` with the App ID issued by the PushPort dashboard. Request notification permission separately from a visible activity. Firebase options are retrieved from the configured PushPort backend; sender credentials remain on the server.

`PushPortConfig` supports an explicit backend for development and self-hosted installations. `PushPortStatus` describes local registration, permission and synchronization state. Initialization schedules background work; it does not wait for registration or guarantee message delivery.

## Release status

Version 0.0.1 is published on Maven Central as `dev.pushport:android-sdk:0.0.1`. Consult the source distribution's README and compatibility guide before integrating or upgrading.

## License and publisher

Copyright 2026 Oleh Yurkov. Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0). Project: [PushPort](https://pushport.dev). Public contact: support@pushport.dev. The source distribution and publication archives contain the full license and attribution notice.

# Package dev.pushport.sdk

Application-facing configuration, status, and static Kotlin/Java entry points. Android runtime adapters are implementation details and are excluded from this reference.
