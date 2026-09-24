# Partner API and application links

The SDK registers the installation and exposes the confirmed user profile. Offer URL construction belongs entirely to the host application: choose its query parameter name and any prefix/suffix in application code.

Use `dev.pushport:android-sdk:0.0.2` or newer for the `PushPort.user(context)` profile API. Version `0.0.1` does not expose it.

After successful synchronization:

```kotlin
val userId = PushPort.user(context)?.userId
if (userId != null) {
    val offerUrl = android.net.Uri.parse("https://example.com/offer")
        .buildUpon()
        .appendQueryParameter("sub_id_10", userId + "::|00030")
        .build().toString()
    // Open offerUrl using the application's existing flow.
} else {
    PushPort.sync(context) // asynchronous; retry after successful synchronization
}
```

The parameter and suffix above are partner-specific examples. Use a URL builder to encode the value once, and avoid adding duplicate query parameters to an existing URL. Wait for a pending login/logout to finish before reading the new confirmed profile, and respect the application's collection consent settings.

The partner must return the original PushPort user UUID without any decoration to our API. It is not Android ID, the FCM token, or the installation UUID. `external_id` in an offer macro is a partner naming convention, independent of `PushPort.login` account identity.

Create the partner's app-scoped API key in Application → Settings → API keys. Keep it on the partner's server, never in the APK or URL. The API supports one user per lookup and up to 10,000 IDs per push request. See [the API contract](https://pushport.dev/en/docs/#external-api).
