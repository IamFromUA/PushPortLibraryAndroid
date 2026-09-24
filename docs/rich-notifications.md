# Notification images and links

SDK 0.0.1 accepts optional `image_url` and `click_url` fields from PushPort. Configure them in the campaign editor or test-send API. No extra host dependency or manifest entry is required.

The SDK displays the text immediately. An independent WorkManager job downloads an image and applies Android BigPictureStyle to the notification if it is still active and the installation remains subscribed with permission. The update retains the click intent and uses `onlyAlertOnce`. Failed, oversized, unsupported or unavailable images leave the text notification intact. Opening the notification cancels its image job. Dismissal is checked before updating; Android does not provide an atomic check-and-update operation.

## Image contract

- Public HTTPS hostname, port 443, URL at most 1,000 characters, no embedded credentials.
- PNG, JPEG or WebP; image content type; at most 1 MiB downloaded.
- Decoded bounds at most 4,096 pixels per side and 8 million total pixels; sampled to at most 1,024 pixels per side.
- Connect/read timeout 5 seconds and total call timeout 10 seconds. No redirects, automatic HTTP retry, cookies or proxy.
- DNS answers must resolve to public addresses. The HTTP client uses those checked answers for its connection.

Image hosting receives the device request and its IP address. Avoid private URLs and expiring credentials in links. An SDK image download does not go through the PushPort backend. The dashboard loads a preview only when the operator explicitly clicks the preview button, without a referrer.

## Click behavior

A valid public HTTPS `click_url` opens through Android's URL handler after recording the opening event. If no URL is provided, it is invalid, or there is no handler, the host application's launcher opens. Custom URI schemes and `intent:` URLs are not supported. Android app links may route the HTTPS URL to an installed application.

Opening analytics indicate a notification tap reported by this installation. FCM acceptance does not establish delivery or a human view.

## Upgrade

Use `dev.pushport:android-sdk:0.0.2` for the current release. Earlier version numbers, including 0.2.0 and 0.3.0, were local builds that were never published to Maven Central. Public API calls, installation credentials, app ID, state file and notification channel remain compatible with those local PushPort builds. OkHttp and WorkManager are transitive implementation dependencies. `NotificationImageWorker` is a retained Android entry point, not an application integration API.
