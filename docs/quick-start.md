# Quick start

## Prerequisites

- A compatible Android application with an `Application` class.
- An application created in PushPort. Its package name must match the Android `applicationId`.
- Firebase credentials configured for that application in the PushPort dashboard.
- A reachable HTTPS PushPort backend and Google Play services on the test device.

`PUSHPORT_APP_ID` below means the UUID issued by PushPort. It is not the Firebase project ID, Firebase app ID, package name, or service-account key.

## Gradle

Gradle downloads the SDK and its dependencies from the standard repositories. In the application's `settings.gradle.kts`, ensure these repositories are present:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Add the dependency in the application module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.pushport:android-sdk:0.0.2")
}
```

For unpublished local development builds only, add the local Maven repository described below. Copying a raw AAR by itself loses transitive dependency metadata and is not the supported installation method.

## Kotlin

```kotlin
import android.app.Application
import dev.pushport.sdk.PushPort

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PushPort.initWithContext(this, "PUSHPORT_APP_ID")
    }
}
```

Register your application class in the host manifest:

```xml
<application android:name=".MyApplication" />
```

If the application already has an `Application` class, add initialization there. Applications using additional Android processes must initialize this SDK in the main process only.

From a visible activity, after explaining why the app needs notifications:

```kotlin
PushPort.requestNotificationPermission(this)
```

The helper requests Android 13+ notification permission and does nothing on older versions. You can instead use the host application's Activity Result API permission flow. A denied permission is not overridden by `setSubscribed(true)`.

## Java

```java
import android.app.Application;
import dev.pushport.sdk.PushPort;

public final class MyApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        PushPort.initWithContext(this, "PUSHPORT_APP_ID");
    }
}
```

From an activity: `PushPort.requestNotificationPermission(this);`.

## Locale, subscription and status

```kotlin
PushPort.setLocale(context, "de-DE")   // Override the locale reported to PushPort.
PushPort.setLocale(context, null)      // Resume Android locale detection.
PushPort.setSubscribed(context, false)
PushPort.setSubscribed(context, true)
PushPort.sync(context)                // Schedule an asynchronous refresh.

val status = PushPort.status(context)
val registered = status.lastSyncedAt != null
```

`status` is a local snapshot. Initialization, having an FCM token, and having permission are separate conditions. `lastSyncedAt` confirms registration with the server; it is not a message delivery receipt. Do not log `status.fcmToken`.

Locale and subscription changes require initialization. Normal integrations do not need custom FCM receivers, WorkManager workers, or notification intent handling.

## Local development

Publish from the SDK repository root:

```shell
./gradlew publishReleasePublicationToLocalReleaseRepository
```

In the consumer's `settings.gradle.kts` add:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("/absolute/path/PushPortLibraryAndroid/build/maven-repository")
            content { includeGroup("dev.pushport") }
        }
    }
}
```

Replace the example with the actual absolute repository path on your machine. On Windows, use forward slashes, for example `D:/PushPort/PushPortLibrary/build/maven-repository`. The included `examples/sdk-consumer` fixture already resolves this local repository.

For a test build with a specific HTTPS backend:

```kotlin
PushPort.init(context, PushPortConfig("PUSHPORT_APP_ID", "https://YOUR-TUNNEL.ngrok-free.dev"))
```

Import `dev.pushport.sdk.PushPortConfig`. Keep test configuration in the debug variant. Test endpoints and application IDs belong in your test application; they are not required to compile this repository.

An installation cannot switch backend or app ID. Use a separate debug application ID or clear that test application's data when changing the endpoint. Production updates must preserve the backend URL.

Debug-only HTTP is available for `localhost`, `127.0.0.2`, `10.0.2.2`, and `::1` with `allowInsecureLocalhost = true`. Android must also permit cleartext traffic in that debug application. HTTPS/ngrok avoids this extra configuration.

## Verify the integration

1. Launch the app and accept notification permission.
2. Inspect `PushPort.status(context)` and the dashboard's device list.
3. Send a test push to this installation and verify the visible notification.
4. Tap it and verify the opened event.
5. Change the app locale, reopen the app, and verify the updated locale in PushPort.

See [testing](testing.md) for upgrade, offline, cancellation and R8 checks.
