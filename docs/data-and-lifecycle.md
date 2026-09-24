# Data and lifecycle

The user/session/consent additions below are unreleased; see [data and users](data-and-users.md) for version availability and integration.

## Data handled by the SDK

| Data | Purpose |
| --- | --- |
| Random installation UUID and generated installation secret | Identify and authenticate this app installation |
| Android ID (`Settings.Secure.ANDROID_ID`), when available | Device metadata; backend V11 links device profiles within an app using this value, while installation UUIDs remain separate |
| FCM token | Address push messages to the device |
| Package, app version, SDK version | Bind registration to the correct application and diagnose compatibility |
| Effective language/locale, ordered locale lists, timezone | Language targeting and scheduling inputs |
| Notification permission and subscription preference | Record whether notifications can be displayed |
| Android version/API, manufacturer, model | Device compatibility diagnostics |
| Carrier, foreground sessions and duration | Activity metrics distinct from background sync |
| Linked user ID and explicitly provided External ID/tags/contact/location/events | Optional app-owned user model; see [data and users](data-and-users.md) |
| Message IDs and opened events | Deduplication and acknowledgement |

The SDK does not request location permission, read GPS, Advertising ID, IMEI or contacts. A country subtag in a locale is a language preference, not a verified physical country. No IP-based geolocation is implemented in the SDK.

Android ID is read after initialization and sent as the optional `androidId` registration field. On Android 8.0+, its scope is the combination of app signing key, Android user and device; it is not a universal person identifier. A factory reset or signing-key change may change it. See [Android's identifier contract](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID). The SDK requires no additional Android permission for this field. Missing, denied, malformed or all-zero values become `null`; a valid hexadecimal value is sent in lowercase without padding. Registration and push delivery continue when it is unavailable.

The local snapshot stores this value alongside the other metadata and detects changes during synchronization. Existing stored snapshots without `androidId` remain readable and keep their installation identity. Diagnostic string representations omit its value. Android ID collection belongs in the integrating application's description of the data collected by this SDK.

With backend V11, the first registration uses a valid Android ID to link the device profile within the same PushPort app. Reinstalling normally keeps one user with two distinct installations. Android ID never replaces the installation UUID or secret, and explicit account login remains separate. See [default identity and fallback behavior](data-and-users.md#default-android-behavior--no-extra-integration-calls).

Data starts being registered after explicit initialization, subject to the configured consent gate. `setSubscribed(false)` suppresses new PushPort notification presentation; it is not a data-deletion or collection-consent API. Call `setConsentRequired` before initialization and `setConsentGiven` from the host consent flow. Server-side deletion remains separate; revoking consent does not erase stored history.

## Initialization and persistence

Initialization binds an installation to one PushPort application and endpoint, restores cached Firebase options, creates the notification channel, registers lifecycle callbacks, and schedules work. Repeating initialization with the same binding preserves identity and preferences.

State is stored with `AtomicFile` in the application's private `noBackupFilesDir/pushport-installation.json`. It is excluded from Android backup by directory choice and has schema version 1. The contents are not separately encrypted by the SDK. Reinstalling/clearing app data creates a new installation; a server-side delete API is not currently exposed.

Unknown future schemas and invalid storage are not silently overwritten. A migration must preserve the installation ID, secret, preferences, revision, and pending opened events. Access is serialized within a process. Multiple Android processes are not supported.

## Synchronization

WorkManager enforces network connectivity, schedules periodic work at a 15-minute minimum interval, and applies OS scheduling limits. The interval is not an exact timer or delivery guarantee. Foreground resumes and environment changes also schedule refreshes.

The coordinator gets Firebase options, refreshes the token, captures the latest snapshot, registers changed data, then sends queued opened events and user operations. Unchanged registration is refreshed after a 12-hour heartbeat. A successful request acknowledges only the revision it actually sent; changes made during the request remain pending.

Transient HTTP/I/O failures retry with exponential backoff. FCM timeout/temporary failure also requests a bounded retry while allowing metadata registration and opened-event uploads. A cached token is retained only while the Firebase configuration is unchanged. Invalid Firebase settings stop the current work with a diagnostic; explicit `firebase: null` means the server has disconnected Firebase. A malformed configuration response is not treated as a disconnect.

Initial work can be followed by at most eight retries for a transient failure. Later foreground or periodic work can try again after that limit. Parent coroutine cancellation propagates and never acknowledges unfinished work.

## Notification lifecycle

Only messages with a matching PushPort app ID and a canonical message UUID are accepted. Other FCM messages can continue to the host's receiver. A bounded history of the latest 100 accepted message IDs suppresses duplicates.

The default channel ID is `pushport_default`. Android permission and the local subscription preference both control presentation. Notification clicks use an internal, non-exported activity to queue an opened event and launch the host application. Up to 100 pending opened IDs are retained and removed individually after server acknowledgement.

There is no exactly-once delivery guarantee. Network loss after server acceptance can cause an event to be resent; the server must handle duplicate message events idempotently.

## Diagnostics

`PushPortStatus.toString()` omits the token, and internal identity/snapshot representations redact credentials. Explicit getters and private storage still contain device-specific values. Bug reports should contain SDK/app/Android versions and sanitized diagnostics, not raw state files, FCM tokens, installation secrets, or service-account keys.
