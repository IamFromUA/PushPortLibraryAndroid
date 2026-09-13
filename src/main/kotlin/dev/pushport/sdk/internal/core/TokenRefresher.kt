// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.core

import dev.pushport.sdk.internal.model.FirebaseConfiguration
import dev.pushport.sdk.internal.ports.InstallationRepository
import dev.pushport.sdk.internal.ports.PushTokenProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** A token failure must not prevent metadata registration or lose its retry signal. */
internal enum class TokenRefreshOutcome { READY, NOT_CONFIGURED, INVALID_CONFIGURATION, RETRY }

internal class TokenRefresher(
    private val repository: InstallationRepository,
    private val provider: PushTokenProvider,
    private val timeoutMillis: Long = 20_000,
) {
    fun restore() {
        val configuration = repository.read().firebase ?: return
        try {
            provider.restore(configuration)
        } catch (_: Exception) {
            repository.update { it.copy(pushError = INVALID_CONFIGURATION, fcmToken = null) }
        }
    }

    suspend fun refresh(configuration: FirebaseConfiguration?): TokenRefreshOutcome {
        if (configuration == null) {
            repository.update { it.copy(firebase = null, fcmToken = null, pushError = NOT_CONFIGURED) }
            return TokenRefreshOutcome.NOT_CONFIGURED
        }
        repository.update { state ->
            state.copy(firebase = configuration, fcmToken = state.fcmToken.takeIf { state.firebase == configuration })
        }
        try {
            val token = withTimeoutOrNull(timeoutMillis) { provider.token(configuration) }
            if (token == null) {
                repository.update { it.copy(pushError = TEMPORARILY_UNAVAILABLE) }
                return TokenRefreshOutcome.RETRY
            }
            require(token.isNotBlank()) { "Empty Firebase token" }
            repository.update { it.copy(fcmToken = token, pushError = null) }
            return TokenRefreshOutcome.READY
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            repository.update { it.copy(pushError = INVALID_CONFIGURATION, fcmToken = null) }
            return TokenRefreshOutcome.INVALID_CONFIGURATION
        } catch (_: Exception) {
            repository.update { it.copy(pushError = TOKEN_FAILURE) }
            return TokenRefreshOutcome.RETRY
        }
    }

    private companion object {
        const val NOT_CONFIGURED = "Настройте Firebase для приложения на сервере"
        const val INVALID_CONFIGURATION = "Проверьте совместимость настройки Firebase"
        const val TEMPORARILY_UNAVAILABLE = "FCM пока недоступен; повторим позже"
        const val TOKEN_FAILURE = "Не удалось получить FCM-токен; проверьте Firebase и Google Play services"
    }
}
