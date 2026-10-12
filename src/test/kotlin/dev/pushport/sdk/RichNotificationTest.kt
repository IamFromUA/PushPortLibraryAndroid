// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import dev.pushport.sdk.internal.model.PushMessage
import dev.pushport.sdk.internal.model.notificationIconName
import dev.pushport.sdk.internal.model.notificationLargeIcon
import dev.pushport.sdk.internal.model.publicHttpsUrl
import dev.pushport.sdk.internal.network.NotificationImageDownloader
import dev.pushport.sdk.internal.platform.AndroidNotificationIcons
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
    @Test fun `status icons accept resource names and thumbnails also accept public HTTPS`() {
        assertEquals("ic_stat_offer", notificationIconName("ic_stat_offer"))
        listOf("https://example.com/icon.png", "ic_stat.png", "android:drawable/icon", "../icon", "IC_ICON").forEach {
            assertNull(notificationIconName(it))
        }
        assertEquals("https://example.com/icon.png", notificationLargeIcon("https://example.com/icon.png"))
        assertEquals("ic_avatar", notificationLargeIcon("ic_avatar"))
        assertNull(notificationLargeIcon("http://example.com/icon.png"))
        val icons = AndroidNotificationIcons(RuntimeEnvironment.getApplication())
        assertEquals(R.drawable.pushport_notification, icons.small("missing_icon"))
        assertEquals(R.drawable.pushport_notification, icons.small("https://example.com/icon.png"))
        assertNull(icons.large("missing_icon"))
        assertTrue(icons.large("pushport_notification") != null)
    }

    @Test fun `thumbnail and picture enrichment preserve each other and the original click intent in either order`() {
        val context = RuntimeEnvironment.getApplication()
        val permissions =
            object : NotificationStateProvider {
                override fun isEnabled() = true
            }
        val presenter = AndroidNotificationPresenter(context, permissions)
        val manager = context.getSystemService(NotificationManager::class.java)
        for (thumbnailFirst in listOf(true, false)) {
            val id = UUID.randomUUID().toString()
            presenter.show(PushMessage(UUID.randomUUID().toString(), id, "Icons", "Both assets", smallIcon = "pushport_notification"))
            val original =
                manager.activeNotifications
                    .single { it.tag == id }
                    .notification.contentIntent
            val picture = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
            val thumbnail = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            if (thumbnailFirst) {
                presenter.enrich(id, thumbnail, largeIcon = true)
                presenter.enrich(id, picture)
            } else {
                presenter.enrich(id, picture)
                presenter.enrich(id, thumbnail, largeIcon = true)
            }
            val posted = manager.activeNotifications.single { it.tag == id }.notification
            assertEquals(original, posted.contentIntent)
            assertEquals(R.drawable.pushport_notification, posted.smallIcon.resId)
            @Suppress("DEPRECATION")
            val shownPicture = posted.extras.getParcelable<Bitmap>("android.picture")!!
            val shownThumbnail = (posted.getLargeIcon().loadDrawable(context) as android.graphics.drawable.BitmapDrawable).bitmap
            // Android may resize the picture to its notification dimensions.
            assertTrue(shownPicture.width > 0)
            assertEquals(2.0, shownPicture.width.toDouble() / shownPicture.height, 0.02)
            assertTrue(shownThumbnail.width <= 256)
            assertTrue(shownThumbnail.height <= 256)
            manager.cancel(id, 0)
            presenter.enrich(id, thumbnail, largeIcon = true)
            assertFalse(presenter.isVisible(id))
        }
    }

    @Test fun `resource thumbnail is visible immediately and missing status icons safely fall back`() {
        val context = RuntimeEnvironment.getApplication()
        val presenter =
            AndroidNotificationPresenter(
                context,
                object : NotificationStateProvider {
                    override fun isEnabled() = true
                },
            )
        val id = UUID.randomUUID().toString()
        presenter.show(
            PushMessage(
                UUID.randomUUID().toString(),
                id,
                "Local thumbnail",
                "Body",
                largeIcon = "pushport_notification",
                smallIcon = "missing",
            ),
        )
        val notification =
            context
                .getSystemService(
                    NotificationManager::class.java,
                ).activeNotifications
                .single { it.tag == id }
                .notification
        assertEquals(R.drawable.pushport_notification, notification.smallIcon.resId)
        assertTrue(notification.getLargeIcon() != null)
    }

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
        assertTrue(decoded.width <= 2000)
    }

    @Test fun `image decoder retains a 2000 pixel banner and renders GIF as a static bitmap`() {
        val loader = NotificationImageDownloader()
        val banner = BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB)
        val png = ByteArrayOutputStream().also { ImageIO.write(banner, "png", it) }.toByteArray()
        val decoded = loader.decode(png)!!
        assertEquals(2000, decoded.width)
        assertEquals(1000, decoded.height)
        val source = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
        val gif = ByteArrayOutputStream().also { ImageIO.write(source, "gif", it) }.toByteArray()
        val firstFrame = loader.decode(gif)!!
        assertEquals(256, firstFrame.width)
        assertEquals(256, firstFrame.height)
        assertNull(loader.decode("GIF89a-broken-data".toByteArray()))
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
