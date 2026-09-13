# Release process

## Current state

The project produces a release AAR, sources JAR, Dokka `javadoc` JAR, POM and Gradle module metadata. Publication currently targets `build/maven-repository` in the workspace. The SDK has its own wrapper, settings, consumer fixture and verification workflow. Remote origin is `https://github.com/IamFromUA/PushPortLibraryAndroid.git`. The manual Publish Maven Central workflow is configured; its environment credentials must be supplied before upload.

Publisher setup completed on 2026-09-13:

- Sonatype Central namespace `dev.pushport` is verified through DNS ownership of `pushport.dev`.
- The owner selected Apache License 2.0 for the SDK. Copyright holder and publisher: Oleh Yurkov; project name: PushPort; public contact: `support@pushport.dev`.
- The POM declares license, developer, SCM and issue tracker metadata. Canonical `LICENSE` and `NOTICE` files are included in all published code/documentation archives. See [licensing](licensing.md).

Version `0.0.1` is a local pre-release version. Do not describe it as a public release until it can be resolved from the public repository.

This candidate sends optional `androidId` metadata. Deploy the compatible backend before releasing the SDK; it accepts missing/null values from older clients and stores the field in the installation snapshot.

## Prepare a candidate

1. Review changes to the public API, dependencies, manifest and persisted state.
2. Update the changelog and version deliberately. Never replace a version already released publicly.
3. Verify the intended HTTPS backend endpoint. Public artifacts must not embed localhost, private keys or a temporary ngrok test endpoint.
4. Run `check` and `dokkaGenerate` from this standalone repository.
5. Publish locally and build the Maven consumer in debug and release/R8 modes.
6. Run the relevant emulator/device scenarios, including an update without clearing data when state or background components change.
7. Review the POM, module metadata, source/documentation archives and AAR contents. Check that `META-INF/dev.pushport/android-sdk/LICENSE` and `NOTICE` match the canonical files in the AAR's `classes.jar` and both publication JARs.

```shell
./gradlew check dokkaGenerate
./gradlew publishReleasePublicationToLocalReleaseRepository
python scripts/verify-publication.py
./gradlew -p examples/sdk-consumer assembleDebug assembleRelease lint
```

## Before the first Central publication

- Commit and push the prepared SDK on the owner's explicit command, then confirm the GitHub verification workflow passes. Review the existing license, developer and SCM metadata before uploading.
- Configure the Portal publishing token, GPG signing key and verification of the public key. Keep private credentials outside source control and distribution artifacts.
- Upload and validate a candidate, review it, then publish the selected version.
- Verify resolution from a fresh consumer/cache using only the public repositories and document the released coordinates.

These are publisher setup steps, not Firebase setup. The application's Firebase credentials remain per application in the backend.

References: [Central requirements](https://central.sonatype.org/publish/requirements/), [namespace verification](https://central.sonatype.org/register/namespace/), [Portal token](https://central.sonatype.org/publish/generate-portal-token/), [Android publication](https://developer.android.com/build/publish-library/upload-library).

## GitHub publication environment

The manual `publish.yml` workflow checks the immutable tag against the build version and reruns package/consumer verification. In the **maven-central** environment set `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` (the Portal User Token pair), `SIGNING_KEY` (armored private PGP key), `SIGNING_KEY_ID` (last eight hex digits), and `SIGNING_PASSWORD`. Only the public PGP key goes to a keyserver. The workflow signs in memory and invokes `publishAndReleaseToMavenCentral`. Ordinary pushes only verify. Public registry availability must be checked before announcing the Maven coordinate as released.
