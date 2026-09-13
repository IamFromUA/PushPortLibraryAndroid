// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.core

import dev.pushport.sdk.internal.ports.Clock
import dev.pushport.sdk.internal.ports.HttpFailure
import dev.pushport.sdk.internal.ports.InstallationApiFactory
import dev.pushport.sdk.internal.ports.InstallationRepository
import dev.pushport.sdk.internal.ports.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

internal enum class SyncOutcome { SUCCESS, RETRY, FAILURE }

/** Coordinates synchronization. The caller supplies an IO dispatcher for blocking API calls. */
internal class SyncCoordinator(
    private val repository: InstallationRepository,
    private val collector: SnapshotCollector,
    private val tokens: TokenRefresher,
    private val apiFactory: InstallationApiFactory,
    private val scheduler: SyncScheduler,
    private val clock: Clock,
    private val debuggable: Boolean,
) {
    private val mutex = Mutex()

    suspend fun synchronize(attempt: Int): SyncOutcome =
        mutex.withLock {
            val config = repository.read().config ?: return@withLock SyncOutcome.SUCCESS
            try {
                val api = apiFactory.create(config.validate(debuggable))
                val preliminary = collector.capture() ?: return@withLock SyncOutcome.SUCCESS
                val tokenOutcome = tokens.refresh(api.configuration(preliminary.packageName))
                val snapshot = collector.capture() ?: return@withLock SyncOutcome.SUCCESS
                val state = repository.read()
                val identity = requireNotNull(state.identity) { "Missing installation identity" }
                val stale = state.lastSyncedAt == null || clock.nowMillis() - state.lastSyncedAt >= HEARTBEAT_MILLIS
                if (state.lastSyncedRevision != snapshot.revision || stale) {
                    api.register(identity, snapshot)
                    repository.update {
                        it.copy(lastSyncedRevision = snapshot.revision, lastSyncedAt = clock.nowMillis(), syncError = null)
                    }
                }
                for (messageId in repository.read().pendingOpenedMessages) {
                    api.opened(identity, messageId)
                    repository.update { it.copy(pendingOpenedMessages = it.pendingOpenedMessages - messageId) }
                }
                repository.update { it.copy(syncError = null) }
                if (repository.read().revision != snapshot.revision) scheduler.enqueue()
                when (tokenOutcome) {
                    TokenRefreshOutcome.RETRY -> retryOrFail(attempt)
                    TokenRefreshOutcome.INVALID_CONFIGURATION -> SyncOutcome.FAILURE
                    TokenRefreshOutcome.READY, TokenRefreshOutcome.NOT_CONFIGURED -> SyncOutcome.SUCCESS
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message =
                    when (error) {
                        is HttpFailure -> "HTTP ${error.status}"
                        is IOException -> "Нет соединения с сервером"
                        else -> "Ошибка конфигурации или ответа сервера (${error.javaClass.simpleName})"
                    }
                repository.update { it.copy(syncError = message) }
                val retryable = error is IOException || (error is HttpFailure && error.retryable)
                if (retryable) retryOrFail(attempt) else SyncOutcome.FAILURE
            }
        }

    private fun retryOrFail(attempt: Int): SyncOutcome = if (attempt < MAX_RETRIES) SyncOutcome.RETRY else SyncOutcome.FAILURE

    private companion object {
        val HEARTBEAT_MILLIS: Long = TimeUnit.HOURS.toMillis(12)
        const val MAX_RETRIES = 8
    }
}
