// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.model

internal fun notificationIconName(value: String?): String? = value?.trim()?.takeIf { it.matches(Regex("[a-z][a-z0-9_]{0,79}")) }

internal fun notificationLargeIcon(value: String?): String? = notificationIconName(value) ?: publicHttpsUrl(value)
