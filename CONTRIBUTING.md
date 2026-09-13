# Contributing to PushPort Android SDK

Work from this SDK repository root, with JDK 17, Android SDK platform 36 and build tools installed. Set `ANDROID_HOME` and use the checked-in Gradle wrapper. Tests need no production accounts or private credentials.

## Development loop

```shell
./gradlew formatKotlinStyle
./gradlew check dokkaGenerate
```

Use `gradlew.bat` on Windows. Tests can be selected through Gradle's `--tests` filter. See [testing](docs/testing.md) for consumer/R8 and emulator checks.

## Code conventions

- Follow Kotlin official style; ktlint enforces formatting and explicit API mode enforces public declarations.
- Give each class a clear responsibility. Keep dependencies directed toward core models and ports.
- Inject external capabilities through constructors. Compose implementations only in `SdkRuntime`.
- Keep Android, Firebase and JSON out of the core. Architecture tests enforce this boundary.
- Public KDoc states lifecycle, threading, return values and meaningful failure behavior.
- Catch only failures that the layer can interpret or recover from. Always propagate coroutine cancellation.
- Keep logs and diagnostics free of tokens, installation credentials and message contents.
- Preserve stored identity and unfinished events on upgrades. Never silently replace unknown future state.

## Tests and review

Tests should describe behavior: a late response, unavailable FCM, token rotation, malformed configuration, a duplicate message, or an interrupted write. Use virtual time for asynchronous timing tests, in-memory ports for core behavior, and Robolectric/MockWebServer for adapters.

For a change, explain the trigger, resulting behavior, and validation. Review public signatures, dependencies, manifest/resource changes, cancellation, privacy and migration impact where relevant. Add a changelog entry when consumers can observe the change.

An API baseline difference is a review request, not permission to overwrite the baseline. Do not weaken architecture or compatibility checks to make an unreviewed design pass.

## Repository workflow

The standalone repository is [IamFromUA/PushPortLibraryAndroid](https://github.com/IamFromUA/PushPortLibraryAndroid). The verification workflow checks the SDK, local Maven metadata and an independent consumer on GitHub Actions after code is pushed. It requires no secrets and performs no remote publication.

The same directory can remain the `:PushPortLibrary` subproject in the owner's local PushPort workspace. When invoked from that workspace root, existing commands such as `./gradlew -PlibraryOnly=true :PushPortLibrary:check` still apply. Standalone commands use this repository's own wrapper and settings. Local Maven output is under the invoking root project's `build/maven-repository`.

When initially staging files on Windows, preserve the executable Git mode for `gradlew` with `git update-index --chmod=+x gradlew`.

Create commits and push only on the owner's explicit command. Do not publish artifacts or change the distribution license as part of routine cleanup.

## License

This SDK uses [Apache License 2.0](LICENSE). Contributions intentionally submitted for inclusion are covered by section 5 of that license unless explicitly stated otherwise. Preserve existing attribution and license notices. Use `SPDX-License-Identifier: Apache-2.0` in new Kotlin files and identify the appropriate copyright holder; do not replace another contributor's copyright notice.
