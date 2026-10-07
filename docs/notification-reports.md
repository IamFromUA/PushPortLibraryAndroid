# Notification diagnostics (SDK 0.0.3+)

No new initialization call is required. Open the application's **Delivery reports** tab in PushPort. Filter by date, user ID, installation ID or message ID; results use 25/50/100-row pages. Each row has an event timeline.

| Metric | Meaning |
| --- | --- |
| Accepted | FCM accepted the send; device delivery is not guaranteed. |
| Received | SDK accepted an owned, valid message, deduplicated by message ID. |
| Notification created | Android accepted the notification API call. This does not prove the user saw it. Disabled permission/channel or SDK subscription is recorded separately. |
| Image downloaded / attached | Image decoded successfully / notification was updated with it. Size, download duration and HTTP status are recorded. |
| Image error | Bounded reason: size limit, non-image response, HTTP error, redirect, invalid URL, unavailable host, timeout, decode failure. Text remains available. |
| Clicked | User activated the notification's content intent. SDK 0.0.2 clicks remain counted without new device timestamps. |
| Link launched | Android started an external link handler; this does not confirm loading the remote page. |
| Direct return | A new foreground session starts within 60 seconds of a notification click. |
| Influenced return | A new session starts within 1 hour of receipt without a click. This is an estimate, not causal attribution. |

Foreground sessions require at least 30 seconds in the background to count again. Rotation and short Activity transitions do not increase the session count. Duration uses a monotonic clock; abrupt process death can lose the uncheckpointed tail of a session. There is no deposit/registration/redeposit inference; the partner must supply those events.

Diagnostics share the existing consent controls. They contain random event IDs, message IDs, type, timestamps, reason codes and numeric diagnostics; no notification text, URLs, credentials or raw exception messages. No additional runtime permissions are requested. Disclose this collection and its purposes in the app's privacy policy and store declarations.

The local outbox persists across process restarts, holds up to 1,000 events, uploads at most 100 per request and retries transient failures. Oldest events are removed on overflow; the report exposes the cumulative dropped count. Accepted event IDs prevent retry duplicates. A 404/405 or missing server capability disables uploading until a later configuration sync. All normal registration and legacy click APIs remain usable. Background restrictions, force-stop, denied consent and network loss can delay/prevent reports; absence of an event is not proof of failure.

The image byte setting is refreshed during configuration sync, not pushed instantly to the device. Pixel limits (4,096 per dimension, 8 million decoded pixels) and the 10-second request timeout remain enforced even if the byte limit is increased.
