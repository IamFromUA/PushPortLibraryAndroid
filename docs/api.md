# Public API

The supported integration surface is the `dev.pushport.sdk` package's `PushPort`, `PushPortConfig` and `PushPortStatus` types. Android entry-point classes in that package are public only so Android/WorkManager can instantiate them; they are annotated `@RestrictTo` and excluded from the generated user reference.

## Entry points

| Method | Behavior |
| --- | --- |
| `initWithContext(context, appId)` | Save settings, restore Firebase options, register lifecycle callbacks, schedule synchronization |
| `init(context, config)` | The same initialization with an explicit endpoint |
| `restore(context)` | Restore an existing installation; return false if no settings are saved |
| `sync(context)` | Capture metadata and schedule work; no-op before initialization |
| `setLocale(context, tag)` | Persist a BCP-47 override; null resumes automatic detection |
| `setSubscribed(context, enabled)` | Persist the app's preference and schedule a server update |
| `requestNotificationPermission(activity)` | Request Android 13+ notification permission on the main thread |
| `status(context)` | Read local registration state and current Android permission |
| `handleNotificationIntent(context, intent)` | Advanced integration: consume the message-ID extra and queue an opened event |

All methods are callable from Java as static methods. `PushPortConfig` has two- and three-argument Java constructors. Public APIs use no Firebase, WorkManager, JSON or coroutine types.

## Execution and errors

Initialization, configuration updates and reads can perform synchronous local file access. HTTP and token retrieval run in WorkManager on an IO dispatcher. The permission helper requires a visible activity and the main thread. Use the SDK in the application's main process.

Invalid configuration, a conflicting persisted app/backend binding, and invalid locale input produce `IllegalArgumentException`. Setters also reject use before initialization. Public methods do not silently discard corrupt installation storage; recovery must preserve identity or be an explicit reset of test data.

Background synchronization failures are recorded in `syncError` or `pushError`. These are human-readable diagnostics, not stable machine-readable codes. Coroutine cancellation propagates to WorkManager and does not acknowledge unsent work.

## Status semantics

- `configured`: settings are saved locally.
- `installationId`: persistent app-installation identifier, independent of hardware identifiers.
- `fcmToken`: current registration token; sensitive device-specific data.
- `locale` / `systemLocales`: last captured locale information.
- `notificationsEnabled`: effective Android application/channel permission at read time.
- `subscribed`: the app's PushPort preference, independent of OS permission.
- `lastSyncedAt`: Unix epoch milliseconds of the last successful registration, nullable before success.
- `syncError` / `pushError`: last diagnostics, nullable when clear.

The status string representation omits the FCM token. The explicit token getter remains available; callers are responsible for handling its value appropriately.

## Generated reference

```shell
./gradlew dokkaGenerate
```

From the SDK repository root, open `build/dokka/html/index.html`. The same reference is included in the Maven `javadoc` JAR. KDoc is the source of truth for method-level contracts; update it with every public API change.
