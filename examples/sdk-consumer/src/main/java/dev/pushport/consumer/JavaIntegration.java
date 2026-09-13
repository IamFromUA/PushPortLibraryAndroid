package dev.pushport.consumer;

import android.content.Context;
import dev.pushport.sdk.PushPort;
import dev.pushport.sdk.PushPortConfig;
import dev.pushport.sdk.PushPortStatus;

/** Kept reachable from the fixture application so the R8 build validates Java references too. */
final class JavaIntegration {
    static void verify(Context context) {
        PushPortConfig settings = new PushPortConfig(
                "11111111-1111-4111-8111-111111111111", "https://example.test");
        PushPortStatus status = PushPort.status(context);
        if (settings.getAllowInsecureLocalhost() || status.getConfigured()) {
            throw new IllegalStateException("Unexpected fixture state");
        }
    }

    private JavaIntegration() {}
}
