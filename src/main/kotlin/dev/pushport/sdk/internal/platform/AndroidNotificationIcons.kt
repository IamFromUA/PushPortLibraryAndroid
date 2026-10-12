// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.platform

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import dev.pushport.sdk.R
import dev.pushport.sdk.internal.model.notificationIconName

/** Per-message icons are app resources; optional defaults mirror the OneSignal resource convention. */
internal class AndroidNotificationIcons(
    private val context: Context,
) {
    @SuppressLint("DiscouragedApi")
    private fun resource(value: String?): Int {
        val name = notificationIconName(value) ?: return 0
        return context.resources
            .getIdentifier(name, "drawable", context.packageName)
            .takeIf { it != 0 } ?: context.resources.getIdentifier(name, "mipmap", context.packageName)
    }

    fun small(value: String?): Int =
        resource(value).takeIf { it != 0 }
            ?: resource("ic_stat_pushport_default").takeIf { it != 0 }
            ?: R.drawable.pushport_notification

    fun large(value: String?): Bitmap? = bitmap(resource(value)) ?: bitmap(resource("ic_pushport_large_icon_default"))

    private fun bitmap(id: Int): Bitmap? {
        if (id == 0) return null
        return runCatching {
            val drawable = context.getDrawable(id) ?: return@runCatching null
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = minOf(1.0, 256.0 / maxOf(width, height))
            val bitmap =
                Bitmap.createBitmap(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            bitmap
        }.getOrNull()
    }
}
