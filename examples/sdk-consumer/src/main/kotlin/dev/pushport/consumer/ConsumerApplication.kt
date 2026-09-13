package dev.pushport.consumer

import android.app.Application
import dev.pushport.sdk.PushPort
import dev.pushport.sdk.PushPortConfig

/** Compile-only fixture: no registration or private credentials are needed to verify the AAR. */
class ConsumerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val status = PushPort.status(this)
        check(!status.configured)
        val settings = PushPortConfig("11111111-1111-4111-8111-111111111111", "https://example.test")
        check(settings.appId.isNotEmpty())
        JavaIntegration.verify(this)
    }
}
