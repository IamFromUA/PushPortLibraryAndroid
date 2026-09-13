# Testing

## Automated checks

Run from the SDK repository root:

```shell
./gradlew check dokkaGenerate
```

| Check | Detects |
| --- | --- |
| ktlint and explicit API compilation | Style issues and accidental implicit public declarations |
| Core tests | Revisions, identity, acknowledgements, retry limits, cancellation, subscription and deduplication |
| Token recovery tests | FCM timeout, project changes, temporary failure, recovery and invalid configuration |
| Public contract tests | Locale/URL validation, Java constructor support and token redaction |
| Robolectric + MockWebServer | Atomic storage, schema compatibility, HTTP bounds/redirects, Firebase coexistence and Android adapters |
| ArchUnit | Core/adapters separation and absence of package cycles |
| Android Lint | Android API, manifest, resources and integration issues |
| `apiCheck` | Changes in the release AAR's reviewed Kotlin/JVM signatures |
| Dokka | Generation of the public API reference; undocumented public declarations fail the check |

Robolectric storage tests use API 28 because newer Android `AtomicFile` implementations rely on POSIX rename-overwrite behavior unavailable in Windows Robolectric. Validate current Android separately on an emulator/device. Production behavior cannot be inferred from JVM tests alone.

Test report: `build/reports/tests/testDebugUnitTest/index.html`. Lint report: `build/reports/lint-results-debug.html`.

## Consumer check

The included Kotlin/Java Android fixture consumes the actual Maven AAR and its transitive dependencies. It has no project dependency on the SDK and uses an exclusive local repository for `dev.pushport`, so a previously published Central version cannot silently replace the candidate under test.

```shell
./gradlew publishReleasePublicationToLocalReleaseRepository
python scripts/verify-publication.py
./gradlew -p examples/sdk-consumer assembleDebug assembleRelease lint
```

Set `ANDROID_HOME` for both builds. The release fixture enables R8 and resource shrinking; Kotlin and Java entry points remain reachable so the optimized build checks both. The fixture has no launcher and does not register an installation or send network requests. These checks validate compilation and packaging, not notification delivery.

The publication verifier checks license, publisher and SCM metadata, Gradle module coordinates, canonical notices in all published archives, and artifact checksums. It works on local outputs without publishing credentials.

## Device scenarios before a release

Use a dedicated application and test Firebase project. Keep service-account keys on the backend.

1. Install the published SDK in the sample, launch it, grant permission and verify server registration.
2. Send a real notification, verify display, tap it, and verify the opened event.
3. Change the app locale and system timezone; verify the server's metadata updates.
4. Disable subscription and verify a newly sent notification is not displayed.
5. Deny OS notification permission, then restore it and check status/synchronization.
6. Disconnect networking, queue changes, reconnect and verify recovery.
7. Update the app without clearing data; verify the same installation ID, preferences and pending work.
8. Exercise background, process restart and a host app with its own Firebase configuration.

For live scenarios, integrate the SDK into a dedicated test application as described in the quick start. A successful historical device test does not substitute for testing a changed transport or storage migration. Record what was actually exercised and list any skipped scenarios.
