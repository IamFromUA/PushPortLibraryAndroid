# Data collection and user identity

Available starting with **Android SDK 0.0.2**. The production backend at `https://pushport.dev` supports this contract; the current Android release is **0.0.5**. Existing installations and their credentials are preserved when upgrading from 0.0.1.

## Automatic data

| Data | Source and behavior |
| --- | --- |
| Installation ID | Random UUID, one subscription per app installation. Survives process restarts; no-backup storage prevents restoration onto another install. |
| User ID | Server-generated UUID. Each fresh installation creates an independent anonymous user. Updates/restarts preserve the existing user. Android ID does not merge profiles or copy tags after reinstall. Verified External ID can link an authenticated account across devices. |
| Android ID | Optional `Settings.Secure.ANDROID_ID`. Stored as app-scoped device history, never used to restore/merge users, as an installation credential or as proof of account ownership. Not a universal person identifier. |
| Delivery | FCM token and token changes, Android permission, app opt-in/out, invalid-token history on the server. |
| Localization | Language, effective locale, app/system locale lists, IANA timezone; updates on foreground/configuration changes. `setLocale` overrides the reported locale. |
| Network | Carrier name where Android exposes it without additional permissions. Backend records last IP and approximate country from trusted ingress, not GPS. A VPN may change country. |
| Software/hardware | Package, app version/code, SDK version, Android version/API, manufacturer/model, platform. |
| Activity | First/last session start, cumulative session count and foreground milliseconds. Background synchronization is recorded separately as lastSeen. |
| Push interaction | Notification opens, queued persistently; existing delivery outcomes remain on the backend. |

A foreground visit starts a new session after at least 30 seconds in the background. Short activity transitions/rotation do not add sessions. Duration uses Android's monotonic clock, is checkpointed every 60 seconds and on backgrounding. The server clamps future device timestamps to receipt time. A hard process kill can lose time since the last checkpoint; these are usage metrics, not a billing clock. Server counters do not increase again on retries and do not decrease if an older SDK sends a payload without usage.

The Android SDK does **not** automatically read advertising ID, IMEI, contacts, email, phone number or GPS coordinates. Firebase Analytics is not needed. PushPort's user ID, installation UUID, Android ID and FCM token have different lifecycles.

## Optional data and consent

Call the consent requirement in `Application.onCreate`, **before initialization**, when your app requires consent:

```kotlin
PushPort.setConsentRequired(this, true)
PushPort.initWithContext(this, "YOUR_PUSHPORT_APP_ID")
// After your own consent flow:
PushPort.setConsentGiven(this, true)
```

Before consent, PushPort does not generate an installation identity, capture metadata, initialize its Firebase app or make registration requests. Revocation stops subsequent PushPort synchronization, event capture and notification presentation. It cannot recall in-flight requests, erase already stored server history or stop a separate Firebase integration owned by the host. Consent persists. Permission to show notifications is separate from consent to collect data.

Only supply the following data intentionally through your app's corresponding disclosure/permission flow:

```kotlin
PushPort.setTags(context, mapOf("plan" to "pro", "obsolete" to null))
PushPort.setEmail(context, "person@example.com") // null clears
PushPort.setPhoneNumber(context, "+49123456789") // E.164; null clears
PushPort.setLocation(context, latitude, longitude) // two nulls clear
PushPort.trackEvent(context, "purchase", mapOf("product" to "example"))
```

No location permission is requested by PushPort; the host supplies authorized coordinates. Contacts are profile metadata, not email/SMS subscriptions. Coordinates represent the most recently explicitly supplied user location. Tags/contact/location are shared by installations linked to this user. Custom events are recorded with user and installation IDs; they do not start journeys or trigger delivery automatically.

## Identify an authenticated user

### Default Android behavior — no extra integration calls

Initialize PushPort normally. Every fresh installation creates an independent anonymous user, even if its Android ID matches a previous installation. A reinstall creates a new installation UUID, secret and userId; existing installations and profiles retain their identity. Android ID is recorded only as device history scoped to the PushPort App ID. Tags and registration/purchase state are not copied to a reinstall; report the relevant events for the new user or use explicit verified login.

Missing, zero, malformed or the known non-unique `9774d56d682e549c` Android ID is ignored for device history. Telemetry cannot switch the user, undo logout or log into an authenticated account. Android ID is client-supplied metadata and is not authentication. Verified login is required to link an authenticated account across installations.

The host does not need an extra call for anonymous registration. If it uses OneSignal.login(androidId), that is an explicit identity-linking choice in the host and is not the default PushPort behavior.

### Explicit account login

1. Initialize and wait until `PushPort.status(context).lastSyncedAt` is non-null.
2. Send `PushPort.status(context).installationId` to **your own authenticated backend**.
3. Your backend verifies its logged-in user, then calls `POST /api/v1/apps/{appId}/identity-tokens` on PushPort using its owner session bearer token with `{"installationId":"...","externalId":"your-stable-account-id"}`.
4. Pass the returned short-lived `token` to the app, then call:

```kotlin
PushPort.login(context, externalId, identityToken)
val confirmed = PushPort.user(context) // local, last server-confirmed profile; initially null
// When your account session ends:
PushPort.logout(context)
```

Never embed owner credentials in Android. A proof is bound to app + installation + exact External ID, expires after five minutes and is consumed once. After a failed/expired login obtain a new proof and call login again. The identity-proof endpoint uses the backend's existing owner authentication. Public scoped API keys and partner adapters support lookup and sending; they cannot mint login proofs.

An existing External ID switches this subscription to the known user without copying anonymous properties over that user. A new External ID retains anonymous properties. If the starting profile is device-linked, these properties are copied into a separate account profile so other installations linked only by Android ID do not gain access to the account. A plain anonymous profile keeps its user ID. Switching from an identified user to a different new account creates a separate profile. Logout detaches only this installation; other devices remain linked. Anonymous/device-profile logout is a no-op for identity. Android ID and push token do not change on account login/logout. Reinstalling creates a new anonymous user; reconnecting an authenticated account requires verified login.

Calls enqueue changes; they do not wait for a server response. Check `status().syncError` and `user()` for confirmation. Up to 100 operations persist in no-backup storage; a full queue throws instead of silently losing events. Retries use monotonic revisions to avoid duplicate application. Login/logout discard **unsent** user operations from the prior account, while already in-flight requests may finish. A failed login blocks subsequent queued user properties so they cannot land on the previous account. Renewed login or logout replaces that queue. Telemetry synchronization continues independently. Metadata rejected permanently with HTTP 400/422 is removed and reported through syncError; later operations proceed on the next synchronization. Authentication failures remain queued until corrected.

Limits: 50 tags, keys ≤64 and values ≤256 characters; External ID ≤128 without leading/trailing whitespace; event name starts with a letter and contains letters/digits/`_.-`, ≤64; up to 20 event properties (64/256); email ≤254; phone in E.164. Null tag values remove keys. Property values and credentials are redacted from model `toString()` output.

## OneSignal comparison

The design follows the user/subscription distinction and the automatic/manual data categories documented by OneSignal; it is not a wire-compatible clone. PushPort treats Android ID as history only; verified account linking requires a server-issued proof. Apple, Web and KMP SDK 0.0.2 support confirmed profiles, tags, verified login/logout, consent and optional contact/location/events. Apple/Web do not collect Android ID. KMP reuses the native SDK and creates no second installation.

References: [SDK data collection](https://documentation.onesignal.com/docs/en/data-collected-by-the-onesignal-sdk), [user/subscription properties](https://documentation.onesignal.com/docs/en/user-subscription-properties), [Android ID](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID).
