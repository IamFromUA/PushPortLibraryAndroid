# Development roadmap

This is a direction for future work, not a promise that these capabilities are implemented.

## First public release

- Signing and public Maven distribution. The publisher account, verified namespace and Apache 2.0 license are configured; see [release process](releasing.md).
- Stable HTTPS backend endpoint and a clean consumer integration test.
- First source push and successful remote verification run; the standalone repository and CI workflow are prepared.
- A documented consumer toolchain/device compatibility matrix.

## Integration experience

- Structured error/status events with lifecycle-safe observation, without exposing transport types.
- Notification appearance and click behavior configuration with stable defaults.
- Explicit data collection consent and installation deletion contracts with the backend.
- A testable story for custom WorkManager initialization and additional Android processes.

## Audience and platform features

- External user identity and tags, with explicit account-switch semantics.
- Additional targeting metadata only after purpose and collection rules are defined.
- Transport modules for other Android ecosystems only when a supported provider is selected.

Server-side scheduling, queues and administration remain backend responsibilities. The Android SDK should collect the agreed installation state and deliver notifications without duplicating campaign scheduling logic on devices.

Every addition needs a public contract, server compatibility review, appropriate tests, migration notes and an API-baseline review. New modules should follow independently useful features rather than an anticipated hierarchy.
