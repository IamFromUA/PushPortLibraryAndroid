// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.platform

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import dev.pushport.sdk.BuildConfig
import dev.pushport.sdk.internal.model.DeviceSnapshot
import dev.pushport.sdk.internal.ports.DeviceInfoProvider
import dev.pushport.sdk.internal.ports.NotificationStateProvider
import java.util.Locale
import java.util.TimeZone

internal class AndroidDeviceInfoProvider(
    private val context: Context,
    private val notifications: NotificationStateProvider,
) : DeviceInfoProvider {
    override fun snapshot(
        localeOverride: String?,
        observedLocales: List<String>?,
    ): DeviceSnapshot {
        val systemLocales =
            if (Build.VERSION.SDK_INT >= 33) {
                context
                    .getSystemService(LocaleManager::class.java)
                    .systemLocales
                    .toLanguageTags()
                    .split(',')
            } else {
                resourceLocales(Resources.getSystem())
            }
        val appLocales =
            if (Build.VERSION.SDK_INT >= 33) {
                context
                    .getSystemService(LocaleManager::class.java)
                    .applicationLocales
                    .toLanguageTags()
                    .split(',')
                    .filter(String::isNotBlank)
            } else {
                emptyList()
            }
        val effective =
            when {
                localeOverride != null -> listOf(localeOverride)
                appLocales.isNotEmpty() -> appLocales
                Build.VERSION.SDK_INT >= 33 -> systemLocales
                observedLocales != null -> observedLocales
                else -> resourceLocales(context.resources)
            }.filter(String::isNotBlank).ifEmpty { listOf("und") }.take(MAX_LOCALES)
        val locale = Locale.forLanguageTag(effective.first())

        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)

        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return DeviceSnapshot(
            packageName = context.packageName,
            language = locale.language.ifBlank { "und" },
            locale = locale.toLanguageTag(),
            appLocales = effective,
            systemLocales = systemLocales.filter(String::isNotBlank).ifEmpty { listOf("und") }.take(MAX_LOCALES),
            timezone = TimeZone.getDefault().id,
            notificationsEnabled = notifications.isEnabled(),
            sdkVersion = BuildConfig.SDK_VERSION,
            appVersion = info.versionName.orEmpty().take(100),
            appVersionCode = versionCode,
            osVersion = Build.VERSION.RELEASE.take(50),
            androidApi = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.take(100),
            model = Build.MODEL.take(100),
            androidId = androidId(),
        )
    }

    // App-signing-key/user/device-scoped metadata; never used as the installation identity.
    @SuppressLint("HardwareIds")
    private fun androidId(): String? =
        try {
            Settings.Secure
                .getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.matches(ANDROID_ID_FORMAT) && it.any { digit -> digit != '0' } }
        } catch (_: SecurityException) {
            null
        }

    companion object {
        private const val MAX_LOCALES = 30
        private val ANDROID_ID_FORMAT = Regex("[0-9a-f]{1,16}")

        @Suppress("DEPRECATION")
        fun resourceLocales(resources: Resources): List<String> =
            if (Build.VERSION.SDK_INT >= 24) {
                resources.configuration.locales
                    .toLanguageTags()
                    .split(',')
            } else {
                listOf(resources.configuration.locale.toLanguageTag())
            }
    }
}
