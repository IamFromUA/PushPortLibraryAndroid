# PushPort Android SDK

This is the standalone Kotlin Android SDK repository for `dev.pushport:android-sdk`, hosted at https://github.com/IamFromUA/PushPortLibraryAndroid. It also remains usable as the `:PushPortLibrary` module in the owner's local workspace. Do not introduce dependencies on sibling repositories.

Distribute the SDK as a normal Maven AAR dependency with transitive dependency metadata. Developers integrate it through Gradle and initialize it with their PushPort App ID. Keep SDK build and verification tasks in Gradle and GitHub Actions; do not add Docker infrastructure to this repository.

Use the checked-in Gradle wrapper with JDK 17 and Android SDK 36. From this directory, run `./gradlew check publishReleasePublicationToLocalReleaseRepository`, `python scripts/verify-publication.py`, then `./gradlew -p examples/sdk-consumer assembleDebug assembleRelease lint` for publication changes. The consumer build checks the local Maven artifacts and requires no accounts, Firebase files or running backend.

Keep `dev.pushport.sdk` and the reviewed `api/PushPortLibrary.api` baseline stable. Do not run `apiDump` to hide an unreviewed API difference.

The SDK is licensed under Apache-2.0. Copyright holder and publisher: Oleh Yurkov. Preserve notices and third-party attribution. Publisher contact: support@pushport.dev. Public builds use https://pushport.dev.

Create commits, push, tag releases and publish to Maven Central only on the owner's explicit command. Keep signing keys and publishing tokens outside the repository and distribution artifacts. The verification workflow does not publish packages.
