# Independently versioned Unity and KMP adapters discover this method at runtime.
-keepclassmembers class dev.pushport.sdk.PushPort { public static java.lang.String appProfileJson(android.content.Context); }

# WorkManager instantiates workers by their persisted class names.
-keep class dev.pushport.sdk.SyncWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class dev.pushport.sdk.NotificationImageWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
