# Published SDK consumer check

This independent Android Gradle build consumes `dev.pushport:android-sdk:0.0.1` from the SDK repository's local Maven output. It has no project dependency on the SDK or backend. Kotlin and Java source call the public API, and the release variant enables R8/resource shrinking.

From the SDK repository root, publish the SDK and run:

```shell
./gradlew publishReleasePublicationToLocalReleaseRepository
python scripts/verify-publication.py
./gradlew -p examples/sdk-consumer assembleDebug assembleRelease lint
```

Set `ANDROID_HOME` to the installed SDK or provide a local, untracked `local.properties` in this fixture. The application has no launcher and does not initialize PushPort or contact a backend. It verifies compilation/packaging, not notification delivery. Use a separate test application for live device tests, following the SDK quick start.
