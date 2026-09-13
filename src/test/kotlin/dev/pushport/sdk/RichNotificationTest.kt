// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import dev.pushport.sdk.internal.model.PushMessage
import dev.pushport.sdk.internal.model.publicHttpsUrl
import dev.pushport.sdk.internal.network.NotificationImageDownloader
import dev.pushport.sdk.internal.platform.AndroidNotificationPresenter
import dev.pushport.sdk.internal.ports.NotificationStateProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.UUID
import javax.imageio.ImageIO

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RichNotificationTest {
    @Test fun `external links accept public HTTPS only and image DNS blocks private destinations`() {
        assertEquals("https://pushport.dev/article?a=1", publicHttpsUrl("https://pushport.dev/article?a=1"))
        listOf(
            "http://pushport.dev",
            "intent://pushport.dev",
            "https://127.0.0.1",
            "https://user:pass@pushport.dev",
            "https://router.local",
            "https://pushport.dev:8080",
        ).forEach {
            assertNull(publicHttpsUrl(it))
        }
        listOf("127.0.0.1", "192.168.1.1", "10.0.0.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "::1", "fc00::1", "fe80::1").forEach {
            assertFalse(NotificationImageDownloader.isPublic(InetAddress.getByName(it)))
        }
        assertTrue(NotificationImageDownloader.isPublic(InetAddress.getByName("1.1.1.1")))
    }

    @Test fun `image decoder rejects malformed data and bounds bitmap size`() {
        val loader = NotificationImageDownloader()
        assertNull(loader.decode(ByteArray(1_048_577)))
        assertNull(loader.decode("not an image".toByteArray()))
        val bitmap = BufferedImage(2048, 512, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(bitmap, "png", it) }.toByteArray()
        val decoded = loader.decode(bytes)!!
        assertTrue(decoded.width <= 1024)
    }

    @Test fun `enrichment preserves click intent and does not recreate a dismissed notification`() {
        val context = RuntimeEnvironment.getApplication()
        val permissions =
            object : NotificationStateProvider {
                override fun isEnabled() = true
            }
        val presenter = AndroidNotificationPresenter(context, permissions)
        val id = UUID.randomUUID().toString()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        presenter.show(PushMessage(UUID.randomUUID().toString(), id, "Title", "Body", clickUrl = "https://pushport.dev"))
        val original =
            manager.activeNotifications
                .single { it.tag == id }
                .notification.contentIntent
        val image = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        presenter.enrich(id, image)
        assertEquals(
            original,
            manager.activeNotifications
                .single { it.tag == id }
                .notification.contentIntent,
        )
        manager.cancel(id, 0)
        presenter.enrich(id, image)
        assertFalse(presenter.isVisible(id))
    }
}
