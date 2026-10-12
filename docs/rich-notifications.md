# Notification images and links

SDK 0.0.1 accepts optional `image_url` and `click_url` fields from PushPort. Configure them in the campaign editor or test-send API. No extra host dependency or manifest entry is required.

The SDK displays the text immediately. An independent WorkManager job downloads an image and applies Android BigPictureStyle to the notification if it is still active and the installation remains subscribed with permission. The update retains the click intent and uses `onlyAlertOnce`. Failed, oversized, unsupported or unavailable images leave the text notification intact. Opening the notification cancels its image job. Dismissal is checked before updating; Android does not provide an atomic check-and-update operation.

## Android icons (SDK 0.0.4+)

SDK 0.0.4 also accepts `large_icon` and `small_icon`, independently of `image_url`:

- `large_icon`: a public HTTPS image URL or the name of a drawable/mipmap bundled in the app. A square 256 × 256 image is recommended. Remote images use the same download limits as the big picture and are reduced to at most 256 pixels per side. A failed download keeps the text notification and its default thumbnail.
- `small_icon`: a drawable/mipmap resource name without an extension, for example `ic_stat_offer`. It must already exist in the app. Use a monochrome white silhouette on a transparent background; Android supplies the color. Remote URLs cannot be used for this icon.
- App defaults: `ic_stat_pushport_default` for the status icon and `ic_pushport_large_icon_default` for the thumbnail. Missing custom resources fall back to those defaults. If neither exists, the SDK uses its standard status icon and omits the thumbnail.

No extra initialization call or permission is required. These fields are supported by campaign/test-send forms and optional table columns `large_icon,small_icon`. Older SDK builds ignore them and continue displaying the existing text and big picture. The placement and cropping of icons depend on Android and the device manufacturer.

If release builds shrink resources, retain any dynamically selected icon names in `res/raw/keep.xml`:

```xml
<resources xmlns:tools="http://schemas.android.com/tools"
    tools:keep="@drawable/ic_stat_offer,@drawable/ic_stat_pushport_default,@drawable/ic_pushport_large_icon_default" />
```

## Image sizes

Use a landscape 2:1 picture, e.g. 1024 × 512; recommended width 300–2000 px. Color thumbnails use 256 × 256 px. Status icons use 24 dp: raster variants 24/36/48/72/96 px for mdpi–xxxhdpi, or a 24 dp vector drawable.

SDK 0.0.5+ accepts GIF as a static first frame and samples pictures to at most 2000 pixels per side. SDK 0.0.4 and earlier retain PNG/JPEG/WebP and the 1024-pixel sampling limit. Uploaded gallery GIFs are converted to PNG on the server and work with existing SDKs.

## Image download limits

- Public HTTPS hostname, port 443, URL at most 1,000 characters, no embedded credentials.
- PNG, JPEG, WebP or GIF (static first frame in SDK 0.0.5+); image content type. SDK 0.0.3 defaults to 5 MiB downloaded; the application owner can set 1–20 MiB in dashboard settings. Values are fetched and cached automatically. SDK 0.0.2 and older retain 1 MiB.
- Decoded bounds at most 4,096 pixels per side and 8 million total pixels; sampled to at most 2,000 pixels per side in SDK 0.0.5+ (1,024 in earlier builds).
- Connect/read timeout 5 seconds and total call timeout 10 seconds. No redirects, automatic HTTP retry, cookies or proxy.
- DNS answers must resolve to public addresses. The HTTP client uses those checked answers for its connection.

Image hosting receives the device request and its IP address. Avoid private URLs and expiring credentials in links. An SDK image download does not go through the PushPort backend. The dashboard loads a preview only when the operator explicitly clicks the preview button, without a referrer.

## Click behavior

A valid public HTTPS `click_url` opens through Android's URL handler after recording the opening event. If no URL is provided, it is invalid, or there is no handler, the host application's launcher opens. Custom URI schemes and `intent:` URLs are not supported. Android app links may route the HTTPS URL to an installed application.

Opening analytics indicate a notification tap reported by this installation. FCM acceptance does not establish delivery or a human view.

## Upgrade

Use `dev.pushport:android-sdk:0.0.5` for static GIF, 2000-pixel big pictures and Android icon support. Earlier version numbers, including 0.2.0 and 0.3.0, were local builds that were never published to Maven Central. Public API calls, installation credentials, app ID, state file and notification channel remain compatible with earlier PushPort builds. OkHttp and WorkManager are transitive implementation dependencies. `NotificationImageWorker` is a retained Android entry point, not an application integration API.
