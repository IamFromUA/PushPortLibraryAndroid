// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.MainThread
import dev.pushport.sdk.internal.platform.AndroidNotificationState
import dev.pushport.sdk.internal.runtime.SdkRuntime

/**
 * Registers an Android installation and delivers PushPort notifications.
 *
 * Call [initWithContext] once from `Application.onCreate` in the application's main process.
 * Only the application context is retained. Network operations run through WorkManager;
 * initialization and status calls perform short, synchronous local storage operations.
 *
 * Configuration is persisted across process restarts. The application ID and server cannot
 * change for an existing installation. This SDK supports one installation per application
 * and does not support concurrent access from multiple Android processes.
 *
 * Runtime notification permission must be requested separately through
 * [requestNotificationPermission] or the host application's permission flow.
 */
public object PushPort {
    /** Last confirmed profile; null before registration. Does not make a network request. */
    @JvmStatic
    public fun user(context: Context): PushPortUser? = SdkRuntime.from(context).users.profile()

    /** Links this installation to an account using a short-lived proof issued by your trusted backend.
     * Requires initialization and consent. Switching accounts discards unsent user operations.
     * Obtain a new proof after expiry or a failed login; never embed your backend credentials in the app.
     */
    @JvmStatic
    public fun login(
        context: Context,
        externalId: String,
        identityToken: String,
    ): Unit =
        SdkRuntime
            .from(
                context,
            ).users
            .enqueue(
                dev.pushport.sdk.internal.model
                    .UserOperation(kind = "login", externalId = externalId, identityToken = identityToken),
            )

    /** Queues detachment of this installation only; other devices stay linked. Discards unsent user operations. */
    @JvmStatic
    public fun logout(context: Context): Unit =
        SdkRuntime.from(context).users.enqueue(
            dev.pushport.sdk.internal.model
                .UserOperation(kind = "logout"),
        )

    /** Queues a tag patch (up to 50 tags, key 64/value 256 characters); null values remove keys. */
    @JvmStatic
    public fun setTags(
        context: Context,
        tags: Map<String, String?>,
    ): Unit =
        SdkRuntime.from(context).users.enqueue(
            dev.pushport.sdk.internal.model
                .UserOperation(kind = "tags", tags = tags),
        )

    /** Queues an explicitly supplied contact email; null clears it. Does not subscribe to email delivery. */
    @JvmStatic
    public fun setEmail(
        context: Context,
        email: String?,
    ): Unit =
        SdkRuntime.from(context).users.enqueue(
            dev.pushport.sdk.internal.model
                .UserOperation(kind = "email", email = email),
        )

    /** Queues an explicitly supplied E.164 phone; null clears it. Does not subscribe to SMS. */
    @JvmStatic
    public fun setPhoneNumber(
        context: Context,
        phoneNumber: String?,
    ): Unit =
        SdkRuntime.from(context).users.enqueue(
            dev.pushport.sdk.internal.model
                .UserOperation(kind = "phone", phoneNumber = phoneNumber),
        )

    /** Queues coordinates supplied with the host's permission flow; two nulls clear them. Never requests GPS. */
    @JvmStatic
    public fun setLocation(
        context: Context,
        latitude: Double?,
        longitude: Double?,
    ): Unit =
        SdkRuntime
            .from(
                context,
            ).users
            .enqueue(
                dev.pushport.sdk.internal.model
                    .UserOperation(kind = "location", latitude = latitude, longitude = longitude),
            )

    /** Queues an event exactly once per acknowledged operation. Up to 20 string properties (64/256 characters).
     * The outbox holds 100 operations; a full queue throws IllegalStateException instead of silently dropping data.
     */
    @JvmStatic
    public fun trackEvent(
        context: Context,
        name: String,
        properties: Map<String, String>,
    ): Unit =
        SdkRuntime
            .from(
                context,
            ).users
            .enqueue(
                dev.pushport.sdk.internal.model
                    .UserOperation(kind = "event", eventName = name, eventProperties = properties),
            )

    /** Call before initialization to defer all SDK network/data collection until consent is given. */
    @JvmStatic
    public fun setConsentRequired(
        context: Context,
        required: Boolean,
    ): Unit = SdkRuntime.from(context).consent(required = required)

    /** Persists consent. Revocation stops future collection and notification presentation, but does not erase server history. */
    @JvmStatic
    public fun setConsentGiven(
        context: Context,
        given: Boolean,
    ): Unit = SdkRuntime.from(context).consent(given = given)

    /**
     * Initializes with the standard PushPort endpoint embedded in this SDK build.
     *
     * Repeated calls with the same configuration preserve the installation ID and subscription.
     * Returning from this method confirms local initialization, not registration with the server.
     * Observe [status] to inspect the last successful synchronization.
     *
     * @param context any context belonging to the host application.
     * @param appId the canonical application UUID shown in the PushPort dashboard.
     * @throws IllegalArgumentException if the ID is invalid or the installation is already bound elsewhere.
     * @throws IllegalStateException if the SDK was built without a default endpoint.
     */
    @JvmStatic
    public fun initWithContext(
        context: Context,
        appId: String,
    ) {
        check(BuildConfig.DEFAULT_SERVER_URL.isNotBlank()) {
            "The SDK publisher must set -Ppushport.serverUrl=https://... when building the library. Use PushPort.init for local development."
        }
        init(context, PushPortConfig(appId, BuildConfig.DEFAULT_SERVER_URL))
    }

    /**
     * Initializes with an explicit endpoint, typically for local testing or self-hosted PushPort.
     *
     * Has the same lifecycle and persistence behavior as [initWithContext].
     * @throws IllegalArgumentException if [config] is invalid or conflicts with persisted settings.
     */
    @JvmStatic
    public fun init(
        context: Context,
        config: PushPortConfig,
    ): Unit = SdkRuntime.from(context).initialize(config)

    /**
     * Reinstates lifecycle observation and scheduled work from saved settings.
     *
     * @return `false` when this installation has never been configured; otherwise `true`.
     * @throws IllegalArgumentException if persisted settings are incompatible with this application's build.
     */
    @JvmStatic
    public fun restore(context: Context): Boolean = SdkRuntime.from(context).restore()

    /**
     * Captures current metadata and schedules a token/registration check.
     *
     * Does nothing before initialization. WorkManager requires connectivity and applies Android's
     * background limits; this method does not guarantee immediate delivery or await completion.
     */
    @JvmStatic
    public fun sync(context: Context): Unit = SdkRuntime.from(context).controller.sync()

    /**
     * Persists the application's subscription preference and schedules synchronization.
     *
     * Setting `false` immediately suppresses presentation of new PushPort messages on this device.
     * Setting `true` still requires Android notification permission. Already displayed notifications
     * are unaffected. The server sees the new preference after the next successful synchronization.
     *
     * @throws IllegalArgumentException if PushPort has not been initialized.
     */
    @JvmStatic
    public fun setSubscribed(
        context: Context,
        subscribed: Boolean,
    ): Unit = SdkRuntime.from(context).controller.setSubscribed(subscribed)

    /**
     * Overrides the locale reported to PushPort, without changing the host application's resources.
     *
     * @param languageTag a well-formed BCP-47 tag such as `de-DE`, or `null` to resume automatic detection.
     * @throws IllegalArgumentException if the tag is invalid, undefined, too long, or the SDK is uninitialized.
     */
    @JvmStatic
    public fun setLocale(
        context: Context,
        languageTag: String?,
    ): Unit = SdkRuntime.from(context).controller.setLocale(languageTag)

    /**
     * Requests `POST_NOTIFICATIONS` on Android 13+; does nothing on earlier Android versions.
     *
     * Call from a visible activity following an explanation of why notifications are useful.
     * This does not bypass a previous denial or change the subscription preference.
     * Results go to the activity's normal permission callback; [status] reports effective permission.
     * The host may use its own Activity Result API permission flow instead of this helper.
     *
     * @param requestCode the host activity's permission request code.
     */
    @MainThread
    @JvmStatic
    @JvmOverloads
    public fun requestNotificationPermission(
        activity: Activity,
        requestCode: Int = 8412,
    ): Unit = AndroidNotificationState.request(activity, requestCode)

    /**
     * Queues an opened event from a custom PushPort notification intent.
     *
     * The `pushport_message_id` extra must contain a canonical message UUID. Invalid or missing
     * values are ignored. A recognized extra is consumed to avoid recording it again on resume.
     * Standard SDK notifications call this automatically; normal integrations need no intent handling.
     */
    @JvmStatic
    public fun handleNotificationIntent(
        context: Context,
        intent: Intent?,
    ): Unit = SdkRuntime.from(context).handleNotificationIntent(intent)

    /**
     * Returns a local snapshot without waiting for network work.
     *
     * Safe to call before initialization: [PushPortStatus.configured] is then `false`.
     * Android permission is checked at call time; token and locale reflect the last local capture.
     * A token or successful registration does not guarantee that a particular notification was delivered.
     */
    @JvmStatic
    public fun status(context: Context): PushPortStatus = SdkRuntime.from(context).controller.status()
}
