# WorkManager instantiates workers by their persisted class names.
-keep class dev.pushport.sdk.SyncWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class dev.pushport.sdk.NotificationImageWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
